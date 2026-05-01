# Document Classification Service API
# 文档来源类型自动分类服务 API

import os
import sys
import json
from typing import List, Dict, Any, Optional
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from dotenv import load_dotenv

# 加载.env文件（指定绝对路径）
current_dir = os.path.dirname(os.path.abspath(__file__))
env_path = os.path.join(current_dir, '.env')
load_dotenv(env_path, override=True)
print(f"[classification_api] 加载环境变量: {env_path}")
print(f"[classification_api] BAILIAN_API_KEY: {os.environ.get('BAILIAN_API_KEY', 'NOT SET')}")

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from industrial_fta_common.fusion import (
    SourceType,
    DocumentClassifier,
    classify_document
)

app = FastAPI(title="Document Classification Service", version="1.0.0")

# 添加所有路由的调试信息
@app.on_event("startup")
async def startup_event():
    print("\n" + "="*70)
    print("★★☆ Classification API 启动中 ☆★★")
    print(f"服务端口: {os.getenv('CLASSIFICATION_API_PORT', '8002')}")
    print("="*70)
    print("\n所有可用路由:")
    for route in app.routes:
        print(f"  {route.methods} {route.path}")
    print("="*70 + "\n")

# 延迟初始化classifier，在第一次请求时才创建
_classifier = None

def get_classifier():
    global _classifier
    if _classifier is None:
        print("[classification_api] 初始化DocumentClassifier...")
        _classifier = DocumentClassifier()
        print("[classification_api] DocumentClassifier初始化完成")
    return _classifier

class ClassifyRequest(BaseModel):
    documentName: str
    content: str
    usePreMatching: Optional[bool] = True
    contentPreviewLength: Optional[int] = 800

class ClassificationResponse(BaseModel):
    sourceType: str
    confidence: float
    reasoning: str
    method: str
    credibilityWeight: float

@app.get("/")
def root():
    print("[classification_api] 访问根路径 /")
    return {"status": "ok", "service": "document-classification-api", "routes": [route.path for route in app.routes]}

@app.get("/health")
def health_check():
    print("[classification_api] 访问健康检查 /health")
    return {"status": "healthy", "service": "document-classification-api"}

@app.post("/api/v1/document/classify", response_model=ClassificationResponse)
def classify_document_endpoint(request: ClassifyRequest):
    """
    对文档进行来源类型自动分类

    使用LLM智能判断文档来源类型：
    - equipment_manual: 设备手册/说明书
    - maintenance_record: 维修记录/故障报告
    - industry_standard: 行业标准/规范文件
    - theory_paper: 理论文献/学术论文
    - user_feedback: 用户反馈/调查报告
    - unknown: 无法确定

    参数:
        request: 分类请求，包含文档名称和内容

    返回:
        分类结果
    """
    if not request.documentName:
        raise HTTPException(status_code=400, detail="文档名称不能为空")

    if not request.content or len(request.content.strip()) < 50:
        return ClassificationResponse(
            sourceType=SourceType.UNKNOWN.value,
            confidence=0.3,
            reasoning="内容不足以判断类型",
            method="none",
            credibilityWeight=0.5
        )

    try:
        classifier = get_classifier()
        result = classifier.classify(
            document_name=request.documentName,
            content=request.content,
            use_prematching=False,  # 直接使用LLM，不使用预匹配
            content_preview_length=request.contentPreviewLength
        )

        return ClassificationResponse(
            sourceType=result.source_type.value,
            confidence=result.confidence,
            reasoning=result.reasoning,
            method=result.method,
            credibilityWeight=result.get_credibility_weight()
        )
    except Exception as e:
        import traceback
        print(f"[classification_api] 分类失败: {e}")
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=f"分类失败: {str(e)}")

@app.get("/api/v1/document/source-types")
def get_source_types():
    """
    获取所有支持的文档来源类型

    返回:
        类型列表及其描述
    """
    return {
        "sourceTypes": [
            {
                "value": "equipment_manual",
                "label": "设备手册/说明书",
                "description": "包含设备型号、技术参数、操作说明、安装指南、维护保养说明的文档",
                "credibilityWeight": 1.0
            },
            {
                "value": "maintenance_record",
                "label": "维修记录/故障报告",
                "description": "包含故障代码、维修日期、故障现象、处理措施、检修报告的文档",
                "credibilityWeight": 0.8
            },
            {
                "value": "industry_standard",
                "label": "行业标准/规范文件",
                "description": "包含GB/T、ISO、IEC、ANSI等标准号的规范文件",
                "credibilityWeight": 1.2
            },
            {
                "value": "theory_paper",
                "label": "理论文献/学术论文",
                "description": "包含摘要、参考文献、实验方法、研究结论的学术论文",
                "credibilityWeight": 0.9
            },
            {
                "value": "user_feedback",
                "label": "用户反馈/调查报告",
                "description": "包含用户反馈、投诉、建议、满意度调查的文档",
                "credibilityWeight": 0.6
            },
            {
                "value": "unknown",
                "label": "无法确定",
                "description": "无法根据内容判断文档来源类型",
                "credibilityWeight": 0.5
            }
        ]
    }

if __name__ == "__main__":
    import uvicorn
    port = int(os.getenv('CLASSIFICATION_API_PORT', '8002'))
    uvicorn.run(app, host="0.0.0.0", port=port)
