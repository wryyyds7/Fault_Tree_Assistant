# Document Source Type Classifier
# 文档来源类型自动分类器

from enum import Enum
from typing import Optional, Tuple, Dict, Any
import json
import re

from rag_service.llm_client import LLMClient
from .document_classification import (
    DOCUMENT_CLASSIFICATION_SYSTEM_PROMPT,
    DOCUMENT_CLASSIFICATION_USER_PROMPT,
    FEWSHOT_EXAMPLES
)


class SourceType(Enum):
    """文档来源类型"""
    EQUIPMENT_MANUAL = "equipment_manual"
    MAINTENANCE_RECORD = "maintenance_record"
    INDUSTRY_STANDARD = "industry_standard"
    THEORY_PAPER = "theory_paper"
    USER_FEEDBACK = "user_feedback"
    UNKNOWN = "unknown"

    @classmethod
    def from_string(cls, value: str) -> 'SourceType':
        """从字符串创建枚举"""
        try:
            return cls(value.lower())
        except ValueError:
            return cls.UNKNOWN


class ClassificationResult:
    """分类结果"""

    def __init__(
        self,
        source_type: SourceType,
        confidence: float,
        reasoning: str,
        method: str = "llm"
    ):
        self.source_type = source_type
        self.confidence = confidence
        self.reasoning = reasoning
        self.method = method

    def get_credibility_weight(self) -> float:
        """
        根据来源类型获取可信度权重

        权重映射：
        - 行业标准：1.2（最高权威）
        - 设备手册：1.0（权威来源）
        - 理论文献：0.9（理论支撑）
        - 维修记录：0.8（实践经验）
        - 用户反馈：0.6（主观性强）
        - 未知来源：0.5（最低信任）
        """
        weight_map = {
            SourceType.INDUSTRY_STANDARD: 1.2,
            SourceType.EQUIPMENT_MANUAL: 1.0,
            SourceType.THEORY_PAPER: 0.9,
            SourceType.MAINTENANCE_RECORD: 0.8,
            SourceType.USER_FEEDBACK: 0.6,
            SourceType.UNKNOWN: 0.5
        }
        base_weight = weight_map.get(self.source_type, 0.5)

        if self.method == "llm":
            confidence_factor = 1.0
        else:
            confidence_factor = 0.8

        return base_weight * confidence_factor

    def to_dict(self) -> Dict[str, Any]:
        """转换为字典"""
        return {
            'source_type': self.source_type.value,
            'confidence': self.confidence,
            'reasoning': self.reasoning,
            'method': self.method,
            'credibility_weight': self.get_credibility_weight()
        }


class DocumentClassifier:
    """文档来源类型分类器

    使用 LLM 进行智能分类，支持：
    1. 策略1：文件名特征匹配（快速筛选）
    2. 策略2：内容关键词检测（深度分析）
    3. 策略3：LLM智能判断（最高准确度）
    """

    FILENAME_PATTERNS = {
        SourceType.EQUIPMENT_MANUAL: [
            r'manual', r'handbook', r'手册', r'说明书',
            r'user\s*guide', r'操作手册', r'使用指南', r'技术手册'
        ],
        SourceType.MAINTENANCE_RECORD: [
            r'maintenance', r'repair', r'维修', r'保养',
            r'service\s*record', r'检修', r'故障记录', r'修理'
        ],
        SourceType.INDUSTRY_STANDARD: [
            r'standard', r'specification', r'标准', r'规范',
            r'GB/?T', r'ISO-?\d', r'IEC', r'ANSI', r'API'
        ],
        SourceType.THEORY_PAPER: [
            r'paper', r'thesis', r'dissertation', r'论文',
            r'reference', r'journal', r'学术', r'期刊', r'research'
        ],
        SourceType.USER_FEEDBACK: [
            r'feedback', r'survey', r'反馈', r'调查',
            r'complaint', r'review', r'用户反馈', r'满意度'
        ]
    }

    CONTENT_PATTERNS = {
        SourceType.INDUSTRY_STANDARD: [
            'GB/T', 'ISO', 'IEC', 'ANSI', '行业标准',
            '国家标准', '安全规程', '技术规范', '操作规程',
            'standard specification', 'regulatory'
        ],
        SourceType.EQUIPMENT_MANUAL: [
            '设备型号', '技术参数', '操作说明', '安装指南',
            '技术规格', '产品说明书', '用户手册', '维护保养',
            '额定功率', 'model number'
        ],
        SourceType.MAINTENANCE_RECORD: [
            '故障代码', '维修日期', '故障现象', '处理措施',
            '维修记录', '检修报告', '保养记录', '故障排除',
            'error code', 'fault code', 'maintenance date'
        ],
        SourceType.THEORY_PAPER: [
            '摘要', '参考文献', '理论分析', '实验方法',
            '研究结论', '学术论文', '期刊', 'doi:',
            'abstract', 'introduction', 'methodology'
        ],
        SourceType.USER_FEEDBACK: [
            '用户反馈', '投诉', '建议', '满意度', '使用体验',
            'user feedback', 'complaint', 'satisfaction'
        ]
    }

    def __init__(
        self,
        llm_client: Optional[LLMClient] = None,
        filename_confidence: float = 0.85,
        content_confidence: float = 0.70,
        llm_confidence: float = 0.90
    ):
        """
        初始化分类器

        参数:
            llm_client: LLM客户端实例，如果为None则创建默认实例
            filename_confidence: 文件名匹配策略的置信度
            content_confidence: 内容匹配策略的置信度
            llm_confidence: LLM判断策略的置信度
        """
        self.llm_client = llm_client or LLMClient()
        self.filename_confidence = filename_confidence
        self.content_confidence = content_confidence
        self.llm_confidence = llm_confidence

    def classify(
        self,
        document_name: str,
        content: str,
        use_prematching: bool = True,
        content_preview_length: int = 800
    ) -> ClassificationResult:
        """
        自动分类文档来源类型

        参数:
            document_name: 文档名称/标题
            content: 文档完整内容
            use_prematching: 是否使用预匹配策略（文件名+关键词）
            content_preview_length: 发送给LLM的内容预览长度

        返回:
            ClassificationResult: 分类结果
        """
        if use_prematching:
            result = self._prematch(document_name, content)
            if result:
                return result

        return self._classify_with_llm(
            document_name,
            content[:content_preview_length]
        )

    def _prematch(
        self,
        document_name: str,
        content: str
    ) -> Optional[ClassificationResult]:
        """
        预匹配策略：文件名 + 内容关键词快速筛选

        如果匹配成功，返回结果；否则返回None，继续使用LLM判断
        """
        name_result = self._match_filename(document_name)
        if name_result:
            name_result.method = "filename"
            return name_result

        content_result = self._match_content(content)
        if content_result:
            content_result.method = "content"
            return content_result

        return None

    def _match_filename(self, document_name: str) -> Optional[ClassificationResult]:
        """策略1：文件名特征匹配"""
        name_lower = document_name.lower()

        for source_type, patterns in self.FILENAME_PATTERNS.items():
            for pattern in patterns:
                if re.search(pattern, name_lower, re.IGNORECASE):
                    return ClassificationResult(
                        source_type=source_type,
                        confidence=self.filename_confidence,
                        reasoning=f"文件名匹配关键词：{pattern}",
                        method="filename"
                    )

        return None

    def _match_content(self, content: str) -> Optional[ClassificationResult]:
        """策略2：内容关键词检测"""
        content_lower = content.lower()

        best_match = None
        best_count = 0

        for source_type, keywords in self.CONTENT_PATTERNS.items():
            count = sum(1 for kw in keywords if kw.lower() in content_lower)
            if count > best_count:
                best_count = count
                best_match = source_type

        if best_count >= 2:
            confidence = min(self.content_confidence + (best_count * 0.05), 0.85)
            matched_keywords = [
                kw for kw in self.CONTENT_PATTERNS[best_match]
                if kw.lower() in content_lower
            ]
            return ClassificationResult(
                source_type=best_match,
                confidence=confidence,
                reasoning=f"内容匹配关键词：{', '.join(matched_keywords[:3])}",
                method="content"
            )

        return None

    def _classify_with_llm(
        self,
        document_name: str,
        content_preview: str
    ) -> ClassificationResult:
        """
        策略3：LLM智能判断

        使用LLM对文档进行分类，这是准确度最高的策略
        """
        if not content_preview or len(content_preview.strip()) < 50:
            return ClassificationResult(
                source_type=SourceType.UNKNOWN,
                confidence=0.3,
                reasoning="内容不足以判断类型",
                method="llm"
            )

        user_prompt = DOCUMENT_CLASSIFICATION_USER_PROMPT.format(
            document_name=document_name,
            content_preview=content_preview
        )

        full_prompt = f"{DOCUMENT_CLASSIFICATION_SYSTEM_PROMPT}\n\n{FEWSHOT_EXAMPLES}\n\n{user_prompt}"

        try:
            response = self.llm_client.generate(full_prompt)

            result = self._parse_llm_response(response)
            if result:
                result.method = "llm"
                return result

        except Exception as e:
            print(f"LLM classification error: {e}")

        return ClassificationResult(
            source_type=SourceType.UNKNOWN,
            confidence=0.3,
            reasoning="LLM判断失败，使用默认分类",
            method="llm"
        )

    def _parse_llm_response(self, response: str) -> Optional[ClassificationResult]:
        """解析LLM响应"""
        try:
            json_match = re.search(r'\{[^}]+\}', response, re.DOTALL)
            if json_match:
                data = json.loads(json_match.group())
                source_type = SourceType.from_string(data.get('source_type', 'unknown'))
                confidence = float(data.get('confidence', 0.5))
                reasoning = str(data.get('reasoning', ''))

                if confidence < 0.3:
                    source_type = SourceType.UNKNOWN

                return ClassificationResult(
                    source_type=source_type,
                    confidence=confidence,
                    reasoning=reasoning,
                    method="llm"
                )
        except (json.JSONDecodeError, ValueError) as e:
            print(f"Failed to parse LLM response: {e}")

        return None


def classify_document(
    document_name: str,
    content: str,
    llm_client: Optional[LLMClient] = None
) -> Dict[str, Any]:
    """
    便捷函数：对文档进行来源类型分类

    参数:
        document_name: 文档名称/标题
        content: 文档内容
        llm_client: LLM客户端实例

    返回:
        分类结果字典
    """
    classifier = DocumentClassifier(llm_client=llm_client)
    result = classifier.classify(document_name, content)
    return result.to_dict()
