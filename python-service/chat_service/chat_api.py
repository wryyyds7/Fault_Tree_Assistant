import os
import sys
import json
import uuid
import datetime
from typing import List, Dict, Any, Optional, Generator
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
import requests

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from llm_client import LLMClient
from vector_retriever import VectorRetriever
from session_repository import ChatSessionRepository

load_dotenv()

app = FastAPI(title="AI Chat Service", version="1.0.0")

llm_client = LLMClient()
vector_retriever = VectorRetriever()
session_repo = ChatSessionRepository()

class ChatMessage(BaseModel):
    role: str
    content: str
    timestamp: Optional[str] = None

class ConversationSession(BaseModel):
    sessionId: str
    userId: str
    title: str
    createdAt: str
    updatedAt: str
    messages: List[ChatMessage] = []
    linkedTreeIds: List[str] = []
    linkedDocIds: List[str] = []

class ChatRequest(BaseModel):
    sessionId: Optional[str] = None
    userId: str
    message: str
    linkedTreeIds: Optional[List[str]] = []
    linkedDocIds: Optional[List[str]] = []
    topEvent: Optional[str] = None

class ChatResponse(BaseModel):
    sessionId: str
    message: ChatMessage
    relatedTrees: Optional[List[Dict[str, Any]]] = None
    relatedDocs: Optional[List[Dict[str, Any]]] = None

class LinkTreeRequest(BaseModel):
    sessionId: str
    treeId: str

class LinkDocumentRequest(BaseModel):
    sessionId: str
    docId: str

class CreateSessionRequest(BaseModel):
    userId: str
    title: Optional[str] = None
    linkedTreeIds: Optional[List[str]] = []
    linkedDocIds: Optional[List[str]] = []

def _snake_to_camel(snake_str: str) -> str:
    components = snake_str.split('_')
    return components[0] + ''.join(x.title() for x in components[1:])

def _convert_to_camel_case(obj: Any) -> Any:
    if isinstance(obj, dict):
        return {_snake_to_camel(k): _convert_to_camel_case(v) for k, v in obj.items()}
    elif isinstance(obj, list):
        return [_convert_to_camel_case(item) for item in obj]
    return obj

def _get_timestamp() -> str:
    return datetime.datetime.now().isoformat()

def _get_fault_tree_service_url() -> str:
    return os.getenv('FAULT_TREE_SERVICE_URL', 'http://localhost:8084')

def _get_document_service_url() -> str:
    url = os.getenv('DOCUMENT_SERVICE_URL', 'http://localhost:8080')
    print(f"[DEBUG] DOCUMENT_SERVICE_URL env: {os.getenv('DOCUMENT_SERVICE_URL', 'not set')}")
    print(f"[DEBUG] _get_document_service_url returning: {url}")
    return url

def _build_chat_context(session_data: Dict[str, Any], current_message: str) -> str:
    context_parts = []

    linked_tree_ids = session_data.get('linkedTreeIds', [])
    linked_doc_ids = session_data.get('linkedDocIds', [])
    messages = session_data.get('messages', [])

    if linked_tree_ids:
        context_parts.append("【关联故障树】")
        for tree_id in linked_tree_ids:
            try:
                response = requests.get(
                    f"{_get_fault_tree_service_url()}/api/v1/fault-trees/{tree_id}",
                    timeout=10
                )
                if response.status_code == 200:
                    data = response.json()
                    tree_data = data.get('treeData', {})
                    context_parts.append(f"故障树ID: {tree_id}")
                    context_parts.append(f"名称: {tree_data.get('eventName', data.get('name', 'Unknown'))}")
                    context_parts.append(f"类型: {tree_data.get('eventType', 'Unknown')}")
                    context_parts.append(f"结构: {json.dumps(tree_data, ensure_ascii=False)[:500]}")
            except Exception as e:
                context_parts.append(f"故障树ID: {tree_id} (获取失败)")
        context_parts.append("")

    if linked_doc_ids:
        context_parts.append("【关联文档】")
        for doc_id in linked_doc_ids:
            try:
                response = requests.get(
                    f"{_get_document_service_url()}/api/v1/documents/{doc_id}/content",
                    timeout=10
                )
                if response.status_code == 200:
                    content = response.json().get('content', [])
                    if isinstance(content, list):
                        content = ' '.join(str(c) for c in content[:5])
                    context_parts.append(f"文档ID: {doc_id}")
                    context_parts.append(f"内容摘要: {str(content)[:300]}...")
            except Exception as e:
                context_parts.append(f"文档ID: {doc_id} (获取失败)")
        context_parts.append("")

    if messages:
        context_parts.append("【对话历史】")
        recent_messages = messages[-6:]
        for msg in recent_messages:
            role_name = "用户" if msg.get('role') == 'user' else "助手"
            context_parts.append(f"{role_name}: {str(msg.get('content', ''))[:200]}")
        context_parts.append("")

    return "\n".join(context_parts)

def _query_knowledge_for_response(user_message: str, context: str, linked_doc_ids: List[str], user_id: str = None) -> Dict[str, Any]:
    print(f"[DEBUG] _query_knowledge_for_response called with:")
    print(f"  - user_message: {user_message[:50]}...")
    print(f"  - linked_doc_ids: {linked_doc_ids}")
    print(f"  - user_id: {user_id}")
    print(f"  - context length: {len(context)}")

    knowledge_context = context
    full_doc_content = []

    if linked_doc_ids:
        print(f"[DEBUG] Processing {len(linked_doc_ids)} linked documents")
        knowledge_context += "\n\n【参考文档内容】\n"
        for doc_id in linked_doc_ids:
            try:
                doc_url = f"{_get_document_service_url()}/api/v1/documents/{doc_id}/paragraphs"
                params = {"userId": user_id} if user_id else {}
                print(f"[DEBUG] Fetching document {doc_id} from {doc_url} with params {params}")

                response = requests.get(doc_url, params=params, timeout=30)
                print(f"[DEBUG] Response status: {response.status_code}")

                if response.status_code == 200:
                    paragraphs = response.json()
                    print(f"[DEBUG] Got {len(paragraphs)} paragraphs from document {doc_id}")

                    if paragraphs:
                        knowledge_context += f"\n--- 文档 {doc_id} ---\n"
                        for para in paragraphs:
                            content = para.get('content', '') or para.get('text', '')
                            if content:
                                knowledge_context += f"{content}\n"
                                full_doc_content.append(content)
                else:
                    print(f"[DEBUG] Failed to fetch document {doc_id}: {response.text}")
            except Exception as e:
                print(f"[ERROR] Error fetching document {doc_id}: {e}")

        print(f"[DEBUG] Total document content length: {len(knowledge_context)}")
        print(f"[DEBUG] First 200 chars of knowledge_context:\n{knowledge_context[:200]}")
    else:
        print("[DEBUG] No linked_doc_ids provided")

    return {"context": knowledge_context, "paragraphs": []}

def _construct_system_prompt() -> str:
    return """你是一个专业的工业设备故障树分析助手。你的职责包括：

1. 回答用户关于故障树构建、分析和优化的问题
2. 基于提供的故障树数据和文档知识，给出专业的建议
3. 解释故障树分析的基本概念和方法
4. 帮助用户理解和改进他们的故障树结构

请用专业、清晰、友好的语言回答。如果涉及专业知识，要确保解释准确。

当用户提供故障树相关问题时，可以参考以下格式回答：
- 顶事件（故障现象）- 中间事件（过渡原因）- 底事件（根本原因）
- 使用 AND、OR、XOR 等逻辑门表示事件关系

请始终以助手身份回答，不要透露你是AI模型。"""

def _construct_user_prompt(user_message: str, context: str) -> str:
    prompt = f"""【用户问题】
{user_message}

"""
    if context:
        prompt += f"""【相关上下文】
{context}

"""
    prompt += """请根据上述信息回答用户的问题。如果涉及故障树分析，请给出具体的建议和解释。"""
    return prompt

@app.get("/health")
def health_check():
    return {"status": "healthy", "service": "ai-chat-service"}

@app.post("/api/v1/chat/sessions", response_model=ConversationSession)
def create_session(request: CreateSessionRequest):
    try:
        session = session_repo.create_session(
            user_id=request.userId,
            title=request.title,
            linked_tree_ids=request.linkedTreeIds,
            linked_doc_ids=request.linkedDocIds
        )
        return _convert_to_camel_case(session)
    except Exception as e:
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=f"Create session failed: {str(e)}")

@app.get("/api/v1/chat/sessions/{session_id}", response_model=ConversationSession)
def get_session(session_id: str):
    session = session_repo.get_session(session_id)
    if not session:
        raise HTTPException(status_code=404, detail="Session not found")
    return _convert_to_camel_case(session)

@app.get("/api/v1/chat/sessions/user/{user_id}")
def get_user_sessions(user_id: str):
    sessions = session_repo.get_user_sessions(user_id)
    return [_convert_to_camel_case(s) for s in sessions]

@app.delete("/api/v1/chat/sessions/{session_id}")
def delete_session(session_id: str):
    success = session_repo.delete_session(session_id)
    if not success:
        raise HTTPException(status_code=404, detail="Session not found")
    return {"message": "Session deleted"}

@app.post("/api/v1/chat/link/tree")
def link_tree(request: LinkTreeRequest):
    linked_trees = session_repo.link_tree(request.sessionId, request.treeId)
    if linked_trees is None:
        raise HTTPException(status_code=404, detail="Session not found")
    return {"message": "Tree linked", "linkedTreeIds": linked_trees}

@app.delete("/api/v1/chat/sessions/{session_id}/tree/{tree_id}")
def unlink_tree(session_id: str, tree_id: str):
    linked_trees = session_repo.unlink_tree(session_id, tree_id)
    if linked_trees is None:
        raise HTTPException(status_code=404, detail="Session not found")
    return {"message": "Tree unlinked", "linkedTreeIds": linked_trees}

@app.post("/api/v1/chat/link/document")
def link_document(request: LinkDocumentRequest):
    linked_docs = session_repo.link_document(request.sessionId, request.docId)
    if linked_docs is None:
        raise HTTPException(status_code=404, detail="Session not found")
    return {"message": "Document linked", "linkedDocIds": linked_docs}

@app.delete("/api/v1/chat/sessions/{session_id}/doc/{doc_id}")
def unlink_document(session_id: str, doc_id: str):
    linked_docs = session_repo.unlink_document(session_id, doc_id)
    if linked_docs is None:
        raise HTTPException(status_code=404, detail="Session not found")
    return {"message": "Document unlinked", "linkedDocIds": linked_docs}

@app.post("/api/v1/chat/chat", response_model=ChatResponse)
def chat(request: ChatRequest):
    user_id = request.userId
    message = request.message

    print(f"[DEBUG] _stream_chat_response called:")
    print(f"  - user_id: {user_id}")
    print(f"  - message: {message[:50]}...")
    print(f"  - request.linkedDocIds: {request.linkedDocIds}")
    print(f"  - request.linkedTreeIds: {request.linkedTreeIds}")

    if request.sessionId:
        session_data = session_repo.get_session(request.sessionId)
        if session_data:
            print(f"[DEBUG] Existing session found: {request.sessionId}")
            print(f"  - session linkedDocIds: {session_data.get('linkedDocIds', [])}")
            session_repo.update_session(
                request.sessionId,
                linked_tree_ids=request.linkedTreeIds or session_data.get('linkedTreeIds', []),
                linked_doc_ids=request.linkedDocIds or session_data.get('linkedDocIds', [])
            )
            linked_doc_ids = request.linkedDocIds or session_data.get('linkedDocIds', [])
            print(f"[DEBUG] Using linked_doc_ids: {linked_doc_ids}")
    else:
        new_session = session_repo.create_session(
            user_id=user_id,
            title=message[:30] + "..." if len(message) > 30 else message,
            linked_tree_ids=request.linkedTreeIds or [],
            linked_doc_ids=request.linkedDocIds or []
        )
        request.sessionId = new_session['sessionId']
        session_data = new_session

    session_repo.add_message(request.sessionId, "user", message)
    session_data = session_repo.get_session(request.sessionId)

    context = _build_chat_context(session_data, message)
    linked_doc_ids = request.linkedDocIds or session_data.get('linkedDocIds', [])
    knowledge_info = _query_knowledge_for_response(message, context, linked_doc_ids, request.userId)
    full_context = knowledge_info.get("context", context)

    system_prompt = _construct_system_prompt()
    user_prompt = _construct_user_prompt(message, full_context)

    try:
        response_text = llm_client.chat(
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt}
            ]
        )
    except Exception as e:
        response_text = f"抱歉，我现在无法回答您的问题。请稍后再试。错误信息: {str(e)}"

    assistant_msg = session_repo.add_message(request.sessionId, "assistant", response_text)

    related_trees = []
    linked_tree_ids = session_data.get('linkedTreeIds', [])
    if linked_tree_ids:
        for tree_id in linked_tree_ids[:3]:
            try:
                response = requests.get(
                    f"{_get_fault_tree_service_url()}/api/v1/fault-trees/{tree_id}",
                    timeout=10
                )
                if response.status_code == 200:
                    data = response.json()
                    related_trees.append({
                        "treeId": tree_id,
                        "name": data.get('name', 'Unknown'),
                        "equipmentType": data.get('equipmentType', '')
                    })
            except Exception:
                pass

    related_docs = []
    if linked_doc_ids:
        for doc_id in linked_doc_ids[:3]:
            try:
                response = requests.get(
                    f"{_get_document_service_url()}/api/v1/documents/{doc_id}",
                    timeout=10
                )
                if response.status_code == 200:
                    data = response.json()
                    related_docs.append({
                        "documentId": doc_id,
                        "fileName": data.get('fileName', 'Unknown'),
                        "sourceType": data.get('sourceType', '')
                    })
            except Exception:
                pass

    return ChatResponse(
        sessionId=request.sessionId,
        message=ChatMessage(
            role=assistant_msg['role'],
            content=assistant_msg['content'],
            timestamp=assistant_msg['timestamp']
        ),
        relatedTrees=related_trees,
        relatedDocs=related_docs
    )

def _stream_chat_response(request: ChatRequest) -> Generator[str, None, None]:
    """流式生成聊天响应"""
    user_id = request.userId
    message = request.message

    if request.sessionId:
        session_data = session_repo.get_session(request.sessionId)
        if session_data:
            session_repo.update_session(
                request.sessionId,
                linked_tree_ids=request.linkedTreeIds or session_data.get('linkedTreeIds', []),
                linked_doc_ids=request.linkedDocIds or session_data.get('linkedDocIds', [])
            )
    else:
        new_session = session_repo.create_session(
            user_id=user_id,
            title=message[:30] + "..." if len(message) > 30 else message,
            linked_tree_ids=request.linkedTreeIds or [],
            linked_doc_ids=request.linkedDocIds or []
        )
        request.sessionId = new_session['sessionId']
        session_data = new_session

    session_repo.add_message(request.sessionId, "user", message)
    session_data = session_repo.get_session(request.sessionId)

    context = _build_chat_context(session_data, message)
    linked_doc_ids = request.linkedDocIds or session_data.get('linkedDocIds', [])
    knowledge_info = _query_knowledge_for_response(message, context, linked_doc_ids, request.userId)
    full_context = knowledge_info.get("context", context)

    system_prompt = _construct_system_prompt()
    user_prompt = _construct_user_prompt(message, full_context)

    full_response = ""
    try:
        for chunk in llm_client.chat_stream(
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt}
            ]
        ):
            full_response += chunk
            yield f"data: {json.dumps({'content': chunk}, ensure_ascii=False)}\n\n"
    except Exception as e:
        error_msg = f"抱歉，发生了错误：{str(e)}"
        full_response = error_msg
        yield f"data: {json.dumps({'content': error_msg}, ensure_ascii=False)}\n\n"

    session_repo.add_message(request.sessionId, "assistant", full_response)

    linked_tree_ids = session_data.get('linkedTreeIds', [])
    if linked_tree_ids:
        for tree_id in linked_tree_ids[:3]:
            try:
                response = requests.get(
                    f"{_get_fault_tree_service_url()}/api/v1/fault-trees/{tree_id}",
                    timeout=10
                )
                if response.status_code == 200:
                    data = response.json()
                    yield f"data: {json.dumps({'tree': {'treeId': tree_id, 'name': data.get('name', 'Unknown'), 'equipmentType': data.get('equipmentType', '')}}, ensure_ascii=False)}\n\n"
            except Exception:
                pass

    if linked_doc_ids:
        for doc_id in linked_doc_ids[:3]:
            try:
                response = requests.get(
                    f"{_get_document_service_url()}/api/v1/documents/{doc_id}",
                    timeout=10
                )
                if response.status_code == 200:
                    data = response.json()
                    yield f"data: {json.dumps({'doc': {'documentId': doc_id, 'fileName': data.get('fileName', 'Unknown'), 'sourceType': data.get('sourceType', '')}}, ensure_ascii=False)}\n\n"
            except Exception:
                pass

    yield f"data: {json.dumps({'done': True, 'sessionId': request.sessionId}, ensure_ascii=False)}\n\n"

@app.post("/api/v1/chat/chat/stream")
def chat_stream(request: ChatRequest):
    """流式聊天接口"""
    return StreamingResponse(
        _stream_chat_response(request),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no"
        }
    )


class FaultTreeAnalysisRequest(BaseModel):
    prompt: str
    systemPrompt: Optional[str] = None


@app.post("/api/v1/chat/analyze-fault-tree")
def analyze_fault_tree(request: FaultTreeAnalysisRequest):
    """分析故障树并给出AI建议"""
    try:
        system_prompt = request.systemPrompt or """你是一位专业的工业故障树分析(FTA)专家。请根据提供的故障树结构和验证结果，给出专业的分析和建议。
请用简洁、专业的语言给出分析，不要包含Markdown格式。"""

        user_prompt = request.prompt

        messages = [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt}
        ]

        print(f"[DEBUG] Analyzing fault tree with AI")
        response = llm_client.chat(messages)
        
        return {"suggestion": response, "success": True}
        
    except Exception as e:
        print(f"[ERROR] Fault tree analysis failed: {e}")
        import traceback
        traceback.print_exc()
        return {
            "suggestion": "分析失败：" + str(e),
            "success": False
        }

@app.get("/api/v1/chat/sessions/{session_id}/messages")
def get_session_messages(session_id: str):
    session = session_repo.get_session(session_id)
    if not session:
        raise HTTPException(status_code=404, detail="Session not found")
    return session.get('messages', [])

@app.get("/api/v1/chat/trees/{user_id}")
def get_user_fault_trees(user_id: str):
    try:
        response = requests.get(
            f"{_get_fault_tree_service_url()}/api/v1/fault-trees",
            params={"createdBy": user_id},
            timeout=10
        )
        if response.status_code == 200:
            return response.json()
    except Exception as e:
        print(f"Error fetching fault trees: {e}")
    return []

@app.get("/api/v1/chat/documents/{user_id}")
def get_user_documents(user_id: str):
    try:
        response = requests.get(
            f"{_get_document_service_url()}/api/v1/documents",
            params={"userId": user_id},
            timeout=10
        )
        if response.status_code == 200:
            return response.json()
    except Exception as e:
        print(f"Error fetching documents: {e}")
    return []

@app.post("/api/v1/chat/sessions/{session_id}/update-title")
def update_session_title(session_id: str, new_title: str):
    session = session_repo.update_session(session_id, title=new_title)
    if not session:
        raise HTTPException(status_code=404, detail="Session not found")
    return {"message": "Title updated", "title": new_title}

if __name__ == "__main__":
    import uvicorn
    port = int(os.getenv('CHAT_API_PORT', '8001'))
    uvicorn.run(app, host="0.0.0.0", port=port)