# Conflict Detector
# 冲突检测与消解器

from enum import Enum
from dataclasses import dataclass, field
from typing import List, Dict, Any, Optional, Tuple
from dataclasses import dataclass

class ConflictType(Enum):
    """冲突类型"""
    CONTRADICTION = "contradiction"     # 矛盾型：两个段落描述相反
    OMISSION = "omission"              # 缺失型：一个提到，另一个没提
    DIFFERENCE = "difference"           # 差异型：对同一关系描述不一致
    UNKNOWN = "unknown"                 # 未知类型

class ConflictSeverity(Enum):
    """冲突严重程度"""
    LOW = "low"       # 低：可以自动合并
    MEDIUM = "medium" # 中：需要人工确认
    HIGH = "high"     # 高：必须人工干预

@dataclass
class Conflict:
    """冲突"""
    conflict_id: str
    conflict_type: ConflictType
    severity: ConflictSeverity
    paragraph_ids: List[str]
    description: str
    evidence: Dict[str, Any]
    resolution_suggestion: str
    resolved: bool = False
    resolution: Optional[str] = None
    expert_decision: Optional[str] = None

class ConflictDetector:
    """冲突检测器"""

    def __init__(self):
        """初始化冲突检测器"""
        self.conflicts: List[Conflict] = []

    def detect_conflicts(
        self,
        clusters: List[Dict[str, Any]],
        paragraphs: Dict[str, Dict[str, Any]]
    ) -> List[Conflict]:
        """
        检测段落间的冲突

        参数:
            clusters: 段落聚类列表
            paragraphs: 段落字典（paragraph_id -> paragraph_data）

        返回:
            冲突列表
        """
        self.conflicts = []

        for cluster in clusters:
            paragraph_ids = cluster.get('paragraph_ids', [])
            if len(paragraph_ids) < 2:
                continue

            self._detect_within_cluster_conflicts(paragraph_ids, paragraphs)

        return self.conflicts

    def _detect_within_cluster_conflicts(
        self,
        paragraph_ids: List[str],
        paragraphs: Dict[str, Dict[str, Any]]
    ):
        """
        检测聚类内部的冲突

        参数:
            paragraph_ids: 段落 ID 列表
            paragraphs: 段落字典
        """
        for i in range(len(paragraph_ids)):
            for j in range(i + 1, len(paragraph_ids)):
                para_a = paragraphs.get(paragraph_ids[i], {})
                para_b = paragraphs.get(paragraph_ids[j], {})

                if not para_a or not para_b:
                    continue

                conflict = self._analyze_pair_conflict(para_a, para_b)
                if conflict:
                    self.conflicts.append(conflict)

    def _analyze_pair_conflict(
        self,
        para_a: Dict[str, Any],
        para_b: Dict[str, Any]
    ) -> Optional[Conflict]:
        """
        分析一对段落的冲突

        参数:
            para_a: 段落 A
            para_b: 段落 B

        返回:
            冲突对象，如果没有冲突则返回 None
        """
        content_a = para_a.get('content', '').lower()
        content_b = para_b.get('content', '').lower()

        conflict_type, severity, description, evidence = self._classify_conflict(
            content_a, content_b, para_a, para_b
        )

        if conflict_type == ConflictType.UNKNOWN:
            return None

        conflict_id = f"conflict_{len(self.conflicts)}"

        return Conflict(
            conflict_id=conflict_id,
            conflict_type=conflict_type,
            severity=severity,
            paragraph_ids=[para_a.get('paragraph_id', ''), para_b.get('paragraph_id', '')],
            description=description,
            evidence=evidence,
            resolution_suggestion=self._generate_resolution_suggestion(conflict_type, severity, evidence)
        )

    def _classify_conflict(
        self,
        content_a: str,
        content_b: str,
        para_a: Dict[str, Any],
        para_b: Dict[str, Any]
    ) -> Tuple[ConflictType, ConflictSeverity, str, Dict[str, Any]]:
        """
        分类冲突类型和严重程度

        参数:
            content_a: 段落 A 内容（小写）
            content_b: 段落 B 内容（小写）
            para_a: 段落 A 完整数据
            para_b: 段落 B 完整数据

        返回:
            (冲突类型, 严重程度, 描述, 证据)
        """
        positive_keywords = ['是', '导致', '引起', '由于', '造成', '可以', '会']
        negative_keywords = ['不是', '不会', '不能', '排除', '非', '无']

        has_positive_a = any(kw in content_a for kw in positive_keywords)
        has_negative_a = any(kw in content_a for kw in negative_keywords)
        has_positive_b = any(kw in content_b for kw in positive_keywords)
        has_negative_b = any(kw in content_b for kw in negative_keywords)

        if (has_positive_a and has_negative_b) or (has_negative_a and has_positive_b):
            return (
                ConflictType.CONTRADICTION,
                ConflictSeverity.HIGH,
                "两个段落对同一事实的描述相互矛盾",
                {
                    'paragraph_a': para_a.get('content', '')[:100],
                    'paragraph_b': para_b.get('content', '')[:100],
                    'type': 'contradiction_keywords'
                }
            )

        source_a = para_a.get('metadata', {}).get('source_type', 'unknown')
        source_b = para_b.get('metadata', {}).get('source_type', 'unknown')

        weight_a = para_a.get('metadata', {}).get('credibility_weight', 1.0)
        weight_b = para_b.get('metadata', {}).get('credibility_weight', 1.0)

        if abs(weight_a - weight_b) > 0.3:
            return (
                ConflictType.DIFFERENCE,
                ConflictSeverity.MEDIUM,
                f"两个段落来源权重差异较大（{weight_a} vs {weight_b}），需要人工确认",
                {
                    'paragraph_a': para_a.get('content', '')[:100],
                    'paragraph_b': para_b.get('content', '')[:100],
                    'weight_a': weight_a,
                    'weight_b': weight_b,
                    'source_a': source_a,
                    'source_b': source_b
                }
            )

        return (
            ConflictType.UNKNOWN,
            ConflictSeverity.LOW,
            "",
            {}
        )

    def _generate_resolution_suggestion(
        self,
        conflict_type: ConflictType,
        severity: ConflictSeverity,
        evidence: Dict[str, Any]
    ) -> str:
        """
        生成冲突解决建议

        参数:
            conflict_type: 冲突类型
            severity: 严重程度
            evidence: 证据

        返回:
            解决建议
        """
        if severity == ConflictSeverity.HIGH:
            return "严重冲突，必须由专家人工确认。建议优先参考行业标准或设备手册的描述。"

        if severity == ConflictSeverity.MEDIUM:
            weight_a = evidence.get('weight_a', 1.0)
            weight_b = evidence.get('weight_b', 1.0)
            if weight_a > weight_b:
                return f"建议采用段落 A（权重 {weight_a}），参考段落 B（权重 {weight_b}）。"
            else:
                return f"建议采用段落 B（权重 {weight_b}），参考段落 A（权重 {weight_a}）。"

        return "轻微差异，可以自动合并。建议综合两个段落的描述。"

    def resolve_conflict(
        self,
        conflict_id: str,
        resolution: str,
        expert_decision: Optional[str] = None
    ) -> bool:
        """
        解决冲突

        参数:
            conflict_id: 冲突 ID
            resolution: 解决方案
            expert_decision: 专家决策（可选）

        返回:
            是否解决成功
        """
        for conflict in self.conflicts:
            if conflict.conflict_id == conflict_id:
                conflict.resolved = True
                conflict.resolution = resolution
                conflict.expert_decision = expert_decision
                return True
        return False

    def get_unresolved_conflicts(self) -> List[Conflict]:
        """获取未解决的冲突列表"""
        return [c for c in self.conflicts if not c.resolved]

    def get_conflicts_by_severity(self, severity: ConflictSeverity) -> List[Conflict]:
        """按严重程度获取冲突"""
        return [c for c in self.conflicts if c.severity == severity]
