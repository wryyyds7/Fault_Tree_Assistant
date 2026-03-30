# Document Metadata
# 文档元数据管理，为段落添加来源标记和可信度权重

from enum import Enum
from dataclasses import dataclass, field
from typing import Optional, Dict, Any
from datetime import datetime

class SourceType(Enum):
    """文档来源类型"""
    EQUIPMENT_MANUAL = "equipment_manual"      # 设备手册
    MAINTENANCE_RECORD = "maintenance_record" # 维修记录
    INDUSTRY_STANDARD = "industry_standard"    # 行业标准
    THEORY_PAPER = "theory_paper"             # 理论文献
    USER_FEEDBACK = "user_feedback"           # 用户反馈
    UNKNOWN = "unknown"                        # 未知来源

@dataclass
class DocumentMetadata:
    """文档元数据"""
    doc_id: str
    source_type: SourceType
    document_name: str
    page_number: Optional[int] = None
    section_title: Optional[str] = None
    created_at: datetime = field(default_factory=datetime.now)
    credibility_weight: float = 1.0
    additional_info: Dict[str, Any] = field(default_factory=dict)

    def get_credibility_weight(self) -> float:
        """
        获取可信度权重

        根据来源类型返回默认权重：
        - 行业标准：1.2（最高权威）
        - 设备手册：1.0（权威来源）
        - 维修记录：0.8（实践经验）
        - 理论文献：0.9（理论支撑）
        - 用户反馈：0.6（主观性强）
        - 未知来源：0.5（最低信任）

        返回:
            可信度权重
        """
        if self.credibility_weight != 1.0:
            return self.credibility_weight

        weight_map = {
            SourceType.INDUSTRY_STANDARD: 1.2,
            SourceType.EQUIPMENT_MANUAL: 1.0,
            SourceType.MAINTENANCE_RECORD: 0.8,
            SourceType.THEORY_PAPER: 0.9,
            SourceType.USER_FEEDBACK: 0.6,
            SourceType.UNKNOWN: 0.5
        }

        return weight_map.get(self.source_type, 0.5)

    def to_dict(self) -> Dict[str, Any]:
        """转换为字典"""
        return {
            'doc_id': self.doc_id,
            'source_type': self.source_type.value,
            'document_name': self.document_name,
            'page_number': self.page_number,
            'section_title': self.section_title,
            'created_at': self.created_at.isoformat(),
            'credibility_weight': self.get_credibility_weight(),
            'additional_info': self.additional_info
        }

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> 'DocumentMetadata':
        """从字典创建"""
        source_type = SourceType(data.get('source_type', 'unknown'))
        return cls(
            doc_id=data['doc_id'],
            source_type=source_type,
            document_name=data.get('document_name', ''),
            page_number=data.get('page_number'),
            section_title=data.get('section_title'),
            created_at=datetime.fromisoformat(data['created_at']) if data.get('created_at') else datetime.now(),
            credibility_weight=data.get('credibility_weight', 1.0),
            additional_info=data.get('additional_info', {})
        )

@dataclass
class ParagraphWithMetadata:
    """带元数据的段落"""
    paragraph_id: str
    content: str
    metadata: DocumentMetadata
    vector_embedding: Optional[Any] = None

    def get_weighted_content(self) -> str:
        """
        获取加权后的内容

        返回:
            内容（包含来源标记）
        """
        weight = self.metadata.get_credibility_weight()
        source_name = self.metadata.source_type.value
        return f"[来源：{source_name}，权重：{weight}] {self.content}"

    def to_dict(self) -> Dict[str, Any]:
        """转换为字典"""
        return {
            'paragraph_id': self.paragraph_id,
            'content': self.content,
            'metadata': self.metadata.to_dict()
        }
