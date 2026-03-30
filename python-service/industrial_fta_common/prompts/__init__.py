# Prompts 包
# 用于存储故障树生成相关的提示词模板

from .fault_tree_generation import FAULT_TREE_GENERATION_PROMPT
from .event_type_rules import EVENT_TYPE_RULES
from .logic_gate_rules import LOGIC_GATE_RULES
from .basic_event_keywords import BASIC_EVENT_KEYWORDS, get_keywords_string
from .fewshot_examples import (
    FEWSHOT_MOTOR_OVERHEATING,
    FEWSHOT_PUMP_FAILURE,
    get_all_examples
)
from .common_fault_patterns import (
    COMMON_FAULT_PATTERNS,
    get_all_patterns_string,
    get_patterns_by_category
)
from .document_classification import (
    DOCUMENT_CLASSIFICATION_SYSTEM_PROMPT,
    DOCUMENT_CLASSIFICATION_USER_PROMPT,
    FEWSHOT_EXAMPLES
)

__all__ = [
    'FAULT_TREE_GENERATION_PROMPT',
    'EVENT_TYPE_RULES',
    'LOGIC_GATE_RULES',
    'BASIC_EVENT_KEYWORDS',
    'get_keywords_string',
    'FEWSHOT_MOTOR_OVERHEATING',
    'FEWSHOT_PUMP_FAILURE',
    'get_all_examples',
    'COMMON_FAULT_PATTERNS',
    'get_all_patterns_string',
    'get_patterns_by_category',
    'DOCUMENT_CLASSIFICATION_SYSTEM_PROMPT',
    'DOCUMENT_CLASSIFICATION_USER_PROMPT',
    'FEWSHOT_EXAMPLES',
]
