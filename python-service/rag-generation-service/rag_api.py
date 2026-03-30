import os
import json
import uuid
import asyncio
import requests
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException, BackgroundTasks
from pydantic import BaseModel
from typing import List, Optional, Dict, Any

from industrial_fta_common.fault_tree_schema import FaultTreeSchema
from industrial_fta_common.prompts import load_fault_tree_prompt
from industrial_fta_common.fusion.fusion_engine import FusionEngine
from rag_service.llm_client import LLMClient
from rag_service.vector_retriever import VectorRetriever
from rag_service.fault_tree_generator import FaultTreeGenerator
from rag_service.hybrid_generator import HybridFaultTreeGenerator
from rag_service.knowledge_graph_client import KnowledgeGraphClient

load_dotenv()

app = FastAPI(title="RAG Generation API", version="1.0.0")

llm_client = LLMClient()
vector_retriever = VectorRetriever()
fault_tree_generator = FaultTreeGenerator()
knowledge_graph_client = KnowledgeGraphClient()
hybrid_generator = HybridFaultTreeGenerator(
    knowledge_graph_client=knowledge_graph_client,
    vector_retriever=vector_retriever,
    llm_client=llm_client
)
fusion_engine = FusionEngine(similarity_threshold=0.7, auto_resolve_low_severity=True)

class GenerateRequest(BaseModel):
    topEvent: str
    docIds: Optional[List[str]] = []
    template: Optional[Dict[str, Any]] = {}
    equipmentType: Optional[str] = 'general'
    userPreferences: Optional[str] = None

class GenerateResponse(BaseModel):
    taskId: str
    status: str
    faultTree: Optional[Dict[str, Any]] = None
    fusionStatistics: Optional[Dict[str, Any]] = None
    error: Optional[str] = None

class ParagraphEvidence(BaseModel):
    paragraphId: str
    content: str
    sourceType: str
    credibilityWeight: Optional[float] = None
    documentName: Optional[str] = None
    pageNumber: Optional[int] = None
    sectionTitle: Optional[str] = None
    similarityScore: float

class EvidenceRequest(BaseModel):
    eventId: str
    sourceEvidence: str

class EvidenceResponse(BaseModel):
    eventId: str
    sourceEvidence: str
    evidences: List[ParagraphEvidence]

tasks_store: Dict[str, Dict[str, Any]] = {}

def _get_document_content_from_service(doc_ids: List[str]) -> List[Dict[str, Any]]:
    """从document-ingest-service获取文档内容，返回带元数据的段落列表"""
    try:
        document_service_url = os.getenv('DOCUMENT_SERVICE_URL', 'http://document-ingest-service:8080')
        vector_service_url = os.getenv('VECTOR_SERVICE_URL', 'http://vector-store-service:8080')

        if not doc_ids:
            return []

        paragraphs_with_metadata = []

        for doc_id in doc_ids:
            doc_response = requests.get(
                f"{document_service_url}/api/v1/documents/{doc_id}/content",
                timeout=10
            )
            if doc_response.status_code != 200:
                continue

            doc_data = doc_response.json()
            content = doc_data.get('content', [])

            evidence_response = requests.get(
                f"{vector_service_url}/api/v1/vector/documents/{doc_id}/paragraphs",
                timeout=10
            )

            paragraph_evidence = {}
            if evidence_response.status_code == 200:
                for para in evidence_response.json():
                    paragraph_evidence[para['paragraphId']] = para

            if isinstance(content, list):
                for idx, para_content in enumerate(content):
                    paragraph_id = f"{doc_id}_p{idx}"
                    metadata = paragraph_evidence.get(paragraph_id, {})

                    paragraphs_with_metadata.append({
                        'paragraph_id': paragraph_id,
                        'content': para_content if isinstance(para_content, str) else str(para_content),
                        'metadata': {
                            'source_type': metadata.get('sourceType', 'unknown'),
                            'credibility_weight': metadata.get('credibilityWeight', 0.5),
                            'document_name': doc_data.get('fileName', doc_id),
                            'doc_id': doc_id,
                            'page_number': metadata.get('pageNumber', 1),
                            'section_title': metadata.get('sectionTitle', '')
                        }
                    })
            elif isinstance(content, str):
                for idx, line in enumerate(content.split('\n')):
                    if line.strip():
                        paragraph_id = f"{doc_id}_p{idx}"
                        metadata = paragraph_evidence.get(paragraph_id, {})

                        paragraphs_with_metadata.append({
                            'paragraph_id': paragraph_id,
                            'content': line.strip(),
                            'metadata': {
                                'source_type': metadata.get('sourceType', 'unknown'),
                                'credibility_weight': metadata.get('credibilityWeight', 0.5),
                                'document_name': doc_data.get('fileName', doc_id),
                                'doc_id': doc_id,
                                'page_number': metadata.get('pageNumber', 1),
                                'section_title': metadata.get('sectionTitle', '')
                            }
                        })

        return paragraphs_with_metadata

    except Exception as e:
        print(f"Error fetching document content: {e}")
        return []

def _fuse_documents(paragraphs: List[Dict[str, Any]]) -> Tuple[List[Dict[str, Any]], Dict[str, Any]]:
    """
    使用融合引擎融合多个文档的段落

    参数:
        paragraphs: 带元数据的段落列表

    返回:
        (融合后的段落列表, 融合统计信息)
    """
    try:
        fusion_result = fusion_engine.fuse(paragraphs)

        fused_paragraphs = []
        for fused in fusion_result.fused_paragraphs:
            fused_paragraphs.append({
                'paragraph_id': fused.get('paragraph_id', ''),
                'content': fused.get('content', ''),
                'metadata': fused.get('metadata', {})
            })

        statistics = {
            'total_clusters': len(fusion_result.clusters),
            'total_conflicts': len(fusion_result.conflicts),
            'resolved_conflicts': sum(1 for c in fusion_result.conflicts if c.get('resolved', False)),
            'fused_paragraphs_count': len(fused_paragraphs)
        }

        return fused_paragraphs, statistics

    except Exception as e:
        print(f"Error in document fusion: {e}")
        return paragraphs, {'error': str(e)}

def _generate_fault_tree_sync(task_id: str, top_event: str, doc_ids: List[str], template: Dict[str, Any], equipment_type: str, user_preferences: Optional[str]):
    """同步生成故障树 - 使用知识驱动+数据驱动混合模式"""
    try:
        tasks_store[task_id] = {'status': 'processing'}

        # 步骤1: 获取文档内容并融合（数据驱动准备）
        paragraphs_with_metadata = _get_document_content_from_service(doc_ids)
        fused_paragraphs, fusion_stats = _fuse_documents(paragraphs_with_metadata)

        # 步骤1.5: 如果没有提供模板，从知识图谱获取默认模板（知识驱动）
        knowledge_template = template
        if not knowledge_template or len(knowledge_template) == 0:
            print(f"Querying knowledge graph for template: topEvent={top_event}, equipmentType={equipment_type}")
            knowledge_template = knowledge_graph_client.query_template(
                top_event=top_event,
                equipment_type=equipment_type
            )
            print(f"Retrieved knowledge template: {knowledge_template.get('templateId', 'unknown')}")

        # 步骤2: 使用混合生成器生成故障树
        # 知识驱动: 使用template作为知识图谱模板
        # 数据驱动: 使用doc_ids进行向量检索
        fault_tree, hybrid_stats = hybrid_generator.generate(
            top_event=top_event,
            doc_ids=doc_ids,
            knowledge_template=knowledge_template,
            user_preferences=user_preferences
        )

        # 合并统计信息
        combined_stats = {
            **fusion_stats,
            **hybrid_stats
        }

        # 转换为字典并添加元数据
        fault_tree_dict = fault_tree.dict() if hasattr(fault_tree, 'dict') else fault_tree

        for node in _traverse_nodes(fault_tree_dict):
            node['sourceDetail'] = {
                'sourceId': node.get('eventId'),
                'sourceType': 'HYBRID_GENERATED',
                'documentName': 'RAG + Knowledge Graph',
                'pageNumber': None,
                'paragraphId': None,
                'fusionStatistics': combined_stats
            }
            if node.get('confidence') is None:
                node['confidence'] = 0.85
            if node.get('aiGenerated') is None:
                node['aiGenerated'] = True
            if node.get('verificationStatus') is None:
                node['verificationStatus'] = 'PENDING'
            # 添加生成模式标记
            node['generationMode'] = 'hybrid'

        tasks_store[task_id] = {
            'status': 'completed',
            'faultTree': fault_tree_dict,
            'fusionStatistics': combined_stats
        }

    except Exception as e:
        print(f"Error generating fault tree: {e}")
        tasks_store[task_id] = {
            'status': 'failed',
            'error': str(e)
        }

def _traverse_nodes(node: Dict[str, Any]):
    """遍历所有节点"""
    yield node
    if node.get('children'):
        for child in node['children']:
            yield from _traverse_nodes(child)

@app.get("/health")
def health_check():
    return {"status": "healthy", "service": "rag-generation-api"}

@app.post("/api/v1/rag/generate", response_model=GenerateResponse)
def generate_fault_tree(request: GenerateRequest, background_tasks: BackgroundTasks):
    """触发故障树生成"""
    task_id = f"task_{uuid.uuid4().hex[:16]}"

    background_tasks.add_task(
        _generate_fault_tree_sync,
        task_id,
        request.topEvent,
        request.docIds,
        request.template,
        request.equipmentType,
        request.userPreferences
    )

    return GenerateResponse(
        taskId=task_id,
        status="processing",
        faultTree=None,
        fusionStatistics=None,
        error=None
    )

@app.get("/api/v1/rag/tasks/{task_id}", response_model=GenerateResponse)
def get_task_status(task_id: str):
    """获取生成任务状态"""
    if task_id not in tasks_store:
        raise HTTPException(status_code=404, detail="Task not found")

    task_data = tasks_store[task_id]
    return GenerateResponse(
        taskId=task_id,
        status=task_data.get('status', 'unknown'),
        faultTree=task_data.get('faultTree'),
        fusionStatistics=task_data.get('fusionStatistics'),
        error=task_data.get('error')
    )

@app.get("/api/v1/rag/evidence/{paragraph_id}", response_model=ParagraphEvidence)
def get_paragraph_evidence(paragraph_id: str):
    """获取段落溯源证据"""
    try:
        vector_service_url = os.getenv('VECTOR_SERVICE_URL', 'http://vector-store-service:8080')
        response = requests.get(
            f"{vector_service_url}/api/v1/vector/paragraphs/{paragraph_id}",
            timeout=10
        )
        if response.status_code == 200:
            data = response.json()
            return ParagraphEvidence(
                paragraphId=data.get('paragraphId', paragraph_id),
                content=data.get('content', ''),
                sourceType=data.get('sourceType', 'UNKNOWN'),
                credibilityWeight=data.get('credibilityWeight'),
                documentName=data.get('documentName'),
                pageNumber=data.get('pageNumber'),
                sectionTitle=data.get('sectionTitle'),
                similarityScore=data.get('similarityScore', 0.0)
            )
        raise HTTPException(status_code=404, detail="Paragraph not found")
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/v1/rag/evidence", response_model=EvidenceResponse)
def get_event_evidence(request: EvidenceRequest):
    """获取事件的溯源证据列表"""
    try:
        vector_service_url = os.getenv('VECTOR_SERVICE_URL', 'http://vector-store-service:8080')

        evidence_list = []

        query = request.sourceEvidence or request.eventId
        response = requests.post(
            f"{vector_service_url}/api/v1/vector/search",
            json={"query": query, "topK": 5},
            timeout=10
        )

        if response.status_code == 200:
            results = response.json()
            for item in results:
                evidence_list.append(ParagraphEvidence(
                    paragraphId=item.get('paragraphId', ''),
                    content=item.get('content', ''),
                    sourceType=item.get('sourceType', 'RETRIEVED'),
                    credibilityWeight=item.get('credibilityWeight'),
                    documentName=item.get('documentName'),
                    pageNumber=item.get('pageNumber'),
                    sectionTitle=item.get('sectionTitle'),
                    similarityScore=item.get('similarityScore', 0.0)
                ))

        return EvidenceResponse(
            eventId=request.eventId,
            sourceEvidence=request.sourceEvidence,
            evidences=evidence_list
        )

    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    port = int(os.getenv('RAG_API_PORT', '8000'))
    uvicorn.run(app, host="0.0.0.0", port=port)