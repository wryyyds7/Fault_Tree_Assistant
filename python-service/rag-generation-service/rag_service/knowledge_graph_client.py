import os
import requests
from typing import Dict, Any, Optional


class KnowledgeGraphClient:
    """
    知识图谱服务客户端
    
    用于调用 Java knowledge-graph-service 的 API
    """
    
    def __init__(self, base_url: Optional[str] = None):
        """
        初始化知识图谱客户端
        
        参数:
            base_url: 知识图谱服务的基础 URL，如果不提供则从环境变量读取
        """
        if base_url:
            self.base_url = base_url
        else:
            self.base_url = os.getenv(
                'KNOWLEDGE_GRAPH_SERVICE_URL',
                'http://knowledge-graph-service:8082'
            )
        self.timeout = float(os.getenv('KG_CLIENT_TIMEOUT', '10.0'))
    
    def query_template(
        self,
        top_event: str,
        equipment_type: str = 'general'
    ) -> Dict[str, Any]:
        """
        从知识图谱查询故障树模板
        
        参数:
            top_event: 顶事件名称
            equipment_type: 设备类型
            
        返回:
            知识图谱模板，包含:
            - templateId: 模板ID
            - structure: 模板结构
            - typicalFaultModes: 典型故障模式
            - logicGatePreferences: 逻辑门偏好
            - causalRelationships: 因果关系
        """
        try:
            url = f"{self.base_url}/api/v1/kg/query-template"
            payload = {
                'topEvent': top_event,
                'equipmentType': equipment_type
            }
            
            response = requests.post(
                url,
                json=payload,
                timeout=self.timeout
            )
            
            if response.status_code == 200:
                template = response.json()
                return self._enrich_template(template, top_event, equipment_type)
            else:
                print(f"Knowledge graph service returned status {response.status_code}")
                return self._get_default_template(top_event, equipment_type)
                
        except requests.exceptions.RequestException as e:
            print(f"Error querying knowledge graph: {e}")
            return self._get_default_template(top_event, equipment_type)
    
    def enrich_knowledge(
        self,
        cause: str,
        effect: str,
        equipment_type: str,
        gate_type: str = 'OR'
    ) -> bool:
        """
        向知识图谱添加因果关系
        
        参数:
            cause: 原因事件
            effect: 结果事件
            equipment_type: 设备类型
            gate_type: 逻辑门类型
            
        返回:
            是否成功
        """
        try:
            url = f"{self.base_url}/api/v1/kg/enrich"
            payload = {
                'cause': cause,
                'effect': effect,
                'equipmentType': equipment_type,
                'gateType': gate_type
            }
            
            response = requests.put(
                url,
                json=payload,
                timeout=self.timeout
            )
            
            return response.status_code == 200
            
        except requests.exceptions.RequestException as e:
            print(f"Error enriching knowledge graph: {e}")
            return False
    
    def initialize_ontology(self) -> bool:
        """
        初始化知识图谱本体
        
        返回:
            是否成功
        """
        try:
            url = f"{self.base_url}/api/v1/kg/initialize"
            response = requests.post(
                url,
                timeout=self.timeout
            )
            return response.status_code == 200
        except requests.exceptions.RequestException as e:
            print(f"Error initializing ontology: {e}")
            return False
    
    def _enrich_template(
        self,
        template: Dict[str, Any],
        top_event: str,
        equipment_type: str
    ) -> Dict[str, Any]:
        """
        丰富知识图谱模板，添加默认的领域约束
        
        参数:
            template: 原始模板
            top_event: 顶事件
            equipment_type: 设备类型
            
        返回:
            丰富后的模板
        """
        enriched = template.copy()
        
        # 添加典型故障模式（基于设备类型）
        enriched['typicalFaultModes'] = self._get_typical_fault_modes(equipment_type)
        
        # 添加逻辑门偏好
        enriched['logicGatePreferences'] = self._get_logic_gate_preferences(equipment_type)
        
        # 添加因果关系
        enriched['causalRelationships'] = self._get_causal_relationships(equipment_type)
        
        return enriched
    
    def _get_typical_fault_modes(self, equipment_type: str) -> list:
        """获取典型故障模式"""
        defaults = {
            'induction_motor': ['温度过高', '振动异常', '转速异常', '噪音过大'],
            'hydraulic_pump': ['压力不足', '流量异常', '泄漏', '噪音过大'],
            'sensor': ['信号异常', '无输出', '精度下降', '漂移'],
            'general': ['故障', '异常', '损坏', '失效']
        }
        return defaults.get(equipment_type.lower(), defaults['general'])
    
    def _get_logic_gate_preferences(self, equipment_type: str) -> Dict[str, str]:
        """获取逻辑门偏好"""
        defaults = {
            'induction_motor': {
                '电源问题': 'OR',
                '轴承故障': 'OR',
                '绕组故障': 'OR',
                '冷却系统故障': 'OR'
            },
            'hydraulic_pump': {
                '泵磨损': 'OR',
                '油液污染': 'OR',
                '密封失效': 'OR'
            },
            'sensor': {
                '电源故障': 'OR',
                '传感器损坏': 'OR',
                '连接松动': 'OR'
            },
            'general': {}
        }
        return defaults.get(equipment_type.lower(), defaults['general'])
    
    def _get_causal_relationships(self, equipment_type: str) -> list:
        """获取因果关系"""
        defaults = {
            'induction_motor': [
                {'cause': '电源电压不稳定', 'effect': '电机过热'},
                {'cause': '轴承润滑不足', 'effect': '轴承磨损'},
                {'cause': '轴承磨损', 'effect': '电机过热'},
                {'cause': '绕组绝缘老化', 'effect': '绕组故障'}
            ],
            'hydraulic_pump': [
                {'cause': '油液污染', 'effect': '泵磨损'},
                {'cause': '泵磨损', 'effect': '压力不足'},
                {'cause': '密封失效', 'effect': '泄漏'}
            ],
            'sensor': [
                {'cause': '电源故障', 'effect': '信号异常'},
                {'cause': '连接松动', 'effect': '信号异常'}
            ],
            'general': []
        }
        return defaults.get(equipment_type.lower(), defaults['general'])
    
    def _get_default_template(self, top_event: str, equipment_type: str) -> Dict[str, Any]:
        """获取默认模板（当知识图谱服务不可用时）"""
        return {
            'templateId': f'tmpl_default_{equipment_type}',
            'structure': {
                'event': top_event,
                'gate': 'OR',
                'children': []
            },
            'typicalFaultModes': self._get_typical_fault_modes(equipment_type),
            'logicGatePreferences': self._get_logic_gate_preferences(equipment_type),
            'causalRelationships': self._get_causal_relationships(equipment_type)
        }
