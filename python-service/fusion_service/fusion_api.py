# Fusion Service API
# 多文档融合服务 API

import os
import json
from typing import List, Dict, Any, Optional
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from dotenv import load_dotenv
import numpy as np

from industrial_fta_common.fusion import (
    FusionEngine,
    DocumentMetadata,
    SourceType,
    DocumentClassifier,
    classify_document
)
from industrial_fta_common.fusion.conflict_detector import (
    Conflict,
    ConflictSeverity
)
from industrial_fta_common.fusion.document_metadata import ParagraphWithMetadata

load_dotenv()

app = FastAPI(title="Fusion Service API", version="1.0.0")

fusion_engine = FusionEngine(
    similarity_threshold=float(os.getenv('SIMILARITY_THRESHOLD', '0.7')),
    auto_resolve_low_severity=os.getenv('AUTO_RESOLVE_LOW_SEVERITY', 'true').lower() == 'true'
)

class ParagraphInput(BaseModel):
    paragraphId: str
    content: str
    docId: str
    sourceType: str
    documentName: str
    pageNumber: Optional[int] = None
    sectionTitle: Optional[str] = None
    credibilityWeight: Optional[float] = None

class FusionRequest(BaseModel):
    paragraphs: List[ParagraphInput]
    topEvent: Optional[str] = None
    similarityThreshold: Optional[float] = 0.7

class FusionResponse(BaseModel):
    fusedParagraphs: List[Dict[str, Any]]
    clusters: List[Dict[str, Any]]
    conflicts: List[Dict[str, Any]]
    statistics: Dict[str, Any]

class ConflictResolveRequest(BaseModel):
    conflictId: str
    resolution: str
    expertDecision: Optional[str] = None

@app.get("/health")
def health_check():
    return {"status": "healthy", "service": "fusion-api"}

@app.post("/api/v1/fusion/fuse", response_model=FusionResponse)
def fuse_documents(request: FusionRequest):
    """
    融合多个文档的段落

    参数:
        request: 融合请求，包含段落列表

    返回:
        融合结果
    """
    if not request.paragraphs:
        raise HTTPException(status_code=400, detail="段落列表不能为空")

    source_type_map = {
        'equipment_manual': SourceType.EQUIPMENT_MANUAL,
        'maintenance_record': SourceType.MAINTENANCE_RECORD,
        'industry_standard': SourceType.INDUSTRY_STANDARD,
        'theory_paper': SourceType.THEORY_PAPER,
        'user_feedback': SourceType.USER_FEEDBACK,
        'unknown': SourceType.UNKNOWN
    }

    classifier = DocumentClassifier()

    doc_contents = {}
    for para_input in request.paragraphs:
        if para_input.docId not in doc_contents:
            doc_contents[para_input.docId] = {
                'document_name': para_input.documentName,
                'contents': [],
                'source_type': para_input.sourceType,
                'credibility_weight': para_input.credibilityWeight
            }
        doc_contents[para_input.docId]['contents'].append(para_input.content)

    auto_classified_docs = {}
    for doc_id, doc_info in doc_contents.items():
        combined_content = ' '.join(doc_info['contents'])[:2000]
        auto_result = classifier.classify(
            doc_info['document_name'],
            combined_content,
            use_prematching=True
        )
        auto_classified_docs[doc_id] = auto_result

    paragraphs = []
    for para_input in request.paragraphs:
        auto_result = auto_classified_docs.get(para_input.docId)

        if auto_result and auto_result.confidence >= 0.6:
            source_type = auto_result.source_type
            credibility_weight = auto_result.get_credibility_weight()
        else:
            source_type = source_type_map.get(
                para_input.sourceType.lower(),
                SourceType.UNKNOWN
            )
            credibility_weight = para_input.credibilityWeight or 1.0

        metadata = DocumentMetadata(
            doc_id=para_input.docId,
            source_type=source_type,
            document_name=para_input.documentName,
            page_number=para_input.pageNumber,
            section_title=para_input.sectionTitle,
            credibility_weight=credibility_weight
        )

        paragraph = {
            'paragraph_id': para_input.paragraphId,
            'content': para_input.content,
            'metadata': metadata.to_dict()
        }
        paragraphs.append(paragraph)

    embeddings = None
    try:
        from rag_generation_service.rag_service.vector_retriever import VectorRetriever
        retriever = VectorRetriever()
        embeddings = []
        for para in paragraphs:
            embedding = retriever.embed_text(para['content'])
            embeddings.append(embedding)
    except Exception as e:
        print(f"Warning: Could not generate embeddings, using content similarity only: {e}")
        embeddings = [np.random.randn(768) for _ in range(len(paragraphs))]

    if request.similarityThreshold:
        fusion_engine.similarity_threshold = request.similarityThreshold
        fusion_engine.clusterer.similarity_threshold = request.similarityThreshold

    result = fusion_engine.fuse(paragraphs, embeddings)

    return FusionResponse(
        fusedParagraphs=result.fused_paragraphs,
        clusters=result.clusters,
        conflicts=result.conflicts,
        statistics=result.statistics
    )

@app.get("/api/v1/fusion/conflicts")
def get_conflicts(
    resolved: Optional[bool] = None,
    severity: Optional[str] = None
):
    """
    获取冲突列表

    参数:
        resolved: 过滤已解决/未解决的冲突
        severity: 严重程度过滤 (low, medium, high)

    返回:
        冲突列表
    """
    return fusion_engine.get_conflict_list(resolved=resolved, severity=severity)

@app.post("/api/v1/fusion/conflicts/resolve")
def resolve_conflict(request: ConflictResolveRequest):
    """
    解决冲突

    参数:
        request: 冲突解决请求

    返回:
        解决结果
    """
    success = fusion_engine.conflict_detector.resolve_conflict(
        conflict_id=request.conflictId,
        resolution=request.resolution,
        expert_decision=request.expertDecision
    )

    if not success:
        raise HTTPException(status_code=404, detail="找不到指定的冲突")

    return {
        "message": "冲突已解决",
        "conflictId": request.conflictId,
        "resolution": request.resolution
    }

@app.get("/api/v1/fusion/statistics")
def get_fusion_statistics():
    """
    获取融合统计信息

    返回:
        统计信息
    """
    conflicts = fusion_engine.conflict_detector.conflicts

    return {
        'totalConflicts': len(conflicts),
        'resolvedConflicts': len([c for c in conflicts if c.resolved]),
        'unresolvedConflicts': len([c for c in conflicts if not c.resolved]),
        'conflictsBySeverity': {
            'high': len([c for c in conflicts if c.severity == ConflictSeverity.HIGH]),
            'medium': len([c for c in conflicts if c.severity == ConflictSeverity.MEDIUM]),
            'low': len([c for c in conflicts if c.severity == ConflictSeverity.LOW])
        }
    }

@app.post("/api/v1/fusion/validate-source-type")
def validate_source_type(source_type: str):
    """
    验证来源类型是否有效

    参数:
        source_type: 来源类型字符串

    返回:
        验证结果
    """
    valid_types = [st.value for st in SourceType]
    is_valid = source_type.lower() in valid_types

    return {
        "sourceType": source_type,
        "isValid": is_valid,
        "validTypes": valid_types
    }

@app.post("/api/v1/fusion/classify-document")
def classify_document_endpoint(document_name: str, content: str = ""):
    """
    自动分类文档来源类型（使用LLM智能判断）

    参数:
        document_name: 文档名称/标题
        content: 文档内容（可选，用于深度分析）

    返回:
        分类结果，包含类型、置信度、判断理由和可信度权重
    """
    if not content or len(content.strip()) < 50:
        raise HTTPException(
            status_code=400,
            detail="内容长度不足，无法进行分类判断（至少需要50个字符）"
        )

    result = classify_document(document_name, content)

    return {
        "documentName": document_name,
        "sourceType": result['source_type'],
        "confidence": result['confidence'],
        "reasoning": result['reasoning'],
        "method": result['method'],
        "credibilityWeight": result['credibility_weight']
    }

@app.post("/api/v1/fusion/classify-document-batch")
def classify_document_batch(documents: List[Dict[str, str]]):
    """
    批量自动分类文档来源类型

    参数:
        documents: 文档列表，每个文档包含 document_name 和 content

    返回:
        批量分类结果
    """
    results = []
    classifier = DocumentClassifier()

    for doc in documents:
        doc_name = doc.get('document_name', '')
        doc_content = doc.get('content', '')

        if not doc_content or len(doc_content.strip()) < 50:
            results.append({
                'documentName': doc_name,
                'sourceType': 'unknown',
                'confidence': 0.3,
                'reasoning': '内容长度不足',
                'method': 'none',
                'credibilityWeight': 0.5
            })
            continue

        result = classifier.classify(doc_name, doc_content)
        results.append(result.to_dict())

    return {
        "totalDocuments": len(documents),
        "classifiedDocuments": len([r for r in results if r['source_type'] != 'unknown']),
        "results": results
    }
