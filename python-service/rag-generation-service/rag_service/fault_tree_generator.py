import os
import json
import re
import uuid
from typing import Optional, Dict, Any, Tuple
import logging
from industrial_fta_common.fault_tree_schema import FaultTreeSchema

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class JSONParsingError(Exception):
    """JSON解析异常"""
    pass

class FaultTreeGenerator:
    def __init__(self):
        self.max_depth = int(os.getenv('FAULT_TREE_MAX_DEPTH', '10'))
        self.valid_event_types = {'TOP', 'INTERMEDIATE', 'BASIC'}
        self.valid_gate_types = {'AND', 'OR', 'XOR', 'NOT', 'VOTE'}
    
    def generate(self, llm_response: str, top_event: Optional[str] = None) -> FaultTreeSchema:
        """
        从大模型响应中生成故障树
        
        参数:
            llm_response: 大模型响应文本
            top_event: 预期的顶事件名称（用于fallback时）
            
        返回:
            FaultTreeSchema实例
        """
        try:
            json_str = self._extract_json_robust(llm_response)
            if not json_str:
                logger.warning("Failed to extract JSON from LLM response, using default fault tree")
                return self._get_default_fault_tree(top_event)
            
            tree_data = self._parse_json_robust(json_str)
            tree_data = self._normalize_tree_data(tree_data, top_event)
            
            fault_tree = self._build_fault_tree_robust(tree_data)
            return fault_tree
            
        except Exception as e:
            logger.error(f"Error generating fault tree: {str(e)}", exc_info=True)
            return self._get_default_fault_tree(top_event)
    
    def _extract_json_robust(self, text: str) -> Optional[str]:
        """
        健壮的JSON提取方法，使用多种策略
        
        参数:
            text: 包含JSON的文本
            
        返回:
            提取的JSON字符串，或None
        """
        if not text or not text.strip():
            return None
        
        strategies = [
            self._extract_json_exact,
            self._extract_json_markdown,
            self._extract_json_code_block,
            self._extract_json_between_braces,
        ]
        
        for strategy in strategies:
            try:
                json_str = strategy(text)
                if json_str and self._is_valid_json_syntax(json_str):
                    return json_str
            except Exception as e:
                logger.debug(f"Strategy {strategy.__name__} failed: {e}")
                continue
        
        logger.warning("All JSON extraction strategies failed")
        return None
    
    def _extract_json_exact(self, text: str) -> Optional[str]:
        """尝试将整个文本作为JSON"""
        text = text.strip()
        if text.startswith('{') and text.endswith('}'):
            return text
        return None
    
    def _extract_json_markdown(self, text: str) -> Optional[str]:
        """从Markdown代码块中提取JSON（```json ... ```）"""
        pattern = r'```json\s*([\s\S]*?)\s*```'
        match = re.search(pattern, text)
        if match:
            return match.group(1).strip()
        return None
    
    def _extract_json_code_block(self, text: str) -> Optional[str]:
        """从通用代码块中提取JSON（``` ... ```）"""
        pattern = r'```\s*([\s\S]*?)\s*```'
        match = re.search(pattern, text)
        if match:
            content = match.group(1).strip()
            if content.startswith('{') or content.startswith('['):
                return content
        return None
    
    def _extract_json_between_braces(self, text: str) -> Optional[str]:
        """提取最外层大括号之间的内容"""
        stack = []
        start_idx = None
        
        for i, char in enumerate(text):
            if char == '{':
                if not stack:
                    start_idx = i
                stack.append(char)
            elif char == '}':
                if stack:
                    stack.pop()
                    if not stack and start_idx is not None:
                        return text[start_idx:i+1]
        
        return None
    
    def _is_valid_json_syntax(self, json_str: str) -> bool:
        """快速检查JSON语法是否有效"""
        try:
            json.loads(json_str)
            return True
        except json.JSONDecodeError:
            return False
    
    def _parse_json_robust(self, json_str: str) -> Dict[str, Any]:
        """
        健壮的JSON解析，包含多种修复策略
        
        参数:
            json_str: JSON字符串
            
        返回:
            解析后的字典
            
        异常:
            JSONParsingError: 当所有解析策略都失败时
        """
        parse_strategies = [
            lambda s: json.loads(s),
            self._parse_with_json5,
            self._parse_with_simple_fixes,
        ]
        
        for strategy in parse_strategies:
            try:
                return strategy(json_str)
            except Exception as e:
                logger.debug(f"Parse strategy failed: {e}")
                continue
        
        raise JSONParsingError("All JSON parsing strategies failed")
    
    def _parse_with_json5(self, json_str: str) -> Dict[str, Any]:
        """尝试使用更宽松的解析"""
        json_str = self._fix_trailing_commas(json_str)
        json_str = self._fix_unquoted_keys(json_str)
        return json.loads(json_str)
    
    def _fix_trailing_commas(self, json_str: str) -> str:
        """移除尾随逗号"""
        json_str = re.sub(r',\s*([}\]])', r'\1', json_str)
        return json_str
    
    def _fix_unquoted_keys(self, json_str: str) -> str:
        """为未加引号的键添加引号"""
        json_str = re.sub(r'([{,])\s*(\w+)\s*:', r'\1"\2":', json_str)
        return json_str
    
    def _parse_with_simple_fixes(self, json_str: str) -> Dict[str, Any]:
        """尝试简单修复后解析"""
        fixes = [
            lambda s: s.replace("'", '"'),
            lambda s: re.sub(r'//.*$', '', s, flags=re.MULTILINE),
            lambda s: re.sub(r'/\*[\s\S]*?\*/', '', s),
        ]
        
        for fix in fixes:
            try:
                fixed = fix(json_str)
                return json.loads(fixed)
            except Exception:
                continue
        
        raise ValueError("Simple fixes also failed")
    
    def _normalize_tree_data(self, data: Dict[str, Any], top_event: Optional[str] = None) -> Dict[str, Any]:
        """规范化树数据，确保必要字段存在"""
        normalized = data.copy()
        
        if 'event_id' not in normalized or not normalized['event_id']:
            normalized['event_id'] = f"evt_{uuid.uuid4().hex[:8]}"
        
        if 'event_name' not in normalized or not normalized['event_name']:
            normalized['event_name'] = top_event or "Unknown Event"
        
        if 'event_type' not in normalized or normalized['event_type'] not in self.valid_event_types:
            normalized['event_type'] = 'INTERMEDIATE'
        
        if 'gate_type' not in normalized or normalized['gate_type'] not in self.valid_gate_types:
            normalized['gate_type'] = 'OR' if normalized['event_type'] != 'BASIC' else None
        
        if 'children' not in normalized or not isinstance(normalized['children'], list):
            normalized['children'] = []
        
        normalized['children'] = [
            self._normalize_tree_data(child, None) 
            for child in normalized['children']
        ]
        
        return normalized
    
    def _build_fault_tree_robust(self, data: Dict[str, Any], depth: int = 0) -> FaultTreeSchema:
        """递归构建故障树，包含深度限制"""
        if depth > self.max_depth:
            logger.warning(f"Max depth {self.max_depth} reached, truncating tree")
            return FaultTreeSchema(
                event_id=f"evt_trunc_{uuid.uuid4().hex[:8]}",
                event_name="[Truncated]",
                event_type="BASIC",
                children=[]
            )
        
        fault_tree = FaultTreeSchema(
            event_id=data.get('event_id', f"evt_{uuid.uuid4().hex[:8]}"),
            event_name=data.get('event_name', 'Unknown Event'),
            event_type=data.get('event_type', 'INTERMEDIATE'),
            gate_type=data.get('gate_type'),
            children=[],
            source_evidence=data.get('source_evidence', ''),
            equipment_type=data.get('equipment_type', 'unknown')
        )
        
        children = data.get('children', [])
        if isinstance(children, list):
            for child_data in children:
                if isinstance(child_data, dict):
                    child = self._build_fault_tree_robust(child_data, depth + 1)
                    fault_tree.children.append(child)
        
        return fault_tree
    
    def _get_default_fault_tree(self, top_event: Optional[str] = None) -> FaultTreeSchema:
        """返回默认故障树"""
        event_name = top_event or "系统故障"
        
        return FaultTreeSchema(
            event_id=f"evt_default_{uuid.uuid4().hex[:8]}",
            event_name=event_name,
            event_type="TOP",
            gate_type="OR",
            children=[
                FaultTreeSchema(
                    event_id=f"evt_default_{uuid.uuid4().hex[:8]}",
                    event_name="电气系统问题",
                    event_type="INTERMEDIATE",
                    gate_type="OR",
                    children=[
                        FaultTreeSchema(
                            event_id=f"evt_default_{uuid.uuid4().hex[:8]}",
                            event_name="电源供应异常",
                            event_type="BASIC",
                            children=[]
                        ),
                        FaultTreeSchema(
                            event_id=f"evt_default_{uuid.uuid4().hex[:8]}",
                            event_name="电路连接故障",
                            event_type="BASIC",
                            children=[]
                        )
                    ]
                ),
                FaultTreeSchema(
                    event_id=f"evt_default_{uuid.uuid4().hex[:8]}",
                    event_name="机械系统问题",
                    event_type="INTERMEDIATE",
                    gate_type="OR",
                    children=[
                        FaultTreeSchema(
                            event_id=f"evt_default_{uuid.uuid4().hex[:8]}",
                            event_name="部件磨损",
                            event_type="BASIC",
                            children=[]
                        ),
                        FaultTreeSchema(
                            event_id=f"evt_default_{uuid.uuid4().hex[:8]}",
                            event_name="润滑不足",
                            event_type="BASIC",
                            children=[]
                        )
                    ]
                )
            ]
        )

