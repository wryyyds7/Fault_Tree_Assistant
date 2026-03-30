# Fusion Engine
# 多文档融合引擎

from typing import List, Dict, Any, Optional, Tuple
from dataclasses import dataclass
import numpy as np

from .document_metadata import DocumentMetadata, ParagraphWithMetadata, SourceType
from .paragraph_cluster import ParagraphClusterer
from .conflict_detector import ConflictDetector, Conflict, ConflictType, ConflictSeverity

@dataclass
class FusionResult:
    """融合结果"""
    fused_paragraphs: List[Dict[str, Any]]
    clusters: List[Dict[str, Any]]
    conflicts: List[Dict[str, Any]]
    statistics: Dict[str, Any]

class FusionEngine:
    """多文档融合引擎"""

    def __init__(
        self,
        similarity_threshold: float = 0.7,
        auto_resolve_low_severity: bool = True
    ):
        """
        初始化融合引擎

        参数:
            similarity_threshold: 相似度阈值，默认 0.7
            auto_resolve_low_severity: 是否自动解决低严重程度的冲突
        """
        self.similarity_threshold = similarity_threshold
        self.auto_resolve_low_severity = auto_resolve_low_severity
        self.clusterer = ParagraphClusterer(similarity_threshold=similarity_threshold)
        self.conflict_detector = ConflictDetector()

    def fuse(
        self,
        paragraphs: List[Dict[str, Any]],
        embeddings: Optional[List[np.ndarray]] = None
    ) -> FusionResult:
        """
        融合多个文档的段落

        参数:
            paragraphs: 段落列表，每个段落包含：
                - paragraph_id: 段落 ID
                - content: 段落内容
                - metadata: 元数据（source_type, document_name, page_number 等）
            embeddings: 段落对应的向量嵌入（可选，如果不提供则使用内容本身）

        返回:
            融合结果
        """
        if not paragraphs:
            return FusionResult(
                fused_paragraphs=[],
                clusters=[],
                conflicts=[],
                statistics={}
            )

        paragraph_dict = {p['paragraph_id']: p for p in paragraphs}

        if embeddings is None:
            embeddings = [None] * len(paragraphs)

        clusters_data = self._create_cluster_data(paragraphs, embeddings)

        clusters = self.clusterer.cluster(paragraphs, embeddings)

        cluster_dicts = []
        for cluster in clusters:
            cluster_dict = {
                'cluster_id': cluster.cluster_id,
                'topic': cluster.topic,
                'paragraph_ids': cluster.paragraph_ids,
                'representative_paragraph_id': cluster.representative_paragraph_id,
                'similarity_score': cluster.similarity_score,
                'paragraphs': [
                    paragraph_dict[pid]
                    for pid in cluster.paragraph_ids
                    if pid in paragraph_dict
                ]
            }
            cluster_dicts.append(cluster_dict)

        conflicts = self.conflict_detector.detect_conflicts(cluster_dicts, paragraph_dict)

        if self.auto_resolve_low_severity:
            self._auto_resolve_low_severity_conflicts()

        fused_paragraphs = self._merge_clusters(cluster_dicts, paragraph_dict)

        statistics = self._calculate_statistics(paragraphs, clusters, conflicts)

        return FusionResult(
            fused_paragraphs=fused_paragraphs,
            clusters=cluster_dicts,
            conflicts=[self._conflict_to_dict(c) for c in conflicts],
            statistics=statistics
        )

    def _create_cluster_data(
        self,
        paragraphs: List[Dict[str, Any]],
        embeddings: List[Optional[np.ndarray]]
    ) -> List[Dict[str, Any]]:
        """
        创建聚类数据

        参数:
            paragraphs: 段落列表
            embeddings: 嵌入列表

        返回:
            聚类数据列表
        """
        cluster_data = []
        for i, para in enumerate(paragraphs):
            data = {
                'paragraph_id': para['paragraph_id'],
                'content': para['content'],
                'metadata': para.get('metadata', {}),
                'embedding': embeddings[i] if i < len(embeddings) else None
            }
            cluster_data.append(data)
        return cluster_data

    def _auto_resolve_low_severity_conflicts(self):
        """自动解决低严重程度的冲突"""
        for conflict in self.conflict_detector.conflicts:
            if conflict.severity == ConflictSeverity.LOW and not conflict.resolved:
                conflict.resolved = True
                conflict.resolution = "自动合并：综合多个段落的描述"

    def _merge_clusters(
        self,
        clusters: List[Dict[str, Any]],
        paragraph_dict: Dict[str, Dict[str, Any]]
    ) -> List[Dict[str, Any]]:
        """
        合并聚类中的段落

        参数:
            clusters: 聚类列表
            paragraph_dict: 段落字典

        返回:
            融合后的段落列表
        """
        fused_paragraphs = []

        for cluster in clusters:
            if len(cluster['paragraph_ids']) == 1:
                pid = cluster['paragraph_ids'][0]
                fused_paragraphs.append(paragraph_dict[pid])
            else:
                merged = self._merge_cluster_paragraphs(cluster, paragraph_dict)
                fused_paragraphs.append(merged)

        return fused_paragraphs

    def _merge_cluster_paragraphs(
        self,
        cluster: Dict[str, Any],
        paragraph_dict: Dict[str, Dict[str, Any]]
    ) -> Dict[str, Any]:
        """
        合并同一聚类中的多个段落

        参数:
            cluster: 聚类数据
            paragraph_dict: 段落字典

        返回:
            合并后的段落
        """
        paragraph_ids = cluster['paragraph_ids']
        paragraphs = [
            paragraph_dict[pid]
            for pid in paragraph_ids
            if pid in paragraph_dict
        ]

        if not paragraphs:
            return {}

        max_weight_para = max(
            paragraphs,
            key=lambda p: p.get('metadata', {}).get('credibility_weight', 0)
        )

        merged_content = self._merge_content(paragraphs)

        merged_paragraph = {
            'paragraph_id': cluster['cluster_id'],
            'content': merged_content,
            'metadata': max_weight_para.get('metadata', {}),
            'source': 'merged',
            'source_paragraph_ids': paragraph_ids,
            'representative_paragraph_id': cluster.get('representative_paragraph_id')
        }

        return merged_paragraph

    def _merge_content(self, paragraphs: List[Dict[str, Any]]) -> str:
        """
        合并多个段落的内容

        参数:
            paragraphs: 段落列表

        返回:
            合并后的内容
        """
        if len(paragraphs) == 1:
            return paragraphs[0].get('content', '')

        contents = []
        for para in paragraphs:
            content = para.get('content', '').strip()
            if content:
                contents.append(content)

        return ' '.join(contents)

    def _calculate_statistics(
        self,
        original_paragraphs: List[Dict[str, Any]],
        clusters: List[Any],
        conflicts: List[Conflict]
    ) -> Dict[str, Any]:
        """
        计算融合统计信息

        参数:
            original_paragraphs: 原始段落列表
            clusters: 聚类列表
            conflicts: 冲突列表

        返回:
            统计信息
        """
        return {
            'original_paragraph_count': len(original_paragraphs),
            'cluster_count': len(clusters),
            'total_conflicts': len(conflicts),
            'resolved_conflicts': len([c for c in conflicts if c.resolved]),
            'unresolved_conflicts': len([c for c in conflicts if not c.resolved]),
            'conflicts_by_severity': {
                'high': len([c for c in conflicts if c.severity == ConflictSeverity.HIGH]),
                'medium': len([c for c in conflicts if c.severity == ConflictSeverity.MEDIUM]),
                'low': len([c for c in conflicts if c.severity == ConflictSeverity.LOW])
            },
            'conflicts_by_type': {
                'contradiction': len([c for c in conflicts if c.conflict_type == ConflictType.CONTRADICTION]),
                'omission': len([c for c in conflicts if c.conflict_type == ConflictType.OMISSION]),
                'difference': len([c for c in conflicts if c.conflict_type == ConflictType.DIFFERENCE])
            }
        }

    def _conflict_to_dict(self, conflict: Conflict) -> Dict[str, Any]:
        """
        将冲突对象转换为字典

        参数:
            conflict: 冲突对象

        返回:
            冲突字典
        """
        return {
            'conflict_id': conflict.conflict_id,
            'conflict_type': conflict.conflict_type.value,
            'severity': conflict.severity.value,
            'paragraph_ids': conflict.paragraph_ids,
            'description': conflict.description,
            'evidence': conflict.evidence,
            'resolution_suggestion': conflict.resolution_suggestion,
            'resolved': conflict.resolved,
            'resolution': conflict.resolution,
            'expert_decision': conflict.expert_decision
        }

    def get_conflict_list(
        self,
        resolved: Optional[bool] = None,
        severity: Optional[str] = None
    ) -> List[Dict[str, Any]]:
        """
        获取冲突列表

        参数:
            resolved: 过滤已解决/未解决的冲突
            severity: 按严重程度过滤

        返回:
            冲突列表
        """
        conflicts = self.conflict_detector.conflicts

        if resolved is not None:
            conflicts = [c for c in conflicts if c.resolved == resolved]

        if severity:
            severity_enum = ConflictSeverity(severity)
            conflicts = [c for c in conflicts if c.severity == severity_enum]

        return [self._conflict_to_dict(c) for c in conflicts]
