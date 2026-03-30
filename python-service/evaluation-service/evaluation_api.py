# Evaluation Service API
# 评估服务 API

import os
import json
import uuid
from datetime import datetime
from typing import List, Dict, Any, Optional
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from dotenv import load_dotenv

from industrial_fta_common.evaluation import FaultTreeEvaluator, GoldStandardManager
from industrial_fta_common.evaluation.gold_standard import GoldStandardFaultTree

load_dotenv()

app = FastAPI(title="Evaluation Service API", version="1.0.0")

evaluator = FaultTreeEvaluator()
gold_standard_manager = GoldStandardManager()

evaluation_history: Dict[str, Dict[str, Any]] = {}

class EvaluationRequest(BaseModel):
    generatedTree: Dict[str, Any]
    goldStandardTreeId: Optional[str] = None
    goldStandardTree: Optional[Dict[str, Any]] = None

class EvaluationResponse(BaseModel):
    evaluationId: str
    overallScore: float
    metrics: Dict[str, float]
    missingEvents: List[Dict[str, Any]]
    extraEvents: List[Dict[str, Any]]
    wrongRelations: List[Dict[str, Any]]
    detailedReport: Dict[str, Any]

class GoldStandardAddRequest(BaseModel):
    name: str
    equipmentType: str
    faultMode: str
    difficultyLevel: str
    treeData: Dict[str, Any]
    metadata: Optional[Dict[str, Any]] = None

class GoldStandardResponse(BaseModel):
    treeId: str
    name: str
    equipmentType: str
    faultMode: str
    difficultyLevel: str
    createdAt: str

@app.get("/health")
def health_check():
    return {"status": "healthy", "service": "evaluation-api"}

@app.post("/api/v1/evaluation/evaluate", response_model=EvaluationResponse)
def evaluate_fault_tree(request: EvaluationRequest):
    """
    评估故障树质量

    参数:
        request: 评估请求，包含生成的故障树和黄金标准

    返回:
        评估结果
    """
    if not request.generatedTree:
        raise HTTPException(status_code=400, detail="生成的故障树不能为空")

    if not request.goldStandardTree and not request.goldStandardTreeId:
        raise HTTPException(status_code=400, detail="必须提供黄金标准树或黄金标准树ID")

    if request.goldStandardTreeId:
        gold_standard = gold_standard_manager.get_gold_standard(request.goldStandardTreeId)
        if not gold_standard:
            raise HTTPException(status_code=404, detail="找不到指定的黄金标准树")
        gold_standard_data = gold_standard.tree_data
    else:
        gold_standard_data = request.goldStandardTree

    result = evaluator.evaluate(request.generatedTree, gold_standard_data)

    evaluation_id = f"eval_{uuid.uuid4().hex[:16]}"
    evaluation_history[evaluation_id] = {
        'evaluationId': evaluation_id,
        'generatedTree': request.generatedTree,
        'goldStandardTreeId': request.goldStandardTreeId,
        'result': {
            'overallScore': result.overall_score,
            'metrics': {
                'structureAccuracy': result.structure_accuracy,
                'eventPrecision': result.event_precision,
                'eventRecall': result.event_recall,
                'eventF1': result.event_f1,
                'relationAccuracy': result.relation_accuracy,
                'gateAccuracy': result.gate_accuracy
            },
            'missingEvents': result.missing_events,
            'extraEvents': result.extra_events,
            'wrongRelations': result.wrong_relations,
            'detailedReport': result.detailed_report
        },
        'evaluatedAt': datetime.now().isoformat()
    }

    return EvaluationResponse(
        evaluationId=evaluation_id,
        overallScore=result.overall_score,
        metrics={
            'structureAccuracy': result.structure_accuracy,
            'eventPrecision': result.event_precision,
            'eventRecall': result.event_recall,
            'eventF1': result.event_f1,
            'relationAccuracy': result.relation_accuracy,
            'gateAccuracy': result.gate_accuracy
        },
        missingEvents=result.missing_events,
        extraEvents=result.extra_events,
        wrongRelations=result.wrong_relations,
        detailedReport=result.detailed_report
    )

@app.get("/api/v1/evaluation/history")
def get_evaluation_history(limit: int = 10):
    """
    获取评估历史

    参数:
        limit: 返回记录数量限制

    返回:
        评估历史列表
    """
    history = list(evaluation_history.values())
    history.sort(key=lambda x: x['evaluatedAt'], reverse=True)
    return history[:limit]

@app.get("/api/v1/evaluation/statistics")
def get_evaluation_statistics():
    """
    获取评估统计信息

    返回:
        统计信息
    """
    if not evaluation_history:
        return {
            'totalEvaluations': 0,
            'averageScore': 0.0,
            'scoreDistribution': {}
        }

    scores = [item['result']['overallScore'] for item in evaluation_history.values()]

    score_distribution = {
        'excellent': len([s for s in scores if s >= 0.9]),
        'good': len([s for s in scores if 0.7 <= s < 0.9]),
        'fair': len([s for s in scores if 0.5 <= s < 0.7]),
        'poor': len([s for s in scores if s < 0.5])
    }

    return {
        'totalEvaluations': len(scores),
        'averageScore': sum(scores) / len(scores) if scores else 0.0,
        'scoreDistribution': score_distribution,
        'goldStandardCount': gold_standard_manager.count()
    }

@app.post("/api/v1/evaluation/gold-standard", response_model=GoldStandardResponse)
def add_gold_standard(request: GoldStandardAddRequest):
    """
    添加黄金标准故障树

    参数:
        request: 黄金标准添加请求

    返回:
        黄金标准响应
    """
    tree_id = f"gold_{uuid.uuid4().hex[:16]}"

    gold_standard = GoldStandardFaultTree(
        tree_id=tree_id,
        name=request.name,
        equipment_type=request.equipmentType,
        fault_mode=request.faultMode,
        difficulty_level=request.difficultyLevel,
        tree_data=request.treeData,
        created_at=datetime.now(),
        updated_at=datetime.now(),
        metadata=request.metadata or {}
    )

    success = gold_standard_manager.add_gold_standard(gold_standard)
    if not success:
        raise HTTPException(status_code=500, detail="添加黄金标准失败")

    return GoldStandardResponse(
        treeId=tree_id,
        name=request.name,
        equipmentType=request.equipmentType,
        faultMode=request.faultMode,
        difficultyLevel=request.difficultyLevel,
        createdAt=gold_standard.created_at.isoformat()
    )

@app.get("/api/v1/evaluation/gold-standard")
def list_gold_standards(
    equipmentType: Optional[str] = None,
    faultMode: Optional[str] = None,
    difficultyLevel: Optional[str] = None
):
    """
    列出黄金标准故障树

    参数:
        equipmentType: 设备类型过滤
        faultMode: 故障模式过滤
        difficultyLevel: 难度等级过滤

    返回:
        黄金标准列表
    """
    standards = gold_standard_manager.list_gold_standards(
        equipment_type=equipmentType,
        fault_mode=faultMode,
        difficulty_level=difficultyLevel
    )

    return [
        {
            'treeId': s.tree_id,
            'name': s.name,
            'equipmentType': s.equipment_type,
            'faultMode': s.fault_mode,
            'difficultyLevel': s.difficulty_level,
            'createdAt': s.created_at.isoformat(),
            'updatedAt': s.updated_at.isoformat()
        }
        for s in standards
    ]

@app.get("/api/v1/evaluation/gold-standard/{tree_id}")
def get_gold_standard(tree_id: str):
    """
    获取指定 ID 的黄金标准故障树

    参数:
        tree_id: 黄金标准树 ID

    返回:
        黄金标准详情
    """
    gold_standard = gold_standard_manager.get_gold_standard(tree_id)
    if not gold_standard:
        raise HTTPException(status_code=404, detail="找不到指定的黄金标准树")

    return {
        'treeId': gold_standard.tree_id,
        'name': gold_standard.name,
        'equipmentType': gold_standard.equipment_type,
        'faultMode': gold_standard.fault_mode,
        'difficultyLevel': gold_standard.difficulty_level,
        'treeData': gold_standard.tree_data,
        'createdAt': gold_standard.created_at.isoformat(),
        'updatedAt': gold_standard.updated_at.isoformat(),
        'metadata': gold_standard.metadata
    }

@app.delete("/api/v1/evaluation/gold-standard/{tree_id}")
def delete_gold_standard(tree_id: str):
    """
    删除黄金标准故障树

    参数:
        tree_id: 黄金标准树 ID

    返回:
        删除结果
    """
    success = gold_standard_manager.delete_gold_standard(tree_id)
    if not success:
        raise HTTPException(status_code=404, detail="找不到指定的黄金标准树")

    return {"message": "删除成功", "treeId": tree_id}

@app.get("/api/v1/evaluation/gold-standard/statistics")
def get_gold_standard_statistics():
    """
    获取黄金标准集统计信息

    返回:
        统计信息
    """
    return gold_standard_manager.get_statistics()
