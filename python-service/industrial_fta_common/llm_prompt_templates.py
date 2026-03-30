class LLMPromptTemplates:
    # 故障树生成提示模板
    FAULT_TREE_GENERATION = """
你是一位专业的工业设备故障分析专家，擅长构建故障树分析（FTA）。

请基于以下提供的工业知识和数据，为顶事件 "{top_event}" 构建一个完整的故障树。

工业知识：
{industrial_knowledge}

知识图谱模板：
{kg_template}

请按照以下要求生成故障树：
1. 清晰识别顶事件、中间事件和底事件
2. 正确使用逻辑门（AND、OR、XOR）
3. 确保故障树结构完整，逻辑合理
4. 为每个事件提供简要说明和溯源依据
5. 按照FTA标准格式输出故障树

输出格式：
请以JSON格式输出故障树，包含以下字段：
- event_id: 事件唯一标识
- event_name: 事件名称
- event_type: 事件类型（TOP、INTERMEDIATE、BASIC）
- gate_type: 逻辑门类型（AND、OR、XOR，底事件不需要）
- children: 子事件列表
- source_evidence: 溯源依据
- equipment_type: 设备类型
"""
    
    # 故障树逻辑校验提示模板
    FAULT_TREE_VALIDATION = """
你是一位专业的故障树分析专家，请检查以下故障树的逻辑合理性：

{fault_tree}

请检查以下问题：
1. 是否存在循环依赖
2. 底事件是否有子节点
3. 逻辑门连接数是否合规（AND/OR至少两个输入）
4. 事件类型是否正确

请提供详细的校验报告，包括问题所在和修复建议。
"""
