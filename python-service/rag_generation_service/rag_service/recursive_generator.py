
import json
import uuid
from typing import Dict, Any, List, Optional
from industrial_fta_common.fault_tree_schema import FaultTreeSchema
import logging

logger = logging.getLogger(__name__)


class RecursiveFaultTreeGenerator:
    """
    递归分批次故障树生成器
    
    核心思想：
    1. 先生成故障树的顶层（包含主要分支
    2. 对每个标记为"待展开"的中间事件，递归调用 LLM 生成子树
    3. 合并所有子树，形成完整的故障树
    
    优势：
    - 不受单次 max_tokens 限制
    - 可以生成非常深且复杂的故障树
    """
    
    def __init__(self, llm_client):
        self.llm_client = llm_client
        self.max_recursion_depth = 5  # 最大递归深度
        self.PLACEHOLDER_MARK = "[待展开]"  # 标记需要继续生成的节点
    
    def generate(
        self,
        top_event: str,
        knowledge_constraints: Dict[str, Any] = None,
        relevant_paragraphs: List[Dict[str, Any]] = None
    ) -&gt; FaultTreeSchema:
        """
        递归生成完整故障树
        
        参数:
            top_event: 顶事件名称
            knowledge_constraints: 领域知识约束
            relevant_paragraphs: 相关文档证据
            
        返回:
            完整的故障树对象
        """
        logger.info(f"Starting recursive fault tree generation for: {top_event}")
        
        # 第一步：生成顶层故障树
        print("\n" + "="*80)
        print("🌳 步骤 1/2: 生成顶层故障树")
        print("="*80)
        
        top_tree = self._generate_top_level(
            top_event=top_event,
            knowledge_constraints=knowledge_constraints,
            relevant_paragraphs=relevant_paragraphs
        )
        
        # 第二步：递归展开占位符节点
        print("\n" + "="*80)
        print("🔄 步骤 2/2: 递归展开子树")
        print("="*80)
        
        full_tree = self._recursively_expand_tree(
            tree=top_tree,
            knowledge_constraints=knowledge_constraints,
            relevant_paragraphs=relevant_paragraphs,
            current_depth=0
        )
        
        print("\n" + "="*80)
        print("✅ 递归生成完成！")
        print("="*80)
        
        return full_tree
    
    def _generate_top_level(
        self,
        top_event: str,
        knowledge_constraints: Dict[str, Any] = None,
        relevant_paragraphs: List[Dict[str, Any]] = None
    ) -&gt; FaultTreeSchema:
        """生成顶层故障树"""
        prompt = self._build_top_level_prompt(
            top_event=top_event,
            knowledge_constraints=knowledge_constraints,
            relevant_paragraphs=relevant_paragraphs
        )
        
        llm_response = self.llm_client.generate(prompt, top_event=top_event)
        
        from .fault_tree_generator import FaultTreeGenerator
        base_generator = FaultTreeGenerator()
        return base_generator.generate(llm_response, top_event)
    
    def _build_top_level_prompt(
        self,
        top_event: str,
        knowledge_constraints: Dict[str, Any] = None,
        relevant_paragraphs: List[Dict[str, Any]] = None
    ) -&gt; str:
        """构建顶层生成提示词"""
        prompt_parts = []
        
        prompt_parts.append("""你是一位专业的工业设备故障分析专家。
你的任务是构建故障树分析（FTA）。

重要提示：
- 对于复杂的中间事件，请使用标记 "[待展开]"，这些节点后续会单独生成详细子树。
- 保持顶层只需要生成主要的故障路径。
""")
        
        prompt_parts.append(f"""## 任务
请为顶事件 \"{top_event}\" 构建故障树的顶层结构。
""")
        
        if knowledge_constraints:
            prompt_parts.append("""## 领域知识约束
请遵循以下领域知识约束：
""")
            if knowledge_constraints.get('typical_fault_modes'):
                prompt_parts.append(f"- 典型故障模式: {', '.join(knowledge_constraints['typical_fault_modes'])}")
        
        if relevant_paragraphs:
            prompt_parts.append("""## 文档证据
以下是从相关文档中检索到的证据段落：
""")
            for i, para in enumerate(relevant_paragraphs[:5], 1):
                content = para.get('content', '')[:200])
                prompt_parts.append(f"[证据{i}] {content}")
        
        prompt_parts.append("""## 输出要求
请以JSON格式输出故障树：
- event_id: 事件唯一标识
- event_name: 事件名称
- event_type: 事件类型（TOP/INTERMEDIATE/BASIC）
- gate_type: 逻辑门类型（AND/OR/XOR）
- children: 子事件列表
- source_evidence: 溯源依据
- equipment_type: 设备类型

重要：对于复杂的中间事件，请将event_name设置为"[待展开]具体事件名"，后续会单独展开。
""")
        
        return '\n'.join(prompt_parts)
    
    def _recursively_expand_tree(
        self,
        tree: FaultTreeSchema,
        knowledge_constraints: Dict[str, Any] = None,
        relevant_paragraphs: List[Dict[str, Any]] = None,
        current_depth: int = 0
    ) -&gt; FaultTreeSchema:
        """递归展开树中的占位符节点"""
        
        if current_depth &gt;= self.max_recursion_depth:
            logger.warning(f"Max recursion depth {self.max_recursion_depth} reached")
            return tree
        
        # 检查当前节点是否需要展开
        if self.PLACEHOLDER_MARK in tree.event_name:
            # 提取真实事件名
            real_event_name = tree.event_name.replace(self.PLACEHOLDER_MARK, '').strip()
            print(f"{'  ' * current_depth}📌 展开节点: {real_event_name} (深度 {current_depth})")
            
            # 生成子树
            subtree = self._generate_subtree(
                parent_event=real_event_name,
                knowledge_constraints=knowledge_constraints,
                relevant_paragraphs=relevant_paragraphs
            )
            
            # 更新当前节点（保留原event_id）
            tree.event_name = subtree.event_name
            tree.event_type = subtree.event_type
            tree.gate_type = subtree.gate_type
            tree.children = subtree.children
            tree.source_evidence = subtree.source_evidence
        
        # 递归处理子节点
        expanded_children = []
        for child in tree.children:
            expanded_child = self._recursively_expand_tree(
                tree=child,
                knowledge_constraints=knowledge_constraints,
                relevant_paragraphs=relevant_paragraphs,
                current_depth=current_depth + 1
            )
            expanded_children.append(expanded_child)
        
        tree.children = expanded_children
        return tree
    
    def _generate_subtree(
        self,
        parent_event: str,
        knowledge_constraints: Dict[str, Any] = None,
        relevant_paragraphs: List[Dict[str, Any]] = None
    ) -&gt; FaultTreeSchema:
        """为某个事件生成详细子树"""
        prompt = self._build_subtree_prompt(
            parent_event=parent_event,
            knowledge_constraints=knowledge_constraints,
            relevant_paragraphs=relevant_paragraphs
        )
        
        llm_response = self.llm_client.generate(prompt, top_event=parent_event)
        
        from .fault_tree_generator import FaultTreeGenerator
        base_generator = FaultTreeGenerator()
        return base_generator.generate(llm_response, parent_event)
    
    def _build_subtree_prompt(
        self,
        parent_event: str,
        knowledge_constraints: Dict[str, Any] = None,
        relevant_paragraphs: List[Dict[str, Any]] = None
    ) -&gt; str:
        """构建子树生成提示词"""
        prompt_parts = []
        
        prompt_parts.append("""你是一位专业的工业设备故障分析专家。
你的任务是为某个故障事件生成详细的子树。
""")
        
        prompt_parts.append(f"""## 任务
请为事件 \"{parent_event}\" 生成详细的故障子树。
请详细分析这个事件可能的原因，构建完整的子树结构。
""")
        
        if relevant_paragraphs:
            prompt_parts.append("""## 相关文档证据
以下是相关文档段落，请参考：
""")
            for i, para in enumerate(relevant_paragraphs[:3], 1):
                content = para.get('content', '')
                if parent_event.lower() in content.lower():
                    prompt_parts.append(f"[证据{i}] {content[:300]}")
        
        prompt_parts.append("""## 输出要求
请以JSON格式输出子树（从该事件作为顶节点）：
- event_id: 事件唯一标识
- event_name: 事件名称
- event_type: 事件类型（TOP/INTERMEDIATE/BASIC）
- gate_type: 逻辑门类型
- children: 子事件列表
""")
        
        return '\n'.join(prompt_parts)

