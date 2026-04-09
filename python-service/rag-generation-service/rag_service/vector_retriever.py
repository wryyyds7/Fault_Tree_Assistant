import json
import os
import requests
import numpy as np
from typing import List, Dict, Any, Optional

class VectorRetriever:
    """
    向量检索器 - 使用千问text-embedding-v4云端向量服务

    数据流程：
    1. 调用千问embedding API将文本转为向量
    2. 调用Java vector-store-service进行向量相似度搜索
    3. 返回带元数据的检索结果
    """

    def __init__(self):
        self.vector_service_url = os.getenv('VECTOR_SERVICE_URL', 'http://localhost:8084')
        self.embedding_model = os.getenv('VECTOR_MODEL', 'text-embedding-v4')
        self.vector_dim = int(os.getenv('VECTOR_DIM', '1536'))

        self.embedding_api_url = os.getenv('EMBEDDING_API_URL', 'https://ark.cn-beijing.volces.com/api/v3/embeddings')
        self.embedding_api_key = os.getenv('BAILIAN_API_KEY')

        self.headers = {
            'Content-Type': 'application/json',
            'Authorization': f'Bearer {self.embedding_api_key}'
        }

        self.index = None
        self.paragraphs = []

    def embed_text(self, text: str) -> np.ndarray:
        """
        使用千问text-embedding-v4 API生成文本向量

        Args:
            text: 输入文本

        Returns:
            归一化的向量表示
        """
        try:
            data = {
                "model": self.embedding_model,
                "input": text
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
                raise Exception(f"Embedding API returned {response.status_code}: {response.text}")

        except Exception as e:
            raise Exception(f"Failed to generate embedding: {e}")

    def embed_batch(self, texts: List[str]) -> List[np.ndarray]:
        """
        批量生成文本向量

        Args:
            texts: 输入文本列表

        Returns:
            归一化的向量列表
        """
        try:
            data = {
                "model": self.embedding_model,
                "input": texts
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
                raise Exception(f"Embedding API returned {response.status_code}: {response.text}")

        except Exception as e:
            raise Exception(f"Failed to generate batch embeddings: {e}")

    def search(self, query: str, doc_ids: List[str], top_k: int = 15) -> List[Dict[str, Any]]:
        """
        从向量库检索与查询相关的段落

        Args:
            query: 查询文本（顶事件）
            doc_ids: 文档ID列表
            top_k: 返回结果数量

        Returns:
            带元数据的段落列表，按相似度排序
        """
        results = []

        try:
            query_embedding = self.embed_text(query)
            embedding_list = query_embedding.tolist()

            search_url = f"{self.vector_service_url}/api/v1/vector/search"
            response = requests.post(
                search_url,
                json={
                    "query": query,
                    "queryEmbedding": embedding_list,
                    "docIds": doc_ids,
                    "topK": top_k,
                    "embeddingModel": self.embedding_model
                },
                timeout=30
            )

            if response.status_code == 200:
                data = response.json()
                results = data.get('results', [])
                print(f"Retrieved {len(results)} paragraphs from vector-store-service")
                return results

        except Exception as e:
            print(f"Vector search failed: {e}")

        try:
            results = self._search_from_mysql(query, doc_ids, top_k)
        except Exception as e:
            print(f"MySQL vector search failed: {e}")

        return results

    def _search_from_mysql(self, query: str, doc_ids: List[str], top_k: int) -> List[Dict[str, Any]]:
        """
        直接从MySQL获取向量数据进行搜索
        """
        results = []

        try:
            query_embedding = self.embed_text(query)

            all_vectors = []
            for doc_id in doc_ids:
                vectors = self._get_vectors_by_doc_id(doc_id)
                all_vectors.extend(vectors)

            if not all_vectors:
                print(f"No vectors found for doc_ids: {doc_ids}")
                return []

            scored_results = []
            for item in all_vectors:
                try:
                    vector_data = json.loads(item['vector_data'])
                    stored_embedding = np.array(vector_data)

                    similarity = np.dot(query_embedding, stored_embedding)

                    scored_results.append({
                        'paragraphId': item['paragraph_id'],
                        'content': item['content'],
                        'documentName': item['document_name'],
                        'pageNumber': item.get('page_number'),
                        'sectionTitle': item.get('section_title'),
                        'similarityScore': float(similarity),
                        'sourceType': item.get('source_type', 'unknown'),
                        'credibilityWeight': item.get('credibility_weight', 0.5)
                    })
                except Exception as e:
                    print(f"Error processing vector: {e}")
                    continue

            scored_results.sort(key=lambda x: x['similarityScore'], reverse=True)
            results = scored_results[:top_k]

        except Exception as e:
            print(f"Error in _search_from_mysql: {e}")

        return results

    def _get_vectors_by_doc_id(self, doc_id: str) -> List[Dict[str, Any]]:
        """
        从MySQL vector_store表获取指定文档的所有向量数据
        """
        try:
            url = f"{self.vector_service_url}/api/v1/vector/documents/{doc_id}/paragraphs"
            response = requests.get(url, timeout=10)

            if response.status_code == 200:
                return response.json()

        except Exception as e:
            print(f"Failed to get vectors for doc_id {doc_id}: {e}")

        return []

    def retrieve_relevant_paragraphs(self, query: str, documents: List[str], top_k: int = 5) -> List[Dict[str, Any]]:
        """
        检索与查询相关的段落（旧API兼容）
        """
        results = []

        for doc_content in documents:
            try:
                search_url = f"{self.vector_service_url}/api/v1/vector/search"
                response = requests.post(
                    search_url,
                    json={
                        "query": query,
                        "topK": top_k
                    },
                    timeout=10
                )

                if response.status_code == 200:
                    data = response.json()
                    results.extend(data.get('results', []))

            except Exception as e:
                print(f"Failed to retrieve paragraphs: {e}")
                continue

        return results[:top_k]

    def index_document(self, doc_id: str, paragraphs: List[Dict[str, Any]]) -> bool:
        """
        将文档段落索引到向量库

        Args:
            doc_id: 文档ID
            paragraphs: 段落列表，每项包含 content, metadata 等

        Returns:
            是否索引成功
        """
        try:
            contents = [p['content'] for p in paragraphs]
            embeddings = self.embed_batch(contents)

            for i, para in enumerate(paragraphs):
                para['embedding'] = embeddings[i].tolist()

            url = f"{self.vector_service_url}/api/v1/vector/index"
            response = requests.post(
                url,
                json={
                    "docId": doc_id,
                    "paragraphs": paragraphs,
                    "embeddingModel": self.embedding_model
                },
                timeout=30
            )

            return response.status_code == 200

        except Exception as e:
            print(f"Failed to index document: {e}")
            return False
