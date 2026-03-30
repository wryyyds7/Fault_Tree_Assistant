import json
from industrial_fta_common.fault_tree_schema import FaultTreeSchema
import re

class FaultTreeGenerator:
    def generate(self, llm_response):
        """从大模型响应中生成故障树"""
        try:
            # 提取JSON部分
            json_str = self._extract_json(llm_response)
            if not json_str:
                # 如果没有提取到JSON，使用默认故障树
                return self._get_default_fault_tree()
            
            # 解析JSON
            tree_data = json.loads(json_str)
            
            # 转换为FaultTreeSchema
            fault_tree = self._build_fault_tree(tree_data)
            return fault_tree
        except Exception as e:
            print(f"Error generating fault tree: {e}")
            # 返回默认故障树
            return self._get_default_fault_tree()
    
    def _extract_json(self, text):
        """从文本中提取JSON部分"""
        # 尝试匹配JSON格式
        json_pattern = r'\{[\s\S]*\}'
        match = re.search(json_pattern, text)
        if match:
            return match.group(0)
        return ""
    
    def _build_fault_tree(self, data):
        """递归构建故障树"""
        # 创建当前节点
        fault_tree = FaultTreeSchema(
            event_id=data.get('event_id', f"evt_{hash(data.get('event_name', 'unknown')) % 10000}"),
            event_name=data.get('event_name', 'Unknown Event'),
            event_type=data.get('event_type', 'INTERMEDIATE'),
            gate_type=data.get('gate_type'),
            children=[],
            source_evidence=data.get('source_evidence', ''),
            equipment_type=data.get('equipment_type', 'unknown')
        )
        
        # 递归处理子节点
        if 'children' in data:
            for child_data in data['children']:
                child = self._build_fault_tree(child_data)
                fault_tree.children.append(child)
        
        return fault_tree
    
    def _get_default_fault_tree(self):
        """返回默认故障树"""
        return FaultTreeSchema(
            event_id="evt_001",
            event_name="默认故障",
            event_type="TOP",
            gate_type="OR",
            children=[
                FaultTreeSchema(
                    event_id="evt_002",
                    event_name="中间事件1",
                    event_type="INTERMEDIATE",
                    gate_type="OR",
                    children=[
                        FaultTreeSchema(
                            event_id="evt_003",
                            event_name="底事件1",
                            event_type="BASIC",
                            children=[]
                        ),
                        FaultTreeSchema(
                            event_id="evt_004",
                            event_name="底事件2",
                            event_type="BASIC",
                            children=[]
                        )
                    ]
                ),
                FaultTreeSchema(
                    event_id="evt_005",
                    event_name="中间事件2",
                    event_type="INTERMEDIATE",
                    gate_type="AND",
                    children=[
                        FaultTreeSchema(
                            event_id="evt_006",
                            event_name="底事件3",
                            event_type="BASIC",
                            children=[]
                        ),
                        FaultTreeSchema(
                            event_id="evt_007",
                            event_name="底事件4",
                            event_type="BASIC",
                            children=[]
                        )
                    ]
                )
            ]
        )
