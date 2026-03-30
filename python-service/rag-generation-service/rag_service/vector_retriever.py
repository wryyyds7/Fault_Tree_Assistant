import torch
from transformers import AutoTokenizer, AutoModel
import faiss
import numpy as np

class VectorRetriever:
    def __init__(self):
        # 加载BGE-M3模型
        self.model_name = "BAAI/bge-m3"
        self.tokenizer = AutoTokenizer.from_pretrained(self.model_name)
        self.model = AutoModel.from_pretrained(self.model_name)
        self.vector_dim = 768
        self.index = None
        self.documents = []
    
    def embed_text(self, text):
        """生成文本的向量表示"""
        inputs = self.tokenizer(text, return_tensors="pt", padding=True, truncation=True, max_length=512)
        with torch.no_grad():
            outputs = self.model(**inputs)
        # 使用[CLS] token的嵌入作为文本表示
        embeddings = outputs.last_hidden_state[:, 0, :].numpy()
        # 归一化
        embeddings = embeddings / np.linalg.norm(embeddings, axis=1, keepdims=True)
        return embeddings[0]
    
    def build_index(self, documents):
        """构建向量索引"""
        self.documents = documents
        # 生成所有文档的向量
        embeddings = []
        for doc in documents:
            embedding = self.embed_text(doc)
            embeddings.append(embedding)
        embeddings = np.array(embeddings)
        # 构建FAISS索引
        self.index = faiss.IndexFlatIP(self.vector_dim)
        self.index.add(embeddings)
    
    def retrieve_relevant_paragraphs(self, query, documents, top_k=5):
        """检索与查询相关的段落"""
        # 如果索引不存在，构建索引
        if self.index is None or len(self.documents) != len(documents):
            self.build_index(documents)
        # 生成查询的向量
        query_embedding = self.embed_text(query)
        query_embedding = np.array([query_embedding])
        # 搜索最相似的文档
        distances, indices = self.index.search(query_embedding, top_k)
        # 获取相关文档
        relevant_paragraphs = []
        for i in indices[0]:
            if i < len(self.documents):
                relevant_paragraphs.append(self.documents[i])
        return relevant_paragraphs


