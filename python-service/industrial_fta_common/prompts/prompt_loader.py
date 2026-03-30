# Prompt Loader
# 统一加载和组装提示词的模块

from .fault_tree_generation import FAULT_TREE_GENERATION_PROMPT
from .event_type_rules import EVENT_TYPE_RULES
from .basic_event_keywords import get_keywords_string
from .fewshot_examples import get_all_examples

def load_fault_tree_prompt(top_event, industrial_knowledge, kg_template=""):
    """
    加载故障树生成的完整提示词

    参数:
        top_event: 顶事件名称
        industrial_knowledge: 工业知识（多个段落组成的字符串）
        kg_template: 知识图谱模板（可选）

    返回:
        组装好的完整提示词
    """
    return FAULT_TREE_GENERATION_PROMPT.format(
        top_event=top_event,
        industrial_knowledge=industrial_knowledge,
        kg_template=kg_template if kg_template else "无",
        event_type_rules=EVENT_TYPE_RULES,
        basic_event_keywords=get_keywords_string(),
        fewshot_examples=get_all_examples()
    )

__all__ = ['load_fault_tree_prompt']
