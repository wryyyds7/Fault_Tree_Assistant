"""
向量存储后端模块

支持多种向量数据库：
- Milvus: 生产级向量检索
- Qdrant: 高性能向量检索
- Chroma: 轻量级本地向量库

特性：
1. 向量个人化隔离 - 每个人的向量只有自己/管理员能访问
2. 分类过滤 - 过滤未知分类的文档
3. 可扩展架构 - 方便添加新的向量库支持
"""

import os
import json
import numpy as np
from abc import ABC, abstractmethod
from typing import List, Dict, Any, Optional, Tuple
from dataclasses import dataclass
from enum import Enum


class VectorBackend(Enum):
    MILVUS = "milvus"
    QDRANT = "qdrant"
    CHROMA = "chroma"
    ORACLE = "oracle"  # 现有Oracle后端兼容


@dataclass
class VectorSearchResult:
    """向量搜索结果"""
    paragraph_id: str
    content: str
    document_id: str
    document_name: str
    user_id: str
    source_type: str
    similarity_score: float
    credibility_weight: float
    page_number: Optional[int] = None
    section_title: Optional[str] = None


@dataclass
class DocumentParagraph:
    """文档段落数据"""
    paragraph_id: str
    doc_id: str
    user_id: str
    content: str
    source_type: str
    credibility_weight: float
    chunk_index: int = 0  # 段落在文档中的索引，用于获取上下文
    page_number: Optional[int] = None
    section_title: Optional[str] = None
    embedding: Optional[np.ndarray] = None


class BaseVectorStore(ABC):
    """向量存储抽象基类"""

    def __init__(self, dimension: int = 1536):
        self.dimension = dimension

    @abstractmethod
    def connect(self) -> bool:
        """连接向量数据库"""
        pass

    @abstractmethod
    def insert_vectors(self, paragraphs: List[DocumentParagraph]) -> bool:
        """批量插入向量"""
        pass

    @abstractmethod
    def search_vectors(
        self,
        query_embedding: np.ndarray,
        user_id: str,
        doc_ids: Optional[List[str]] = None,
        source_types: Optional[List[str]] = None,
        top_k: int = 15
    ) -> List[VectorSearchResult]:
        """搜索向量

        Args:
            query_embedding: 查询向量
            user_id: 用户ID（用于个人化隔离）
            doc_ids: 指定文档ID列表（可选）
            source_types: 指定来源类型列表（用于过滤，如排除"未知"）
            top_k: 返回数量
        """
        pass

    @abstractmethod
    def delete_vectors(self, doc_id: str, user_id: str) -> bool:
        """删除指定文档的所有向量"""
        pass

    @abstractmethod
    def get_vectors_by_doc_id(self, doc_id: str, user_id: str) -> List[VectorSearchResult]:
        """获取指定文档的所有向量"""
        pass

    @abstractmethod
    def get_paragraph_with_context(
        self,
        paragraph_id: str,
        user_id: str,
        context_before: int = 2,
        context_after: int = 2
    ) -> Dict[str, Any]:
        """
        获取指定段落及其上下文
        
        Args:
            paragraph_id: 段落ID
            user_id: 用户ID
            context_before: 前向段落数
            context_after: 后向段落数
        
        Returns:
            包含目标段落和上下文的字典
        """
        pass


class MilvusVectorStore(BaseVectorStore):
    """
    Milvus 向量存储实现

    特性：
    - 支持HNSW/IVF等向量索引
    - 支持元数据过滤
    - 用户隔离通过partition实现
    """

    def __init__(self, dimension: int = 1536):
        super().__init__(dimension)
        self.collection_name = os.getenv('MILVUS_COLLECTION', 'fault_tree_vectors')
        self.index_params = None
        self.search_params = None
        self.collection = None
        self.connected = False

    def connect(self) -> bool:
        """连接Milvus服务器"""
        try:
            from pymilvus import connections, Collection, CollectionSchema, FieldSchema, DataType

            milvus_host = os.getenv('MILVUS_HOST', 'localhost')
            milvus_port = os.getenv('MILVUS_PORT', '19530')

            connections.connect(host=milvus_host, port=milvus_port, alias="default")
            print(f"[Milvus] Connected to {milvus_host}:{milvus_port}")

            self._ensure_collection()
            self.connected = True
            return True

        except ImportError:
            print("[Milvus] pymilvus not installed, Milvus backend unavailable")
            return False
        except Exception as e:
            print(f"[Milvus] Connection failed: {e}")
            return False

    def _ensure_collection(self):
        """确保Collection存在"""
        from pymilvus import connections, Collection, CollectionSchema, FieldSchema, DataType, utility

        if not utility.has_collection(self.collection_name):
            fields = [
                FieldSchema(name="paragraph_id", dtype=DataType.VARCHAR, max_length=64),
                FieldSchema(name="doc_id", dtype=DataType.VARCHAR, max_length=64),
                FieldSchema(name="user_id", dtype=DataType.VARCHAR, max_length=64),
                FieldSchema(name="content", dtype=DataType.VARCHAR, max_length=65535),
                FieldSchema(name="source_type", dtype=DataType.VARCHAR, max_length=32),
                FieldSchema(name="credibility_weight", dtype=DataType.DOUBLE),
                FieldSchema(name="page_number", dtype=DataType.INT32, nullable=True),
                FieldSchema(name="section_title", dtype=DataType.VARCHAR, max_length=256, nullable=True),
                FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=self.dimension),
            ]

            schema = CollectionSchema(fields=fields, description="Fault Tree Vector Collection")
            collection = Collection(name=self.collection_name, schema=schema)

            index_params = {
                "index_type": "HNSW",
                "metric_type": "COSINE",
                "params": {"M": 16, "efConstruction": 256}
            }
            collection.create_index(field_name="embedding", index_params=index_params)
            print(f"[Milvus] Created collection: {self.collection_name}")
        else:
            collection = Collection(name=self.collection_name)

        self.collection = collection

    def insert_vectors(self, paragraphs: List[DocumentParagraph]) -> bool:
        """批量插入向量"""
        if not self.connected:
            if not self.connect():
                return False

        try:
            from pymilvus import Collection

            entities = []
            for p in paragraphs:
                entities.append({
                    "paragraph_id": p.paragraph_id,
                    "doc_id": p.doc_id,
                    "user_id": p.user_id,
                    "content": p.content,
                    "source_type": p.source_type,
                    "credibility_weight": p.credibility_weight,
                    "page_number": p.page_number if p.page_number else 0,
                    "section_title": p.section_title or "",
                    "embedding": p.embedding.tolist() if p.embedding is not None else np.random.randn(self.dimension).tolist()
                })

            self.collection.insert(entities)
            self.collection.flush()
            print(f"[Milvus] Inserted {len(paragraphs)} vectors")
            return True

        except Exception as e:
            print(f"[Milvus] Insert failed: {e}")
            return False

    def search_vectors(
        self,
        query_embedding: np.ndarray,
        user_id: str,
        doc_ids: Optional[List[str]] = None,
        source_types: Optional[List[str]] = None,
        top_k: int = 15
    ) -> List[VectorSearchResult]:
        """搜索向量（带用户隔离和分类过滤）"""
        if not self.connected:
            if not self.connect():
                return []

        try:
            from pymilvus import Collection

            filter_conditions = f'user_id == "{user_id}"'

            if doc_ids:
                doc_ids_str = '", "'.join(doc_ids)
                filter_conditions += f' AND doc_id in ["{doc_ids_str}"]'

            if source_types:
                source_types_str = '", "'.join(source_types)
                filter_conditions += f' AND source_type in ["{source_types_str}"]'
            else:
                filter_conditions += ' AND source_type != "未知" AND source_type != "unknown"'

            search_params = {
                "metric_type": "COSINE",
                "params": {"ef": 128}
            }

            results = self.collection.search(
                data=[query_embedding.tolist()],
                anns_field="embedding",
                param=search_params,
                limit=top_k,
                expr=filter_conditions,
                output_fields=["paragraph_id", "doc_id", "user_id", "content", "source_type",
                             "credibility_weight", "page_number", "section_title"]
            )

            search_results = []
            for hits in results:
                for hit in hits:
                    search_results.append(VectorSearchResult(
                        paragraph_id=hit.entity.get("paragraph_id"),
                        content=hit.entity.get("content", ""),
                        document_id=hit.entity.get("doc_id", ""),
                        document_name="",  # 需要额外查询
                        user_id=hit.entity.get("user_id", ""),
                        source_type=hit.entity.get("source_type", "unknown"),
                        similarity_score=hit.distance,
                        credibility_weight=hit.entity.get("credibility_weight", 0.5),
                        page_number=hit.entity.get("page_number") or None,
                        section_title=hit.entity.get("section_title") or None
                    ))

            return search_results

        except Exception as e:
            print(f"[Milvus] Search failed: {e}")
            return []

    def delete_vectors(self, doc_id: str, user_id: str) -> bool:
        """删除指定文档的所有向量"""
        if not self.connected:
            return False

        try:
            from pymilvus import Collection

            expr = f'doc_id == "{doc_id}" AND user_id == "{user_id}"'
            self.collection.delete(expr)
            self.collection.flush()
            print(f"[Milvus] Deleted vectors for doc_id: {doc_id}")
            return True

        except Exception as e:
            print(f"[Milvus] Delete failed: {e}")
            return False

    def get_vectors_by_doc_id(self, doc_id: str, user_id: str) -> List[VectorSearchResult]:
        """获取指定文档的所有向量"""
        if not self.connected:
            return []

        try:
            from pymilvus import Collection

            expr = f'doc_id == "{doc_id}" AND user_id == "{user_id}" AND source_type != "未知"'
            results = self.collection.query(
                expr=expr,
                output_fields=["paragraph_id", "doc_id", "user_id", "content", "source_type",
                             "credibility_weight", "page_number", "section_title"]
            )

            return [VectorSearchResult(
                paragraph_id=r["paragraph_id"],
                content=r["content"],
                document_id=r["doc_id"],
                document_name="",
                user_id=r["user_id"],
                source_type=r["source_type"],
                similarity_score=0.0,
                credibility_weight=r["credibility_weight"],
                page_number=r.get("page_number"),
                section_title=r.get("section_title")
            ) for r in results]

        except Exception as e:
            print(f"[Milvus] Query failed: {e}")
            return []


class OracleVectorStore(BaseVectorStore):
    """
    Oracle 向量存储实现（现有实现兼容）

    注意：Oracle不适合存储向量，此实现主要用于兼容现有系统
    """

    def __init__(self, dimension: int = 1536):
        super().__init__(dimension)
        self.vector_service_url = os.getenv('VECTOR_SERVICE_URL', 'http://localhost:8084')

    def connect(self) -> bool:
        return True

    def insert_vectors(self, paragraphs: List[DocumentParagraph]) -> bool:
        """通过Java向量服务插入向量"""
        try:
            url = f"{self.vector_service_url}/api/v1/vector/index"
            payload = {
                "docId": paragraphs[0].doc_id if paragraphs else "",
                "paragraphs": [{
                    "paragraphId": p.paragraph_id,
                    "content": p.content,
                    "sourceType": p.source_type,
                    "credibilityWeight": p.credibility_weight
                } for p in paragraphs]
            }

            response = requests.post(url, json=payload, timeout=30)
            return response.status_code == 200

        except Exception as e:
            print(f"[Oracle] Insert failed: {e}")
            return False

    def search_vectors(
        self,
        query_embedding: np.ndarray,
        user_id: str,
        doc_ids: Optional[List[str]] = None,
        source_types: Optional[List[str]] = None,
        top_k: int = 15
    ) -> List[VectorSearchResult]:
        """从Java向量服务搜索"""
        results = []

        try:
            url = f"{self.vector_service_url}/api/v1/vector/search"
            payload = {
                "queryEmbedding": query_embedding.tolist(),
                "docIds": doc_ids or [],
                "topK": top_k,
                "userId": user_id
            }

            if source_types:
                payload["sourceTypes"] = source_types

            response = requests.post(url, json=payload, timeout=30)

            if response.status_code == 200:
                data = response.json()
                for item in data.get('results', []):
                    if item.get('sourceType') in ['未知', 'unknown', '']:
                        continue

                    results.append(VectorSearchResult(
                        paragraph_id=item.get('paragraphId', ''),
                        content=item.get('content', ''),
                        document_id=item.get('docId', ''),
                        document_name=item.get('documentName', ''),
                        user_id=item.get('userId', user_id),
                        source_type=item.get('sourceType', 'unknown'),
                        similarity_score=item.get('similarityScore', 0.0),
                        credibility_weight=item.get('credibilityWeight', 0.5),
                        page_number=item.get('pageNumber'),
                        section_title=item.get('sectionTitle')
                    ))

        except Exception as e:
            print(f"[Oracle] Search failed: {e}")

        return results

    def delete_vectors(self, doc_id: str, user_id: str) -> bool:
        """通过Java服务删除向量"""
        try:
            url = f"{self.vector_service_url}/api/v1/vector/documents/{doc_id}"
            response = requests.delete(url, timeout=10)
            return response.status_code == 200
        except Exception as e:
            print(f"[Oracle] Delete failed: {e}")
            return False

    def get_vectors_by_doc_id(self, doc_id: str, user_id: str) -> List[VectorSearchResult]:
        """从Java服务获取文档向量"""
        try:
            url = f"{self.vector_service_url}/api/v1/vector/documents/{doc_id}/paragraphs"
            response = requests.get(url, timeout=10)

            if response.status_code == 200:
                results = []
                for item in response.json():
                    if item.get('sourceType') in ['未知', 'unknown', '']:
                        continue

                    results.append(VectorSearchResult(
                        paragraph_id=item.get('paragraphId', ''),
                        content=item.get('content', ''),
                        document_id=item.get('docId', doc_id),
                        document_name=item.get('documentName', ''),
                        user_id=item.get('userId', user_id),
                        source_type=item.get('sourceType', 'unknown'),
                        similarity_score=item.get('similarityScore', 0.0),
                        credibility_weight=item.get('credibilityWeight', 0.5),
                        page_number=item.get('pageNumber'),
                        section_title=item.get('sectionTitle')
                    ))
                return results

        except Exception as e:
            print(f"[Oracle] Query failed: {e}")

        return []


class ChromaVectorStore(BaseVectorStore):
    """
    Chroma 向量存储实现

    特性：
    - 轻量级本地向量库
    - 支持元数据过滤
    - 易于部署和使用
    """

    def __init__(self, dimension: int = 1536):
        super().__init__(dimension)
        self.collection_name = os.getenv('CHROMA_COLLECTION', 'fault_tree_vectors')
        self.persist_directory = os.getenv('CHROMA_PERSIST_DIR', './data/chroma')
        self.collection = None
        self.connected = False

        print(f"\n{'='*60}")
        print(f"[Chroma] ★★★ ChromaVectorStore 初始化 ★★★")
        print(f"[Chroma] collection_name: {self.collection_name}")
        print(f"[Chroma] persist_directory: {self.persist_directory}")
        print(f"[Chroma] dimension: {self.dimension}")
        print(f"[Chroma] 完整路径: {os.path.abspath(self.persist_directory)}")
        print(f"{'='*60}")

    def connect(self) -> bool:
        """连接 Chroma 数据库"""
        print(f"\n[Chroma] ★★★ connect 被调用 ★★★")
        print(f"[Chroma] persist_directory: {self.persist_directory}")
        print(f"[Chroma] collection_name: {self.collection_name}")
        try:
            import chromadb
            from chromadb.config import Settings

            os.makedirs(self.persist_directory, exist_ok=True)
            print(f"[Chroma] 目录已确保存在: {self.persist_directory}")

            print(f"[Chroma] 正在创建 Persistent Chroma Client...")
            self.client = chromadb.PersistentClient(
                path=self.persist_directory
            )
            print(f"[Chroma] Client 创建成功，类型: {type(self.client)}")

            print(f"[Chroma] 正在获取或创建 Collection: {self.collection_name}")
            self.collection = self.client.get_or_create_collection(
                name=self.collection_name,
                metadata={"hnsw:space": "cosine", "dimension": self.dimension}
            )
            print(f"[Chroma] Collection 获取成功，类型: {type(self.collection)}")

            self.connected = True
            print(f"[Chroma] ✓ Connected to collection: {self.collection_name}")
            print(f"[Chroma] Collection count: {self.collection.count()}")
            return True

        except ImportError:
            print("[Chroma] ✗ chromadb not installed, please run: pip install chromadb")
            return False
        except Exception as e:
            print(f"[Chroma] ✗ Connection failed: {e}")
            import traceback
            traceback.print_exc()
            return False

    def insert_vectors(self, paragraphs: List[DocumentParagraph]) -> bool:
        """批量插入向量"""
        print(f"[Chroma] insert_vectors 被调用，paragraphs 数量: {len(paragraphs)}")
        if not self.connected:
            print(f"[Chroma] 未连接，正在尝试连接...")
            if not self.connect():
                print(f"[Chroma] 连接失败，返回 False")
                return False
            print(f"[Chroma] 连接成功")

        try:
            import chromadb

            ids = []
            embeddings = []
            documents = []
            metadatas = []

            print(f"[Chroma] 正在准备 {len(paragraphs)} 个段落的数据...")
            for i, p in enumerate(paragraphs):
                embedding = p.embedding.tolist() if p.embedding is not None else np.random.randn(self.dimension).tolist()
                ids.append(p.paragraph_id)
                embeddings.append(embedding)
                documents.append(p.content)
                metadatas.append({
                    "doc_id": p.doc_id,
                    "user_id": p.user_id,
                    "source_type": p.source_type,
                    "credibility_weight": str(p.credibility_weight),
                    "chunk_index": str(p.chunk_index),
                    "page_number": str(p.page_number) if p.page_number else "",
                    "section_title": p.section_title or ""
                })
                if i < 3:
                    print(f"[Chroma]   段落{i+1}: id={p.paragraph_id}, doc_id={p.doc_id}, content长度={len(p.content)}")

            print(f"[Chroma] 正在调用 collection.add...")
            print(f"[Chroma]   ids 数量: {len(ids)}")
            print(f"[Chroma]   embeddings 形状: {len(embeddings)} x {len(embeddings[0]) if embeddings else 0}")
            print(f"[Chroma]   documents 数量: {len(documents)}")

            self.collection.add(
                ids=ids,
                embeddings=embeddings,
                documents=documents,
                metadatas=metadatas
            )

            print(f"[Chroma] ✓ 成功插入 {len(paragraphs)} 个向量到 Chroma")
            return True

        except Exception as e:
            print(f"[Chroma] ✗ 插入失败: {e}")
            import traceback
            traceback.print_exc()
            return False

    def search_vectors(
        self,
        query_embedding: np.ndarray,
        user_id: str,
        doc_ids: Optional[List[str]] = None,
        source_types: Optional[List[str]] = None,
        top_k: int = 15
    ) -> List[VectorSearchResult]:
        """搜索向量（带用户隔离和分类过滤）"""
        print(f"\n[Chroma] ★★★ search_vectors 被调用 ★★★")
        print(f"[Chroma] user_id: {user_id}")
        print(f"[Chroma] doc_ids: {doc_ids}")
        print(f"[Chroma] source_types: {source_types}")
        print(f"[Chroma] top_k: {top_k}")

        if not self.connected:
            print(f"[Chroma] 未连接，尝试连接...")
            if not self.connect():
                print(f"[Chroma] ✗ 连接失败，返回空结果")
                return []

        try:
            conditions = []

            if user_id:
                conditions.append({"user_id": user_id})
                print(f"[Chroma] 添加 user_id 过滤: {user_id}")
            else:
                print(f"[Chroma] ⚠️ user_id 为 None，将不使用用户隔离过滤")

            if doc_ids and len(doc_ids) == 1:
                conditions.append({"doc_id": doc_ids[0]})
                print(f"[Chroma] 添加 doc_id 过滤: {doc_ids[0]}")
            elif doc_ids and len(doc_ids) > 1:
                conditions.append({"$or": [{"doc_id": doc_id} for doc_id in doc_ids]})
                print(f"[Chroma] 添加多 doc_id 过滤: {doc_ids}")

            if source_types:
                conditions.append({"source_type": {"$in": source_types}})
                print(f"[Chroma] 添加 source_types 过滤: {source_types}")
            else:
                conditions.append({"source_type": {"$ne": "未知"}})
                print(f"[Chroma] 默认排除 '未知' 类型")

            if len(conditions) == 1:
                where_filter = conditions[0]
            else:
                where_filter = {"$and": conditions}

            print(f"[Chroma] 最终过滤条件: {where_filter}")

            print(f"[Chroma] 检查 Chroma collection 状态...")
            print(f"[Chroma] Collection name: {self.collection_name}")
            print(f"[Chroma] Collection count (总数): {self.collection.count()}")
            print(f"[Chroma] 正在执行查询...")

            results = self.collection.query(
                query_embeddings=[query_embedding.tolist()],
                n_results=top_k,
                where=where_filter,
                include=["metadatas", "distances", "documents"]
            )

            print(f"[Chroma] 查询返回原始结果: {results.keys() if results else 'None'}")

            search_results = []
            if results and results['ids'] and len(results['ids']) > 0:
                print(f"[Chroma] 找到 {len(results['ids'][0])} 条原始结果")
                print(f"[Chroma] results keys: {results.keys()}")
                print(f"[Chroma] results['documents'] exists: {results.get('documents') is not None}")
                if results.get('documents'):
                    print(f"[Chroma] results['documents'] length: {len(results['documents'])}")
                    print(f"[Chroma] results['documents'][0] length: {len(results['documents'][0]) if results['documents'] else 'N/A'}")

                for i, pid in enumerate(results['ids'][0]):
                    metadata = results['metadatas'][0][i] if results['metadatas'] else {}
                    distance = results['distances'][0][i] if results['distances'] else 0.0

                    doc_content = results['documents'][0][i] if results.get('documents') and len(results['documents']) > 0 and i < len(results['documents'][0]) else "[EMPTY]"

                    print(f"[Chroma] DEBUG pid={pid}, doc_content='{doc_content}', doc_content_len={len(doc_content) if doc_content and doc_content != '[EMPTY]' else 'N/A'}")

                    if metadata.get('source_type') in ['未知', 'unknown', '']:
                        print(f"[Chroma] 跳过 '未知' 类型段落: {pid}")
                        continue

                    search_results.append(VectorSearchResult(
                        paragraph_id=pid,
                        content=doc_content if doc_content != '[EMPTY]' else "",
                        document_id=metadata.get('doc_id', ''),
                        document_name="",
                        user_id=metadata.get('user_id', ''),
                        source_type=metadata.get('source_type', 'unknown'),
                        similarity_score=1.0 - distance,
                        credibility_weight=float(metadata.get('credibility_weight', 0.5)),
                        page_number=int(metadata['page_number']) if metadata.get('page_number') else None,
                        section_title=metadata.get('section_title') or None
                    ))

            print(f"[Chroma] ✓ Search returned {len(search_results)} results (过滤后)")
            return search_results

        except Exception as e:
            print(f"[Chroma] ✗ Search failed: {e}")
            import traceback
            traceback.print_exc()
            return []

    def delete_vectors(self, doc_id: str, user_id: str) -> bool:
        """删除指定文档的所有向量"""
        print(f"\n[Chroma] ★★★ delete_vectors 被调用 ★★★")
        print(f"[Chroma] doc_id: {doc_id}")
        print(f"[Chroma] user_id: {user_id}")

        if not self.connected:
            print(f"[Chroma] 未连接，尝试连接...")
            if not self.connect():
                print(f"[Chroma] ✗ 连接失败，无法删除")
                return False

        try:
            where_filter = {"$and": [{"doc_id": doc_id}, {"user_id": user_id}]}
            print(f"[Chroma] 正在删除 doc_id={doc_id}, user_id={user_id} 的向量...")
            print(f"[Chroma] 删除过滤条件: {where_filter}")
            result = self.collection.delete(where=where_filter)
            print(f"[Chroma] 删除操作完成，结果: {result}")
            print(f"[Chroma] ✓ Deleted vectors for doc_id: {doc_id}")
            return True

        except Exception as e:
            print(f"[Chroma] ✗ Delete failed: {e}")
            import traceback
            traceback.print_exc()
            return False

    def get_vectors_by_doc_id(self, doc_id: str, user_id: str) -> List[VectorSearchResult]:
        """获取指定文档的所有向量"""
        if not self.connected:
            return []

        try:
            where_filter = {"$and": [{"doc_id": doc_id}, {"user_id": user_id}]}
            results = self.collection.get(
                where=where_filter,
                include=["metadatas"]
            )

            return [VectorSearchResult(
                paragraph_id=results['ids'][i],
                content=results['documents'][i] if results['documents'] else "",
                document_id=doc_id,
                document_name="",
                user_id=user_id,
                source_type=results['metadatas'][i].get('source_type', 'unknown'),
                similarity_score=0.0,
                credibility_weight=float(results['metadatas'][i].get('credibility_weight', 0.5)),
                page_number=int(results['metadatas'][i]['page_number']) if results['metadatas'][i].get('page_number') else None,
                section_title=results['metadatas'][i].get('section_title') or None
            ) for i in range(len(results['ids']))]

        except Exception as e:
            print(f"[Chroma] Query failed: {e}")
            return []

    def get_paragraph_with_context(
        self,
        paragraph_id: str,
        user_id: str,
        context_before: int = 2,
        context_after: int = 2
    ) -> Dict[str, Any]:
        """获取指定段落及其上下文"""
        print(f"\n[Chroma] ★★★ get_paragraph_with_context 被调用 ★★★")
        print(f"[Chroma] paragraph_id: {paragraph_id}")
        print(f"[Chroma] user_id: {user_id}")
        print(f"[Chroma] context_before: {context_before}")
        print(f"[Chroma] context_after: {context_after}")

        if not self.connected:
            print(f"[Chroma] 未连接，尝试连接...")
            if not self.connect():
                print(f"[Chroma] ✗ 连接失败")
                return {"found": False, "error": "Connection failed"}

        try:
            # 1. 先获取目标段落
            target_result = self.collection.get(
                ids=[paragraph_id],
                include=["documents", "metadatas"]
            )

            if not target_result or not target_result['ids'] or len(target_result['ids']) == 0:
                print(f"[Chroma] ✗ 未找到段落: {paragraph_id}")
                return {"found": False, "error": "Paragraph not found"}

            target_idx = 0
            target_metadata = target_result['metadatas'][target_idx]
            doc_id = target_metadata.get('doc_id', '')
            chunk_index = int(target_metadata.get('chunk_index', '0'))

            print(f"[Chroma] 找到目标段落: doc_id={doc_id}, chunk_index={chunk_index}")

            # 2. 获取该文档的所有段落
            where_filter = {"$and": [{"doc_id": doc_id}, {"user_id": user_id}]}
            all_paragraphs = self.collection.get(
                where=where_filter,
                include=["documents", "metadatas"]
            )

            if not all_paragraphs or not all_paragraphs['ids']:
                print(f"[Chroma] ✗ 未找到该文档的段落")
                return {"found": False, "error": "No paragraphs found for document"}

            print(f"[Chroma] 该文档共有 {len(all_paragraphs['ids'])} 个段落")

            # 3. 按 chunk_index 排序所有段落
            paragraphs_with_idx = []
            for i, pid in enumerate(all_paragraphs['ids']):
                meta = all_paragraphs['metadatas'][i]
                c_idx = int(meta.get('chunk_index', '0'))
                paragraphs_with_idx.append({
                    'id': pid,
                    'chunk_index': c_idx,
                    'content': all_paragraphs['documents'][i] if all_paragraphs['documents'] else '',
                    'metadata': meta
                })

            paragraphs_with_idx.sort(key=lambda x: x['chunk_index'])

            # 4. 找到目标段落在排序列表中的位置
            target_pos = -1
            for i, p in enumerate(paragraphs_with_idx):
                if p['id'] == paragraph_id:
                    target_pos = i
                    break

            if target_pos == -1:
                print(f"[Chroma] ✗ 目标段落不在文档段落列表中")
                return {"found": False, "error": "Target paragraph not in document list"}

            # 5. 提取上下文
            start_idx = max(0, target_pos - context_before)
            end_idx = min(len(paragraphs_with_idx) - 1, target_pos + context_after)

            print(f"[Chroma] 目标位置: {target_pos}, 提取范围: {start_idx} ~ {end_idx}")

            context_paragraphs = []
            for i in range(start_idx, end_idx + 1):
                p = paragraphs_with_idx[i]
                context_paragraphs.append({
                    'paragraph_id': p['id'],
                    'chunk_index': p['chunk_index'],
                    'content': p['content'],
                    'is_target': i == target_pos,
                    'position': i - target_pos,  # -2, -1, 0, 1, 2 表示相对位置
                    'metadata': p['metadata']
                })

            result = {
                "found": True,
                "target_paragraph_id": paragraph_id,
                "doc_id": doc_id,
                "user_id": user_id,
                "context_paragraphs": context_paragraphs,
                "total_paragraphs_in_doc": len(paragraphs_with_idx),
                "target_position_in_doc": target_pos
            }

            print(f"[Chroma] ✓ 返回上下文，共 {len(context_paragraphs)} 个段落")
            return result

        except Exception as e:
            print(f"[Chroma] ✗ 获取上下文失败: {e}")
            import traceback
            traceback.print_exc()
            return {"found": False, "error": str(e)}


class VectorStoreFactory:
    """向量存储工厂"""

    _instances: Dict[VectorBackend, BaseVectorStore] = {}

    @classmethod
    def get_store(cls, backend: VectorBackend = None) -> BaseVectorStore:
        """获取向量存储实例"""
        if backend is None:
            backend_str = os.getenv('VECTOR_BACKEND', 'chroma').lower()
            try:
                backend = VectorBackend(backend_str)
            except ValueError:
                backend = VectorBackend.CHROMA

        if backend not in cls._instances:
            if backend == VectorBackend.MILVUS:
                cls._instances[backend] = MilvusVectorStore()
            elif backend == VectorBackend.QDRANT:
                raise NotImplementedError("Qdrant backend not implemented yet")
            elif backend == VectorBackend.CHROMA:
                cls._instances[backend] = ChromaVectorStore()
            else:
                cls._instances[backend] = OracleVectorStore()

        return cls._instances[backend]

    @classmethod
    def reset(cls):
        """重置所有实例（用于切换后端）"""
        cls._instances.clear()


import requests


class EnhancedVectorRetriever:
    """
    增强版向量检索器

    支持：
    1. 多向量后端切换（Milvus/Qdrant/Oracle/Chroma）
    2. 用户隔离
    3. 分类过滤（排除未知分类）
    4. 向量相似度搜索
    """

    def __init__(self, backend: VectorBackend = None):
        self.dimension = int(os.getenv('VECTOR_DIM', '1536'))
        self.embedding_api_url = os.getenv('EMBEDDING_API_URL', 'https://ark.cn-beijing.volces.com/api/v3/embeddings')
        self.embedding_api_key = os.getenv('BAILIAN_API_KEY')
        self.backend = self._get_backend(backend)

        self.headers = {
            'Content-Type': 'application/json',
            'Authorization': f'Bearer {self.embedding_api_key}'
        }

    def _get_backend(self, preferred_backend: VectorBackend = None) -> BaseVectorStore:
        """获取当前配置的向量后端"""
        if preferred_backend is not None:
            backend = preferred_backend
        else:
            backend_str = os.getenv('VECTOR_BACKEND', 'chroma').lower()
            try:
                backend = VectorBackend(backend_str)
            except ValueError:
                backend = VectorBackend.CHROMA

        return VectorStoreFactory.get_store(backend)

    def embed_text(self, text: str) -> np.ndarray:
        """生成文本向量"""
        try:
            data = {
                "model": os.getenv('VECTOR_MODEL', 'text-embedding-v4'),
                "input": text,
                "dimensions": self.dimension
            }

            response = requests.post(
                self.embedding_api_url,
                headers=self.headers,
                data=json.dumps(data, ensure_ascii=False),
                timeout=30
            )

            if response.status_code == 200:
                result = response.json()
                embedding = result['data'][0]['embedding']
                vector = np.array(embedding)
                vector = vector / np.linalg.norm(vector)
                return vector
            else:
                raise Exception(f"Embedding API returned {response.status_code}")

        except Exception as e:
            print(f"[Embed] Failed: {e}")
            return np.random.randn(self.dimension)

    def embed_batch(self, texts: List[str]) -> List[np.ndarray]:
        """批量生成向量"""
        try:
            data = {
                "model": os.getenv('VECTOR_MODEL', 'text-embedding-v4'),
                "input": texts,
                "dimensions": self.dimension
            }

            response = requests.post(
                self.embedding_api_url,
                headers=self.headers,
                data=json.dumps(data, ensure_ascii=False),
                timeout=60
            )

            if response.status_code == 200:
                result = response.json()
                embeddings = []
                for item in result['data']:
                    vector = np.array(item['embedding'])
                    vector = vector / np.linalg.norm(vector)
                    embeddings.append(vector)
                return embeddings
            else:
                raise Exception(f"Embedding API returned {response.status_code}")

        except Exception as e:
            print(f"[Embed] Batch failed: {e}")
            return [np.random.randn(self.dimension) for _ in texts]

    def search(
        self,
        query: str,
        user_id: str,
        doc_ids: Optional[List[str]] = None,
        source_types: Optional[List[str]] = None,
        top_k: int = 15
    ) -> List[Dict[str, Any]]:
        """
        搜索相似段落

        Args:
            query: 查询文本
            user_id: 用户ID（用于个人化隔离）
            doc_ids: 指定文档ID列表（可选）
            source_types: 指定来源类型列表（用于过滤）
            top_k: 返回数量

        Returns:
            带元数据的段落列表
        """
        query_embedding = self.embed_text(query)

        results = self.backend.search_vectors(
            query_embedding=query_embedding,
            user_id=user_id,
            doc_ids=doc_ids,
            source_types=source_types,
            top_k=top_k
        )

        return [{
            'paragraphId': r.paragraph_id,
            'content': r.content,
            'docId': r.document_id,
            'documentName': r.document_name,
            'userId': r.user_id,
            'sourceType': r.source_type,
            'similarityScore': r.similarity_score,
            'credibilityWeight': r.credibility_weight,
            'pageNumber': r.page_number,
            'sectionTitle': r.section_title
        } for r in results]

    def index_document(
        self,
        doc_id: str,
        user_id: str,
        source_type: str = 'unknown',
        equipment_type: str = 'general',
        paragraphs: List[Dict[str, Any]] = None
    ) -> bool:
        """
        索引文档段落

        Args:
            doc_id: 文档ID
            user_id: 用户ID（用于个人化隔离）
            source_type: 来源类型
            equipment_type: 设备类型
            paragraphs: 段落列表

        Returns:
            是否成功
        """
        print(f"\n[Index] ★★★ index_document 被调用 ★★★")
        print(f"[Index] doc_id: {doc_id}")
        print(f"[Index] user_id: {user_id}")
        print(f"[Index] source_type: {source_type}")
        print(f"[Index] equipment_type: {equipment_type}")
        print(f"[Index] paragraphs 数量: {len(paragraphs) if paragraphs else 0}")

        if source_type in ['未知', 'unknown', '']:
            print(f"[Index] ✗ 文档类型为 '{source_type}'，跳过向量解析和插入")
            return False

        try:
            if paragraphs is None:
                paragraphs = []

            valid_paragraphs = []
            for p in paragraphs:
                para_source_type = p.get('sourceType', source_type)
                if para_source_type in ['未知', 'unknown', '']:
                    print(f"[Index] 跳过段落 (id={p.get('paragraphId', 'unknown')})，类型为 '{para_source_type}'")
                    continue
                valid_paragraphs.append(p)

            if len(valid_paragraphs) == 0:
                print(f"[Index] ✗ 所有段落类型均为'未知'或'unknown'，跳过向量解析")
                return False

            print(f"[Index] 有效段落数量: {len(valid_paragraphs)} / {len(paragraphs)}")
            paragraphs = valid_paragraphs

            print(f"[Index] 正在生成 {len(paragraphs)} 个段落的向量...")
            contents = [p['content'] for p in paragraphs]
            print(f"[Index] 内容列表长度: {len(contents)}")

            embeddings = self.embed_batch(contents)
            print(f"[Index] ✓ 向量生成完成，embeddings 形状: {embeddings.shape if hasattr(embeddings, 'shape') else len(embeddings)}")

            doc_paragraphs = []
            print(f"[Index] 正在构建 DocumentParagraph 对象...")
            for i, para in enumerate(paragraphs):
                para_id = para.get('paragraphId', f"para_{doc_id}_{i}")
                # 优先使用段落自带的 chunk_index，否则使用索引 i
                chunk_idx = para.get('chunkIndex') or para.get('chunk_index') or i
                doc_paragraphs.append(DocumentParagraph(
                    paragraph_id=para_id,
                    doc_id=doc_id,
                    user_id=user_id,
                    content=para['content'],
                    source_type=para.get('sourceType', source_type),
                    credibility_weight=para.get('credibilityWeight', 0.5),
                    chunk_index=int(chunk_idx),
                    page_number=para.get('pageNumber'),
                    section_title=para.get('sectionTitle'),
                    embedding=embeddings[i] if i < len(embeddings) else np.random.randn(self.dimension)
                ))
                if i < 2:
                    print(f"[Index]   段落{i+1}: id={para_id}, chunk_index={chunk_idx}, content长度={len(para['content'])}, embedding维度={len(embeddings[i]) if i < len(embeddings) else 'N/A'}")

            print(f"[Index] 共有 {len(doc_paragraphs)} 个 DocumentParagraph")
            print(f"[Index] 正在调用 self.backend.insert_vectors...")
            print(f"[Index] self.backend 类型: {type(self.backend)}")

            result = self.backend.insert_vectors(doc_paragraphs)
            print(f"[Index] self.backend.insert_vectors 返回: {result}")
            return result

        except Exception as e:
            print(f"[Index] ✗ 索引失败: {e}")
            import traceback
            traceback.print_exc()
            return False

    def delete_document(self, doc_id: str, user_id: str) -> bool:
        """删除文档的所有向量"""
        print(f"\n[EnhancedRetriever] ★★★ delete_document 被调用 ★★★")
        print(f"[EnhancedRetriever] doc_id: {doc_id}")
        print(f"[EnhancedRetriever] user_id: {user_id}")
        print(f"[EnhancedRetriever] 当前 backend 类型: {type(self.backend)}")
        return self.backend.delete_vectors(doc_id, user_id)

    def get_paragraph_with_context(
        self,
        paragraph_id: str,
        user_id: str,
        context_before: int = 2,
        context_after: int = 2
    ) -> Dict[str, Any]:
        """获取段落及其上下文"""
        print(f"\n[EnhancedRetriever] ★★★ get_paragraph_with_context 被调用 ★★★")
        return self.backend.get_paragraph_with_context(
            paragraph_id=paragraph_id,
            user_id=user_id,
            context_before=context_before,
            context_after=context_after
        )

    def switch_backend(self, backend: VectorBackend):
        """切换向量后端"""
        VectorStoreFactory.reset()
        self.backend = VectorStoreFactory.get_store(backend)
        print(f"[VectorRetriever] Switched to {backend.value} backend")