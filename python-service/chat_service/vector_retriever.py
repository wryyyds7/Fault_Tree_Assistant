# 简化的向量检索器
import os
import requests
from dotenv import load_dotenv

load_dotenv()

class VectorRetriever:
    def __init__(self):
        self.api_key = os.getenv('BAILIAN_API_KEY', 'sk-667d6c0a4b134de1ba04a8b86c98a3a3')
        self.api_url = os.getenv('EMBEDDING_API_URL', 'https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings')
        self.model = os.getenv('VECTOR_MODEL', 'text-embedding-v4')
        self.dimension = int(os.getenv('VECTOR_DIM', '1536'))

    def get_embedding(self, text):
        """获取文本的向量表示"""
        headers = {
            'Authorization': f'Bearer {self.api_key}',
            'Content-Type': 'application/json'
        }

        payload = {
            'model': self.model,
            'input': text,
            'dimensions': self.dimension
        }

        try:
            response = requests.post(
                self.api_url,
                headers=headers,
                json=payload,
                timeout=30
            )
            response.raise_for_status()
            result = response.json()

            if 'data' in result and len(result['data']) > 0:
                return result['data'][0]['embedding']
            return None

        except Exception as e:
            print(f"Error getting embedding: {e}")
            return None

    def search(self, query, top_k=5):
        """搜索最相关的文档"""
        return []
