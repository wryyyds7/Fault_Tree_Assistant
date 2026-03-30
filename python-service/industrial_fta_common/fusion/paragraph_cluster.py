# Paragraph Cluster
# 段落聚类器，将语义相似的段落聚类

from typing import List, Dict, Any, Set, Tuple
from dataclasses import dataclass
import numpy as np

@dataclass
class ParagraphCluster:
    """段落聚类"""
    cluster_id: str
    topic: str
    paragraph_ids: List[str]
    representative_paragraph_id: str
    similarity_score: float

class ParagraphClusterer:
    """段落聚类器"""

    def __init__(self, similarity_threshold: float = 0.7):
        """
        初始化聚类器

        参数:
            similarity_threshold: 相似度阈值，默认 0.7
        """
        self.similarity_threshold = similarity_threshold

    def cluster(
        self,
        paragraphs: List[Dict[str, Any]],
        embeddings: List[np.ndarray]
    ) -> List[ParagraphCluster]:
        """
        对段落进行聚类

        参数:
            paragraphs: 段落列表，每个段落包含 paragraph_id, content, metadata
            embeddings: 段落对应的向量嵌入

        返回:
            聚类列表
        """
        if len(paragraphs) != len(embeddings):
            raise ValueError("段落数量和嵌入数量不匹配")

        n = len(paragraphs)
        if n == 0:
            return []

        similarity_matrix = self._calculate_similarity_matrix(embeddings)

        clusters = []
        used_indices: Set[int] = set()
        cluster_id_counter = 0

        for i in range(n):
            if i in used_indices:
                continue

            cluster_indices = {i}
            used_indices.add(i)

            for j in range(i + 1, n):
                if j in used_indices:
                    continue
                if similarity_matrix[i][j] >= self.similarity_threshold:
                    cluster_indices.add(j)
                    used_indices.add(j)

            if len(cluster_indices) > 1:
                representative_idx = self._find_representative(
                    cluster_indices, similarity_matrix
                )
                topic = self._extract_topic(
                    paragraphs[representative_idx]['content']
                )

                cluster = ParagraphCluster(
                    cluster_id=f"cluster_{cluster_id_counter}",
                    topic=topic,
                    paragraph_ids=[
                        paragraphs[idx]['paragraph_id']
                        for idx in sorted(cluster_indices)
                    ],
                    representative_paragraph_id=paragraphs[representative_idx]['paragraph_id'],
                    similarity_score=self._calculate_cluster_similarity(
                        cluster_indices, similarity_matrix
                    )
                )
                clusters.append(cluster)
                cluster_id_counter += 1

        return clusters

    def _calculate_similarity_matrix(
        self,
        embeddings: List[np.ndarray]
    ) -> np.ndarray:
        """
        计算余弦相似度矩阵

        参数:
            embeddings: 向量嵌入列表

        返回:
            相似度矩阵
        """
        n = len(embeddings)
        similarity_matrix = np.zeros((n, n))

        for i in range(n):
            for j in range(n):
                if i == j:
                    similarity_matrix[i][j] = 1.0
                elif j > i:
                    similarity = self._cosine_similarity(embeddings[i], embeddings[j])
                    similarity_matrix[i][j] = similarity
                    similarity_matrix[j][i] = similarity

        return similarity_matrix

    def _cosine_similarity(self, vec1: np.ndarray, vec2: np.ndarray) -> float:
        """
        计算余弦相似度

        参数:
            vec1: 向量 1
            vec2: 向量 2

        返回:
            余弦相似度
        """
        dot_product = np.dot(vec1, vec2)
        norm1 = np.linalg.norm(vec1)
        norm2 = np.linalg.norm(vec2)

        if norm1 == 0 or norm2 == 0:
            return 0.0

        return float(dot_product / (norm1 * norm2))

    def _find_representative(
        self,
        cluster_indices: Set[int],
        similarity_matrix: np.ndarray
    ) -> int:
        """
        找到聚类中的代表性段落

        参数:
            cluster_indices: 聚类中的索引集合
            similarity_matrix: 相似度矩阵

        返回:
            代表性段落的索引
        """
        indices_list = list(cluster_indices)
        max_total_similarity = -1
        representative_idx = indices_list[0]

        for i in indices_list:
            total_similarity = sum(
                similarity_matrix[i][j] for j in indices_list if j != i
            )
            if total_similarity > max_total_similarity:
                max_total_similarity = total_similarity
                representative_idx = i

        return representative_idx

    def _extract_topic(self, content: str) -> str:
        """
        从段落内容中提取主题

        参数:
            content: 段落内容

        返回:
            主题（取前20个字符）
        """
        if len(content) <= 20:
            return content
        return content[:20] + "..."

    def _calculate_cluster_similarity(
        self,
        cluster_indices: Set[int],
        similarity_matrix: np.ndarray
    ) -> float:
        """
        计算聚类的内部相似度

        参数:
            cluster_indices: 聚类中的索引集合
            similarity_matrix: 相似度矩阵

        返回:
            平均相似度
        """
        indices_list = list(cluster_indices)
        total_similarity = 0.0
        count = 0

        for i in range(len(indices_list)):
            for j in range(i + 1, len(indices_list)):
                total_similarity += similarity_matrix[indices_list[i]][indices_list[j]]
                count += 1

        if count == 0:
            return 0.0

        return total_similarity / count
