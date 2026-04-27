import os
from typing import List, Dict, Any, Optional
from dotenv import load_dotenv

load_dotenv('.env')

from langchain_text_splitters import RecursiveCharacterTextSplitter

class SemanticChunker:
    """
    基于LangChain的语义分块器

    使用RecursiveCharacterTextSplitter进行语义分块：
    - 按段落、句子、单词递归分割
    - 支持滑动窗口（overlap）保持上下文关联
    - 可配置块大小和重叠长度
    """

    def __init__(
        self,
        chunk_size: int = 1500,
        chunk_overlap: int = 300,
        separators: Optional[List[str]] = None
    ):
        """
        初始化语义分块器

        Args:
            chunk_size: 每个块的最大字符数
            chunk_overlap: 相邻块之间的重叠字符数
            separators: 自定义分隔符列表，按优先级排序
        """
        if separators is None:
            separators = ["\n\n", "。", "！", "？", "；",  ""]

        self.text_splitter = RecursiveCharacterTextSplitter(
            chunk_size=chunk_size,
            chunk_overlap=chunk_overlap,
            length_function=len,
            separators=separators,
            is_separator_regex=False
        )

    def split_text(self, text: str) -> List[str]:
        """
        将文本分割成多个语义块

        Args:
            text: 输入文本

        Returns:
            分割后的文本块列表
        """
        if not text or not text.strip():
            return []

        chunks = self.text_splitter.split_text(text)
        return [chunk for chunk in chunks if chunk.strip()]

    def split_documents(
        self,
        texts: List[str],
        doc_id: Optional[str] = None,
        user_id: Optional[str] = None,
        source_type: str = "unknown",
        metadata: Optional[Dict[str, Any]] = None
    ) -> List[Dict[str, Any]]:
        """
        将多个文档分割成语义块，并添加元数据

        Args:
            texts: 文档文本列表
            doc_id: 文档ID
            user_id: 用户ID
            source_type: 来源类型
            metadata: 额外的元数据

        Returns:
            带元数据的段落列表
        """
        paragraphs = []

        for doc_index, text in enumerate(texts):
            if not text or not text.strip():
                continue

            chunks = self.split_text(text)

            for chunk_index, chunk in enumerate(chunks):
                para = {
                    'content': chunk,
                    'chunk_index': chunk_index,
                    'doc_index': doc_index,
                    'source_type': source_type
                }

                if doc_id:
                    para['doc_id'] = doc_id
                if user_id:
                    para['user_id'] = user_id
                if metadata:
                    para.update(metadata)

                paragraphs.append(para)

        return paragraphs

    def split_document_by_paragraphs(
        self,
        paragraphs: List[Dict[str, Any]],
        doc_id: Optional[str] = None,
        user_id: Optional[str] = None
    ) -> List[Dict[str, Any]]:
        """
        将已分段的文档（来自Java后端）重新进行语义分块

        Args:
            paragraphs: Java后端返回的段落列表，每项包含content、pageNumber等
            doc_id: 文档ID
            user_id: 用户ID

        Returns:
            重新分块后的段落列表
        """
        result_paragraphs = []

        existing_doc_id = None
        existing_user_id = None
        existing_source_type = None

        if paragraphs and len(paragraphs) > 0:
            existing_doc_id = paragraphs[0].get('docId') or paragraphs[0].get('doc_id')
            existing_user_id = paragraphs[0].get('userId') or paragraphs[0].get('user_id')
            existing_source_type = paragraphs[0].get('sourceType') or paragraphs[0].get('source_type', 'unknown')

        combined_text = "\n\n".join([
            p.get('content', '') for p in paragraphs if p.get('content')
        ])

        chunks = self.split_text(combined_text)

        for i, chunk in enumerate(chunks):
            para = {
                'content': chunk,
                'chunk_index': i,
                'source_type': existing_source_type or 'unknown'
            }

            final_doc_id = doc_id or existing_doc_id
            final_user_id = user_id or existing_user_id

            if final_doc_id:
                para['doc_id'] = final_doc_id
            if final_user_id:
                para['user_id'] = final_user_id

            result_paragraphs.append(para)

        return result_paragraphs


def create_semantic_chunker(
    chunk_size: Optional[int] = None,
    chunk_overlap: Optional[int] = None
) -> SemanticChunker:
    """
    创建语义分块器的工厂函数

    Args:
        chunk_size: 块大小，默认从环境变量CHUNK_SIZE或500
        chunk_overlap: 重叠大小，默认从环境变量CHUNK_OVERLAP或100

    Returns:
        SemanticChunker实例
    """
    if chunk_size is None:
        chunk_size = int(os.getenv('CHUNK_SIZE', '500'))

    if chunk_overlap is None:
        chunk_overlap = int(os.getenv('CHUNK_OVERLAP', '100'))

    return SemanticChunker(
        chunk_size=chunk_size,
        chunk_overlap=chunk_overlap
    )


if __name__ == "__main__":
    chunker = create_semantic_chunker(chunk_size=300, chunk_overlap=50)

    test_text = """
    故障代码F01005：编码器模块固件下载失败。

    故障值为十六进制格式，yyxxxx格式中yy为组件编号，xxxx为故障原因。当出现此故障时，需要检查编码器模块的固件状态。

    故障原因分析：
    1. 固件文件损坏或缺失
    2. 下载过程中通讯中断
    3. 编码器模块硬件故障

    处理方法：
    1. 更换存储卡，重新获取固件文件
    2. 检查通讯速度和负载
    3. 如有必要，更换编码器模块
    """

    print("=" * 60)
    print("[SemanticChunker] Test")
    print("=" * 60)
    print(f"Original text length: {len(test_text)}")
    print()

    chunks = chunker.split_text(test_text)

    print(f"Split into {len(chunks)} chunks:")
    for i, chunk in enumerate(chunks):
        print(f"\n--- Chunk {i} (len={len(chunk)}) ---")
        print(chunk[:150] + "..." if len(chunk) > 150 else chunk)