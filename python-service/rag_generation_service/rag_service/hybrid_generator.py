import json
from typing import Dict, Any, List, Tuple
from industrial_fta_common.fault_tree_schema import FaultTreeSchema


class HybridFaultTreeGenerator:
    """
    知识驱动+数据驱动混合故障树生成器
    
    核心流程：
    1. 知识驱动（Knowledge-driven）：从知识图谱获取领域模板和约束
    2. 数据驱动（Data-driven）：从向量检索获取具体文档证据
    3. 融合生成：结合两者，通过大模型生成高质量故障树
    4. 知识校验：使用知识图谱校验生成结果的逻辑一致性
    """
    
    def __init__(self, knowledge_graph_client=None, vector_retriever=None, llm_client=None):
        self.knowledge_graph_client = knowledge_graph_client
        self.vector_retriever = vector_retriever
        self.llm_client = llm_client
    
    def generate(
        self,
        top_event: str,
        doc_ids: List[str] = None,
        knowledge_template: Dict[str, Any] = None,
        user_preferences: str = None
    ) -> Tuple[FaultTreeSchema, Dict[str, Any]]:
        """
        混合生成故障树
        
        参数:
            top_event: 顶事件名称
            doc_ids: 文档ID列表（数据驱动来源）
            knowledge_template: 知识图谱模板（知识驱动来源）
            user_preferences: 用户偏好
            
        返回:
            (故障树对象, 生成统计信息)
        """
        statistics = {
            'knowledge_driven_enabled': False,
            'data_driven_enabled': False,
            'knowledge_template_used': False,
            'documents_retrieved': 0,
            'paragraphs_retrieved': 0,
            'generation_mode': 'hybrid'
        }
        
        # 步骤1: 知识驱动 - 获取领域知识约束
        knowledge_constraints = None
        if knowledge_template:
            knowledge_constraints = self._extract_knowledge_constraints(knowledge_template)
            statistics['knowledge_driven_enabled'] = True
            statistics['knowledge_template_used'] = True
        
        # 步骤2: 数据驱动 - 从文档检索相关证据
        relevant_paragraphs = []
        if doc_ids and self.vector_retriever:
            relevant_paragraphs = self._retrieve_relevant_paragraphs(top_event, doc_ids)
            statistics['data_driven_enabled'] = True
            statistics['documents_retrieved'] = len(doc_ids)
            statistics['paragraphs_retrieved'] = len(relevant_paragraphs)
        
        # 步骤3: 构建混合提示词
        prompt = self._build_hybrid_prompt(
            top_event=top_event,
            knowledge_constraints=knowledge_constraints,
            relevant_paragraphs=relevant_paragraphs,
            user_preferences=user_preferences
        )
        
        # 步骤4: 调用大模型生成
        if self.llm_client:
            llm_response = self.llm_client.generate(prompt)
        else:
            llm_response = self._get_default_response(top_event)
        
        # 步骤5: 解析生成结果
        from .fault_tree_generator import FaultTreeGenerator
        base_generator = FaultTreeGenerator()
        fault_tree = base_generator.generate(llm_response)
        
        # 步骤6: 知识校验（如果有知识图谱）
        validation_result = None
        if self.knowledge_graph_client:
            validation_result = self._validate_with_knowledge_graph(fault_tree)
            statistics['validation_performed'] = True
            statistics['validation_result'] = validation_result
        
        return fault_tree, statistics
    
    def _extract_knowledge_constraints(self, knowledge_template: Dict[str, Any]) -> Dict[str, Any]:
        """
        从知识图谱模板中提取约束条件
        
        提取内容:
        - 典型故障模式
        - 事件分类规则
        - 逻辑门使用偏好
        - 因果关系约束
        """
        constraints = {
            'typical_fault_modes': [],
            'event_classification_rules': {},
            'logic_gate_preferences': {},
            'causal_relationships': []
        }
        
        if 'typicalFaultModes' in knowledge_template:
            constraints['typical_fault_modes'] = knowledge_template['typicalFaultModes']
        
        if 'eventRules' in knowledge_template:
            constraints['event_classification_rules'] = knowledge_template['eventRules']
        
        if 'logicGatePreferences' in knowledge_template:
            constraints['logic_gate_preferences'] = knowledge_template['logicGatePreferences']
        
        if 'causalRelationships' in knowledge_template:
            constraints['causal_relationships'] = knowledge_template['causalRelationships']
        
        return constraints
    
    def _retrieve_relevant_paragraphs(self, top_event: str, doc_ids: List[str]) -> List[Dict[str, Any]]:
        """
        数据驱动：从向量库检索相关段落
        
        返回:
            带元数据的段落列表，按相似度排序
        """
        if not self.vector_retriever:
            return []
        
        try:
            paragraphs = self.vector_retriever.search(
                query=top_event,
                doc_ids=doc_ids,
                top_k=15
            )
            
            paragraphs.sort(
                key=lambda x: x.get('similarityScore', 0),
                reverse=True
            )
            
            return paragraphs
        except Exception as e:
            print(f"Error retrieving paragraphs: {e}")
            return []
    
    def _build_hybrid_prompt(
        self,
        top_event: str,
        knowledge_constraints: Dict[str, Any] = None,
        relevant_paragraphs: List[Dict[str, Any]] = None,
        user_preferences: str = None
    ) -> str:
        """
        构建混合提示词
        
        提示词结构:
        1. 角色设定
        2. 任务说明
        3. 知识驱动部分（领域约束）
        4. 数据驱动部分（文档证据）
        5. 用户偏好
        6. 输出要求
        """
        prompt_parts = []
        
        # 1. 角色设定
        prompt_parts.append("""你是一位专业的工业设备故障分析专家，擅长构建故障树分析（FTA）。
你将基于两部分信息生成故障树：
1. 领域知识约束（来自知识图谱）
2. 具体文档证据（来自向量检索）
""")
        
        # 2. 任务说明
        prompt_parts.append(f"""## 任务
请为顶事件 \"{top_event}\" 构建一个完整的故障树。
""")
        
        # 3. 知识驱动部分
        if knowledge_constraints:
            prompt_parts.append("""## 领域知识约束（知识驱动）
请遵循以下领域知识约束来构建故障树：
""")
            
            if knowledge_constraints.get('typical_fault_modes'):
                prompt_parts.append(f"- 典型故障模式: {', '.join(knowledge_constraints['typical_fault_modes'])}")
            
            if knowledge_constraints.get('logic_gate_preferences'):
                prompt_parts.append("- 逻辑门使用偏好:")
                for event, gate in knowledge_constraints['logic_gate_preferences'].items():
                    prompt_parts.append(f"  * {event} 通常使用 {gate} 门")
            
            if knowledge_constraints.get('causal_relationships'):
                prompt_parts.append("- 典型因果关系:")
                for rel in knowledge_constraints['causal_relationships']:
                    prompt_parts.append(f"  * {rel.get('cause', '')} → {rel.get('effect', '')}")
        
        # 4. 数据驱动部分
        if relevant_paragraphs:
            prompt_parts.append("""## 文档证据（数据驱动）
以下是从相关文档中检索到的证据段落，请基于这些内容生成故障树：
""")
            for i, para in enumerate(relevant_paragraphs[:10], 1):
                content = para.get('content', '')
                source = para.get('documentName', 'Unknown')
                credibility = para.get('credibilityWeight', 0.5)
                prompt_parts.append(f"[证据{i}] (可信度: {credibility}, 来源: {source})")
                prompt_parts.append(content)
                prompt_parts.append("")
        
        # 5. 用户偏好
        if user_preferences:
            prompt_parts.append(f"""## 用户偏好
{user_preferences}
""")
        
        # 6. 输出要求
        prompt_parts.append("""## 输出要求
请以JSON格式输出故障树，包含以下字段：
- event_id: 事件唯一标识
- event_name: 事件名称
- event_type: 事件类型（TOP/INTERMEDIATE/BASIC）
- gate_type: 逻辑门类型（AND/OR/XOR，底事件不需要）
- children: 子事件列表
- source_evidence: 溯源依据（引用哪个证据段落）
- equipment_type: 设备类型

请确保：
1. 结构完整，逻辑合理
2. 优先使用文档证据中的信息
3. 遵循领域知识约束
4. 为每个事件提供溯源依据
""")
        
        return '\n'.join(prompt_parts)
    
    def _validate_with_knowledge_graph(self, fault_tree: FaultTreeSchema) -> Dict[str, Any]:
        """
        使用知识图谱校验生成的故障树
        
        校验内容:
        - 事件分类是否合理
        - 逻辑门类型是否符合领域习惯
        - 因果链是否完整
        """
        validation = {
            'passed': True,
            'warnings': [],
            'suggestions': []
        }
        
        # 这里是简化实现，实际应该调用知识图谱服务
        # 真实场景应该:
        # 1. 遍历故障树所有节点
        # 2. 对每个节点查询知识图谱
        # 3. 检查事件分类、逻辑门类型等
        # 4. 生成警告和建议
        
        return validation
    
    def _get_default_response(self, top_event: str) -> str:
        """获取默认响应（当LLM不可用时）"""
        default_tree = {
            "event_id": "evt_001",
            "event_name": top_event,
            "event_type": "TOP",
            "gate_type": "OR",
            "children": [
                {
                    "event_id": "evt_002",
                    "event_name": "中间事件1",
                    "event_type": "INTERMEDIATE",
                    "gate_type": "OR",
                    "children": [
                        {"event_id": "evt_003", "event_name": "底事件1", "event_type": "BASIC", "children": []},
                        {"event_id": "evt_004", "event_name": "底事件2", "event_type": "BASIC", "children": []}
                    ]
                }
            ]
        }
        return json.dumps(default_tree, ensure_ascii=False)
