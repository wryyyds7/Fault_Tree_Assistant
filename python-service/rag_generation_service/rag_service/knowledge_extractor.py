import os
import json
import re
from typing import List, Dict, Any, Optional, Tuple
from difflib import SequenceMatcher
from .llm_client import LLMClient


class KnowledgeExtractor:
    """
    LLM驱动的文档知识抽取器 - 专门针对工业驱动系统故障文档

    从文档段落中提取适用于知识图谱的完整故障树分析知识：
    - 组件节点（控制单元、功率单元、电机模块等）
    - 故障代码事件（F01000、F01002等）
    - 故障原因事件
    - 因果关系（原因->结果）
    - 组件与故障的关联关系
    """

    KNOWLEDGE_EXTRACTION_PROMPT = """你是一位拥有20年工业驱动系统故障分析经验的资深专家，专注于西门子、ABB等品牌驱动系统故障树分析。

## 【核心任务】
仔细阅读以下工业驱动系统故障文档，精准抽取知识图谱所需的完整知识。

## 【关键概念】

### 1. 组件/对象（Component）
文档中提到的设备组件，如：
- 控制单元（CU）
- 功率单元
- 电机模块（MM）
- 液压模块（HLA）
- 编码器及传感器
- 端子模块（TM54F、TM31）
- 通讯组件（DRIVE-CLiQ、PROFINET）
- 辅助组件（制动模块、电源滤波器、散热装置）

### 2. 事件类型定义
1. **故障代码事件（FAULT_CODE_EVENT）**：文档中明确提到的故障代码，如F01000、F01002、A01013等
   - 特征：以F或A开头，后面跟着数字
   - 重要：必须完整提取故障代码（包括前缀）
   - 严重性：CRITICAL（F开头）或 WARNING（A开头）

2. **故障现象事件（FAULT_SYMPTOM_EVENT）**：故障的外在表现，如"电机过热停机"、"通讯中断"等

3. **故障原因事件（FAULT_CAUSE_EVENT）**：导致故障的原因，如"内部软件错误"、"轴承润滑不足"等

### 3. 关系类型定义
1. **因果关系（CAUSES）**：原因事件 -> 结果事件（故障代码或故障现象）
   - 关键词："导致"、"引起"、"原因是"、"会触发"

2. **组件关联关系（ASSOCIATED_WITH）**：故障代码 -> 组件
   - 例如：故障代码F01000 关联 控制单元（CU）

3. **故障代码分类关系（IS_TYPE_OF）**：具体故障代码 -> 故障类型
   - 例如：F01000 IS_TYPE_OF 硬件/软件故障

## 【Few-Shot 示例 - 驱动系统故障文档】

### 示例输入：
```
故障代码F01000：故障类别为硬件/软件故障（PROFIdrive编号1），驱动对象为所有目标，组件为控制单元（CU），传播方式为GLOBAL，故障反应为OFF2，应答方式为上电，信息值格式为"模块:%1,行:%2"，关联故障值r0949（十六进制，设备内部诊断专用）。

故障代码F01002：故障类别为硬件/软件故障，驱动对象为所有目标，组件为控制单元（CU）。

针对F01000、F01002、F01015等内部软件错误类故障，需先读取故障缓冲器r0945的详细信息。
```

### 预期输出：
```json
{
  "events": [
    {
      "name": "F01000",
      "type": "FAULT_CODE_EVENT",
      "description": "控制单元硬件/软件故障，故障反应为OFF2，应答方式为上电，关联故障值r0949",
      "severity": "CRITICAL",
      "probability": 0.15,
      "faultCode": "F01000",
      "component": "控制单元（CU）"
    },
    {
      "name": "F01002",
      "type": "FAULT_CODE_EVENT",
      "description": "控制单元硬件/软件故障",
      "severity": "CRITICAL",
      "probability": 0.12,
      "faultCode": "F01002",
      "component": "控制单元（CU）"
    },
    {
      "name": "F01015",
      "type": "FAULT_CODE_EVENT",
      "description": "内部软件错误",
      "severity": "CRITICAL",
      "probability": 0.10,
      "faultCode": "F01015",
      "component": "控制单元（CU）"
    },
    {
      "name": "内部软件错误",
      "type": "FAULT_CAUSE_EVENT",
      "description": "控制单元内部软件运行异常",
      "severity": "MAJOR",
      "probability": 0.25
    },
    {
      "name": "硬件故障",
      "type": "FAULT_CAUSE_EVENT",
      "description": "控制单元硬件组件故障",
      "severity": "MAJOR",
      "probability": 0.20
    }
  ],
  "causalRelations": [
    {
      "cause": "内部软件错误",
      "effect": "F01000",
      "gateType": "OR",
      "description": "内部软件错误会导致F01000故障"
    },
    {
      "cause": "内部软件错误",
      "effect": "F01002",
      "gateType": "OR",
      "description": "内部软件错误会导致F01002故障"
    },
    {
      "cause": "内部软件错误",
      "effect": "F01015",
      "gateType": "OR",
      "description": "内部软件错误会导致F01015故障"
    },
    {
      "cause": "硬件故障",
      "effect": "F01000",
      "gateType": "OR",
      "description": "硬件故障会导致F01000故障"
    },
    {
      "cause": "硬件故障",
      "effect": "F01002",
      "gateType": "OR",
      "description": "硬件故障会导致F01002故障"
    }
  ],
  "componentAssociations": [
    {
      "event": "F01000",
      "component": "控制单元（CU）",
      "description": "F01000是控制单元的故障代码"
    },
    {
      "event": "F01002",
      "component": "控制单元（CU）",
      "description": "F01002是控制单元的故障代码"
    },
    {
      "event": "F01015",
      "component": "控制单元（CU）",
      "description": "F01015是控制单元的故障代码"
    }
  ],
  "components": [
    {
      "name": "控制单元（CU）",
      "description": "驱动系统的核心控制模块，负责接收上位控制器指令、处理故障诊断信号、协调各组件间通讯",
      "type": "CORE_COMPONENT"
    }
  ],
  "equipmentType": "通用型驱动系统",
  "faultModes": ["硬件故障", "软件故障", "内部错误"]
}
```

## 【提取规则（非常重要！必须严格遵守！）】

### 1. 事件提取规则
1. **必须提取所有故障代码**：寻找所有以F或A开头的故障代码，如F01000、A01013等
2. **故障代码事件必须包含完整代码**，不要省略
3. **每个故障代码事件必须关联组件**：从文档中找到该故障代码对应的组件
4. **提取所有故障原因**：如"内部软件错误"、"硬件故障"、"通讯中断"等
5. **提取所有故障现象**：如"电机过热停机"、"系统无法启动"等
6. **每个事件必须有详细描述**：尽可能从原文提取
7. **评估严重程度**：CRITICAL（F开头故障）> MAJOR（主要故障）> WARNING（A开头报警）> MINOR（次要）
8. **概率值**：根据文档中出现频率，若无明确数值，范围0.05-0.3

### 2. 因果关系提取规则
1. **仔细分析文本中的因果逻辑**：原因->结果
2. **特别注意故障分类描述**：如"针对F01000、F01002等内部软件错误类故障"，这表明"内部软件错误"是这些故障代码的原因
3. **多步因果链要完整提取**
4. **识别逻辑门类型**：OR（任一原因导致）或 AND（所有原因同时需要）

### 3. 组件提取与关联规则
1. **提取文档中所有组件**：控制单元、功率单元、电机模块等
2. **每个故障代码必须关联到对应的组件**
3. **建立组件与故障代码的关联关系**

### 4. 设备类型识别
1. **从文档中识别设备类型**：如"通用型驱动系统"、"电机模块"等
2. **如果文档中没有明确的设备类型，则根据上下文推断**

## 【输出格式】
请严格按照以下JSON格式输出，不要添加任何额外内容：
```json
{
  "events": [
    {
      "name": "事件名称（故障代码必须完整，如F01000）",
      "type": "FAULT_CODE_EVENT | FAULT_SYMPTOM_EVENT | FAULT_CAUSE_EVENT",
      "description": "事件详细描述",
      "severity": "CRITICAL | MAJOR | MINOR | WARNING",
      "probability": 0.0-1.0之间的数值,
      "faultCode": "如果是故障代码事件，填写故障代码，否则不填",
      "component": "该事件关联的组件，如控制单元（CU）"
    }
  ],
  "causalRelations": [
    {
      "cause": "原因事件名称",
      "effect": "结果事件名称",
      "gateType": "AND | OR",
      "description": "因果关系描述"
    }
  ],
  "componentAssociations": [
    {
      "event": "事件名称（通常是故障代码）",
      "component": "组件名称",
      "description": "关联关系描述"
    }
  ],
  "components": [
    {
      "name": "组件名称",
      "description": "组件详细描述",
      "type": "CORE_COMPONENT | AUXILIARY_COMPONENT | SENSOR_COMPONENT | COMMUNICATION_COMPONENT"
    }
  ],
  "equipmentType": "设备类型",
  "faultModes": ["故障模式1", "故障模式2"]
}
```

## 【输出约束】
1. **必须提取所有故障代码**，这是最重要的！
2. **事件名称必须准确**：故障代码要完整（如F01000，不能只写01000）
3. **因果关系方向必须正确**（原因→结果）
4. **严格按照JSON格式输出**，不要添加markdown语法标签
5. **不要添加任何额外的文本解释**
6. **如果文档中没有故障相关内容，则返回空数组**

## 【待分析的文档内容】：
{content}

## 输出（仅输出JSON）：
"""

    def __init__(self, llm_client: Optional[LLMClient] = None):
        """
        初始化知识抽取器

        参数:
            llm_client: LLM客户端，如果不提供则创建新的
        """
        self.llm_client = llm_client or LLMClient()
        self.max_chars_per_batch = int(os.getenv('KG_EXTRACT_CHUNK_SIZE', '6000'))

    def extract_knowledge_from_paragraphs(
        self,
        paragraphs: List[Dict[str, Any]],
        source_type: str,
        equipment_type: str = 'general',
        doc_id: str = None
    ) -> Dict[str, Any]:
        """
        从段落列表中提取知识

        参数:
            paragraphs: 段落列表，每个段落包含 content, paragraphId 等
            source_type: 文档来源类型
            equipment_type: 设备类型
            doc_id: 文档ID（用于日志）

        返回:
            提取的知识，包含 events, causalRelations, componentAssociations, components, equipmentType, faultModes
        """
        print(f"\n[KG-Extract] ★★★ 开始知识抽取 ★★★")
        print(f"[KG-Extract] doc_id: {doc_id}")
        print(f"[KG-Extract] source_type: {source_type}")
        print(f"[KG-Extract] equipment_type: {equipment_type}")
        print(f"[KG-Extract] 段落数量: {len(paragraphs)}")

        if source_type in ['未知', 'unknown', '']:
            print(f"[KG-Extract] ✗ 文档类型为 '{source_type}'，跳过知识抽取")
            return {
                'events': [],
                'causalRelations': [],
                'componentAssociations': [],
                'components': [],
                'equipmentType': equipment_type,
                'faultModes': []
            }

        all_events = []
        all_causal_relations = []
        all_component_associations = []
        all_components = []
        all_fault_modes = []

        print(f"[KG-Extract] DEBUG: 开始合并段落")
        combined_content = self._combine_paragraphs(paragraphs)
        print(f"[KG-Extract] DEBUG: 段落合并完成，内容长度={len(combined_content)}")

        chunks = self._split_into_chunks(combined_content)
        print(f"[KG-Extract] 分成 {len(chunks)} 个批次进行抽取")

        for i, chunk in enumerate(chunks):
            print(f"[KG-Extract] 处理批次 {i+1}/{len(chunks)}, chunk长度={len(chunk)}")

            extracted = self._extract_from_chunk(chunk, equipment_type)
            if extracted:
                all_events.extend(extracted.get('events', []))
                all_causal_relations.extend(extracted.get('causalRelations', []))
                all_component_associations.extend(extracted.get('componentAssociations', []))
                all_components.extend(extracted.get('components', []))
                if extracted.get('equipmentType'):
                    equipment_type = extracted['equipmentType']
                all_fault_modes.extend(extracted.get('faultModes', []))

        print(f"[KG-Extract] 初步提取完成，开始智能合并与验证...")

        # 步骤1：智能去重合并事件
        merged_events = self._intelligent_deduplicate_events(all_events)
        print(f"[KG-Extract] 智能合并后事件：{len(all_events)} → {len(merged_events)}")

        # 步骤2：去重组件
        merged_components = self._deduplicate_components(all_components)
        print(f"[KG-Extract] 去重后组件：{len(all_components)} → {len(merged_components)}")

        # 步骤3：去重组件关联
        merged_associations = self._deduplicate_component_associations(all_component_associations)
        print(f"[KG-Extract] 去重后组件关联：{len(all_component_associations)} → {len(merged_associations)}")

        # 步骤4：建立事件映射
        event_mapping = self._build_event_mapping(merged_events)

        # 步骤5：解析因果关系引用
        resolved_relations = self._resolve_relation_references(all_causal_relations, event_mapping)

        # 步骤6：去重因果关系
        unique_relations = self._deduplicate_relations(resolved_relations)

        # 步骤7：去重故障模式
        unique_fault_modes = list(set(all_fault_modes))

        # 步骤8：后处理和验证
        raw_result = {
            'events': merged_events,
            'causalRelations': unique_relations,
            'componentAssociations': merged_associations,
            'components': merged_components,
            'equipmentType': equipment_type,
            'faultModes': unique_fault_modes
        }

        validated_result = self._post_process_extracted_data(raw_result)

        result = {
            'events': validated_result['events'],
            'causalRelations': validated_result['causalRelations'],
            'componentAssociations': validated_result.get('componentAssociations', []),
            'components': validated_result.get('components', []),
            'equipmentType': validated_result['equipmentType'],
            'faultModes': validated_result['faultModes'],
            'docId': doc_id,
            'sourceType': source_type
        }

        print(f"[KG-Extract] ✓ 最终抽取完成:")
        print(f"[KG-Extract]   - 事件数: {len(result['events'])}")
        print(f"[KG-Extract]   - 因果关系数: {len(result['causalRelations'])}")
        print(f"[KG-Extract]   - 组件数: {len(result.get('components', []))}")
        print(f"[KG-Extract]   - 组件关联数: {len(result.get('componentAssociations', []))}")
        print(f"[KG-Extract]   - 故障模式数: {len(result['faultModes'])}")

        if result['events']:
            print(f"[KG-Extract]   - 事件示例:")
            for evt in result['events'][:5]:
                print(f"[KG-Extract]     * {evt['name']} ({evt['type']})")

        return result

    def _intelligent_deduplicate_events(self, events: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """智能去重合并事件"""
        unique_events = []

        for event in events:
            name = event.get('name', '').strip()
            if not name:
                continue

            # 查找是否有相似的事件
            similar_event = self._find_similar_event(event, unique_events)

            if similar_event:
                # 合并两个事件
                merged = self._merge_events(similar_event, event)
                # 更新
                idx = unique_events.index(similar_event)
                unique_events[idx] = merged
            else:
                unique_events.append(event)

        return unique_events

    def _deduplicate_components(self, components: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """去重组件"""
        seen = set()
        unique = []
        for comp in components:
            name = comp.get('name', '').strip()
            if name and name not in seen:
                seen.add(name)
                unique.append(comp)
        return unique

    def _deduplicate_component_associations(self, associations: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """去重组件关联"""
        seen = set()
        unique = []
        for assoc in associations:
            event = assoc.get('event', '').strip()
            component = assoc.get('component', '').strip()
            key = (event, component)
            if event and component and key not in seen:
                seen.add(key)
                unique.append(assoc)
        return unique

    def _combine_paragraphs(self, paragraphs: List[Dict[str, Any]]) -> str:
        """将段落合并为单个文本"""
        contents = []
        for p in paragraphs:
            content = p.get('content', '')
            if content and len(content) > 10:
                contents.append(content)
        return '\n\n'.join(contents)

    def _split_into_chunks(self, text: str) -> List[str]:
        """将文本分割成多个块"""
        if len(text) <= self.max_chars_per_batch:
            return [text] if text.strip() else []

        chunks = []
        current_pos = 0
        while current_pos < len(text):
            chunk_end = min(current_pos + self.max_chars_per_batch, len(text))
            if chunk_end < len(text):
                # 尝试在段落边界分割
                newline_pos = text.rfind('\n\n', current_pos, chunk_end)
                if newline_pos > current_pos:
                    chunk_end = newline_pos + 2
                else:
                    newline_pos = text.rfind('\n', current_pos, chunk_end)
                    if newline_pos > current_pos:
                        chunk_end = newline_pos + 1
            chunk = text[current_pos:chunk_end].strip()
            if chunk:
                chunks.append(chunk)
            current_pos = chunk_end
        return chunks

    def _extract_from_chunk(self, chunk: str, equipment_type: str) -> Dict[str, Any]:
        """从单个文本块中抽取知识"""
        print("="*80)
        print("[DEBUG-KG-Extract] 进入 _extract_from_chunk")
        print(f"[DEBUG-KG-Extract] chunk 长度: {len(chunk)}")
        print(f"[DEBUG-KG-Extract] chunk 前100字符: {chunk[:100]}")
        
        try:
            print(f"[DEBUG-KG-Extract] 准备格式化 prompt...")
            prompt = self.KNOWLEDGE_EXTRACTION_PROMPT.format(content=chunk)
            print(f"[DEBUG-KG-Extract] prompt 格式化成功，长度: {len(prompt)}")
        except Exception as e:
            import traceback
            print(f"[DEBUG-KG-Extract] prompt 格式化失败: {e}")
            print(f"[DEBUG-KG-Extract] 堆栈:\n{traceback.format_exc()}")
            raise
        
        print(f"[KG-Extract] DEBUG: 调用 LLM，chunk长度={len(chunk)}")

        try:
            # 禁用 fallback，让 KnowledgeExtractor 的异常处理接管
            print(f"[DEBUG-KG-Extract] 准备调用 llm_client.generate...")
            response = self.llm_client.generate(prompt, use_fallback=False)
            print(f"[KG-Extract] DEBUG: LLM调用成功，响应长度={len(response)}")
            return self._parse_llm_response(response)
        except Exception as e:
            import traceback
            print(f"[KG-Extract] ✗ LLM抽取异常: {type(e).__name__}: {e}")
            traceback.print_exc()
            return {
                'events': [],
                'causalRelations': [],
                'componentAssociations': [],
                'components': [],
                'equipmentType': equipment_type,
                'faultModes': []
            }

    def _parse_llm_response(self, response: str) -> Dict[str, Any]:
        """解析LLM响应"""
        print(f"[KG-Parse] >>> _parse_llm_response 被调用，响应长度={len(response)}")
        try:
            response = response.strip()
            print(f"[KG-Parse] 原始响应长度: {len(response)}")
            print(f"[KG-Parse] 原始响应前200字符: {response[:200]}")

            if response.startswith('```json'):
                response = response[7:]
            if response.startswith('```'):
                response = response[3:]
            if response.endswith('```'):
                response = response[:-3]

            response = response.strip()

            if not response:
                print(f"[KG-Parse] ✗ 响应为空")
                return {
                    'events': [],
                    'causalRelations': [],
                    'componentAssociations': [],
                    'components': [],
                    'equipmentType': '',
                    'faultModes': []
                }

            if not response.startswith('{'):
                print(f"[KG-Parse] ✗ 响应不是JSON对象，以文本返回: {response[:100]}")
                return {
                    'events': [],
                    'causalRelations': [],
                    'componentAssociations': [],
                    'components': [],
                    'equipmentType': '',
                    'faultModes': []
                }

            result = json.loads(response)

            # 确保返回的结构符合预期，即使原始响应缺少字段
            parsed_result = {
                'events': result.get('events', []),
                'causalRelations': result.get('causalRelations', []),
                'componentAssociations': result.get('componentAssociations', []),
                'components': result.get('components', []),
                'equipmentType': result.get('equipmentType', ''),
                'faultModes': result.get('faultModes', [])
            }

            # 验证各字段是否为列表
            for field in ['events', 'causalRelations', 'componentAssociations', 'components', 'faultModes']:
                if not isinstance(parsed_result[field], list):
                    print(f"[KG-Parse] ⚠️ {field}字段不是列表，重置为空")
                    parsed_result[field] = []

            print(f"[KG-Parse] ✓ 成功解析: {len(parsed_result['events'])}个事件, {len(parsed_result['causalRelations'])}个关系, {len(parsed_result['components'])}个组件")
            return parsed_result

        except json.JSONDecodeError as e:
            print(f"[KG-Parse] ✗ JSON解析失败: {e}")
            print(f"[KG-Parse]   响应内容: {response[:500]}")

            # 尝试从错误位置提取有效的JSON
            try:
                # 尝试查找第一个 '{' 和最后一个 '}' 之间的内容
                start_idx = response.find('{')
                end_idx = response.rfind('}')
                if start_idx != -1 and end_idx != -1 and end_idx > start_idx:
                    partial_json = response[start_idx:end_idx+1]
                    print(f"[KG-Parse] 🔍 尝试解析部分JSON: {partial_json[:300]}")
                    result = json.loads(partial_json)
                    parsed_result = {
                        'events': result.get('events', []),
                        'causalRelations': result.get('causalRelations', []),
                        'componentAssociations': result.get('componentAssociations', []),
                        'components': result.get('components', []),
                        'equipmentType': result.get('equipmentType', ''),
                        'faultModes': result.get('faultModes', [])
                    }
                    if isinstance(parsed_result['events'], list):
                        print(f"[KG-Parse] ✓ 从部分JSON中解析出 {len(parsed_result['events'])} 个事件")
                        return parsed_result
            except Exception as partial_e:
                print(f"[KG-Parse] ✗ 部分JSON解析也失败: {partial_e}")

            return {
                'events': [],
                'causalRelations': [],
                'componentAssociations': [],
                'components': [],
                'equipmentType': '',
                'faultModes': []
            }

    def _deduplicate_events(self, events: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """去重事件"""
        seen = set()
        unique = []
        for event in events:
            name = event.get('name', '').strip()
            if name and name not in seen:
                seen.add(name)
                unique.append(event)
        return unique

    def _deduplicate_relations(self, relations: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """去重因果关系"""
        seen = set()
        unique = []
        for rel in relations:
            key = (rel.get('cause', '').strip(), rel.get('effect', '').strip())
            if key[0] and key[1] and key not in seen:
                seen.add(key)
                unique.append(rel)
        return unique

    def _is_similar_string(self, s1: str, s2: str, threshold: float = 0.85) -> bool:
        """计算两个字符串的相似度"""
        if not s1 or not s2:
            return False
        # 对于故障代码，要求精确匹配
        if s1.startswith(('F', 'A')) and s2.startswith(('F', 'A')):
            return s1.upper() == s2.upper()
        return SequenceMatcher(None, s1.lower(), s2.lower()).ratio() >= threshold

    def _find_similar_event(self, event: Dict[str, Any], event_list: List[Dict[str, Any]]) -> Optional[Dict[str, Any]]:
        """在事件列表中查找相似的事件"""
        for existing_event in event_list:
            if self._is_similar_string(event.get('name', ''), existing_event.get('name', '')):
                return existing_event
        return None

    def _merge_events(self, event1: Dict[str, Any], event2: Dict[str, Any]) -> Dict[str, Any]:
        """合并两个事件，取更完整的信息"""
        merged = event1.copy()

        # 合并描述
        desc1 = event1.get('description', '')
        desc2 = event2.get('description', '')
        if len(desc2) > len(desc1):
            merged['description'] = desc2

        # 取更高的严重性
        severity_priority = {'CRITICAL': 4, 'MAJOR': 3, 'MINOR': 2, 'WARNING': 1}
        sev1 = event1.get('severity', 'MINOR')
        sev2 = event2.get('severity', 'MINOR')
        if severity_priority.get(sev2, 0) > severity_priority.get(sev1, 0):
            merged['severity'] = sev2

        # 取更高的概率
        prob1 = event1.get('probability', 0)
        prob2 = event2.get('probability', 0)
        merged['probability'] = max(prob1, prob2)

        # 保留组件信息
        if event2.get('component') and not event1.get('component'):
            merged['component'] = event2.get('component')

        # 保留故障代码
        if event2.get('faultCode') and not event1.get('faultCode'):
            merged['faultCode'] = event2.get('faultCode')

        return merged

    def _post_process_extracted_data(self, data: Dict[str, Any]) -> Dict[str, Any]:
        """对提取的数据进行后处理和验证"""
        events = data.get('events', [])
        causal_relations = data.get('causalRelations', [])

        # 验证并修复事件
        validated_events = []
        event_name_map = {}

        for event in events:
            # 确保必要字段存在
            name = event.get('name', '').strip()
            if not name:
                continue

            event_type = event.get('type', 'FAULT_CAUSE_EVENT')
            if event_type not in ['FAULT_CODE_EVENT', 'FAULT_SYMPTOM_EVENT', 'FAULT_CAUSE_EVENT']:
                event_type = 'FAULT_CAUSE_EVENT'
                event['type'] = event_type

            severity = event.get('severity', 'MINOR')
            if severity not in ['CRITICAL', 'MAJOR', 'MINOR', 'WARNING']:
                severity = 'MINOR'
                event['severity'] = severity

            probability = event.get('probability')
            if probability is None:
                event['probability'] = 0.15
            elif probability < 0 or probability > 1:
                event['probability'] = max(0, min(1, probability))

            if event.get('description') is None:
                event['description'] = ''

            validated_events.append(event)
            event_name_map[name] = event

        # 验证并修复因果关系
        validated_relations = []
        for rel in causal_relations:
            cause = rel.get('cause', '').strip()
            effect = rel.get('effect', '').strip()

            if not cause or not effect or cause == effect:
                continue

            gate_type = rel.get('gateType', 'OR')
            if gate_type not in ['AND', 'OR']:
                gate_type = 'OR'
                rel['gateType'] = gate_type

            if rel.get('description') is None:
                rel['description'] = f'{cause} 导致 {effect}'

            validated_relations.append(rel)

        return {
            'events': validated_events,
            'causalRelations': validated_relations,
            'componentAssociations': data.get('componentAssociations', []),
            'components': data.get('components', []),
            'equipmentType': data.get('equipmentType', 'general'),
            'faultModes': data.get('faultModes', [])
        }

    def _build_event_mapping(self, events: List[Dict[str, Any]]) -> Dict[str, Dict[str, Any]]:
        """建立事件名称到事件对象的映射（含模糊匹配）"""
        mapping = {}
        for event in events:
            name = event.get('name', '').strip()
            if name:
                mapping[name] = event
        return mapping

    def _resolve_relation_references(self,
                                     relations: List[Dict[str, Any]],
                                     event_mapping: Dict[str, Dict[str, Any]]) -> List[Dict[str, Any]]:
        """解析因果关系中的事件名称引用，处理可能的不匹配"""
        resolved = []
        for rel in relations:
            cause_name = rel.get('cause', '').strip()
            effect_name = rel.get('effect', '').strip()

            # 查找最匹配的事件
            best_cause = None
            best_cause_score = 0
            best_effect = None
            best_effect_score = 0

            for event_name, event in event_mapping.items():
                cause_score = SequenceMatcher(None, cause_name.lower(), event_name.lower()).ratio()
                effect_score = SequenceMatcher(None, effect_name.lower(), event_name.lower()).ratio()

                if cause_score > best_cause_score:
                    best_cause_score = cause_score
                    best_cause = event_name

                if effect_score > best_effect_score:
                    best_effect_score = effect_score
                    best_effect = event_name

            if best_cause and best_effect and best_cause_score > 0.6 and best_effect_score > 0.6:
                new_rel = rel.copy()
                new_rel['cause'] = best_cause
                new_rel['effect'] = best_effect
                resolved.append(new_rel)

        return resolved
