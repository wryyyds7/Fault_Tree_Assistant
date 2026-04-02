import os
import requests
import json
import uuid
from typing import Optional, Dict, Any
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class LLMClientError(Exception):
    """LLM客户端异常基类"""
    pass

class LLMConfigurationError(LLMClientError):
    """LLM配置异常"""
    pass

class LLMServiceError(LLMClientError):
    """LLM服务异常"""
    pass

class LLMClient:
    def __init__(self, fallback_enabled: bool = True):
        self.api_key = os.getenv('BAILIAN_API_KEY')
        self.api_url = os.getenv('BAILIAN_API_URL')
        self.model = os.getenv('LLM_MODEL', 'qwen-max')
        self.temperature = float(os.getenv('LLM_TEMPERATURE', '0.7'))
        self.max_tokens = int(os.getenv('LLM_MAX_TOKENS', '2000'))
        self.timeout = int(os.getenv('LLM_TIMEOUT', '30'))
        self.fallback_enabled = fallback_enabled
        self.use_mock = os.getenv('USE_MOCK_LLM', 'false').lower() == 'true'
        
        self._validate_config()
        
        self.headers = {
            'Content-Type': 'application/json',
            'Authorization': f'Bearer {self.api_key}'
        }
        
        logger.info(f"LLMClient initialized. Model: {self.model}, Mock: {self.use_mock}")
    
    def _validate_config(self):
        """验证配置"""
        if not self.use_mock:
            if not self.api_key:
                raise LLMConfigurationError("BAILIAN_API_KEY environment variable is not set")
            if not self.api_url:
                raise LLMConfigurationError("BAILIAN_API_URL environment variable is not set")
    
    def generate(self, prompt: str, top_event: Optional[str] = None) -> str:
        """
        调用大模型生成故障树
        
        参数:
            prompt: 提示词
            top_event: 顶事件名称（用于生成fallback时的占位符）
            
        返回:
            模型生成的文本响应
            
        异常:
            LLMServiceError: 当API调用失败且fallback禁用时抛出
        """
        if self.use_mock:
            logger.info("Using mock LLM response")
            return self._generate_mock_response(top_event)
        
        try:
            return self._call_llm_api(prompt)
        except Exception as e:
            logger.error(f"LLM API call failed: {str(e)}", exc_info=True)
            if self.fallback_enabled:
                logger.warning("Using fallback response due to LLM API failure")
                return self._generate_fallback_response(top_event)
            else:
                raise LLMServiceError(f"LLM API call failed: {str(e)}") from e
    
    def _call_llm_api(self, prompt: str) -> str:
        """调用LLM API"""
        data = {
            "model": self.model,
            "messages": [
                {
                    "role": "user",
                    "content": prompt
                }
            ],
            "temperature": self.temperature,
            "max_tokens": self.max_tokens
        }
        
        logger.debug(f"Sending request to LLM API at {self.api_url}")
        
        response = requests.post(
            self.api_url,
            headers=self.headers,
            data=json.dumps(data, ensure_ascii=False),
            timeout=self.timeout
        )
        
        if response.status_code != 200:
            error_msg = f"LLM API returned status {response.status_code}: {response.text}"
            logger.error(error_msg)
            raise LLMServiceError(error_msg)
        
        result = response.json()
        
        if 'choices' not in result or len(result['choices']) == 0:
            error_msg = "LLM API response has no choices"
            logger.error(error_msg)
            raise LLMServiceError(error_msg)
        
        content = result['choices'][0]['message']['content']
        logger.info("Successfully received response from LLM API")
        return content
    
    def _generate_fallback_response(self, top_event: Optional[str] = None) -> str:
        """生成fallback响应"""
        event_name = top_event or "系统故障"
        
        fallback_data = {
            "event_id": f"evt_{uuid.uuid4().hex[:8]}",
            "event_name": event_name,
            "event_type": "TOP",
            "gate_type": "OR",
            "children": [
                {
                    "event_id": f"evt_{uuid.uuid4().hex[:8]}",
                    "event_name": "电气系统问题",
                    "event_type": "INTERMEDIATE",
                    "gate_type": "OR",
                    "children": [
                        {
                            "event_id": f"evt_{uuid.uuid4().hex[:8]}",
                            "event_name": "电源供应异常",
                            "event_type": "BASIC",
                            "children": []
                        },
                        {
                            "event_id": f"evt_{uuid.uuid4().hex[:8]}",
                            "event_name": "电路连接故障",
                            "event_type": "BASIC",
                            "children": []
                        }
                    ]
                },
                {
                    "event_id": f"evt_{uuid.uuid4().hex[:8]}",
                    "event_name": "机械系统问题",
                    "event_type": "INTERMEDIATE",
                    "gate_type": "OR",
                    "children": [
                        {
                            "event_id": f"evt_{uuid.uuid4().hex[:8]}",
                            "event_name": "部件磨损",
                            "event_type": "BASIC",
                            "children": []
                        },
                        {
                            "event_id": f"evt_{uuid.uuid4().hex[:8]}",
                            "event_name": "润滑不足",
                            "event_type": "BASIC",
                            "children": []
                        }
                    ]
                }
            ],
            "_metadata": {
                "is_fallback": True,
                "reason": "LLM service unavailable"
            }
        }
        
        return json.dumps(fallback_data, ensure_ascii=False, indent=2)
    
    def _generate_mock_response(self, top_event: Optional[str] = None) -> str:
        """生成模拟响应（用于开发测试）"""
        event_name = top_event or "电机过热"
        
        mock_data = {
            "event_id": "evt_mock_001",
            "event_name": event_name,
            "event_type": "TOP",
            "gate_type": "OR",
            "children": [
                {
                    "event_id": "evt_mock_002",
                    "event_name": "电源问题",
                    "event_type": "INTERMEDIATE",
                    "gate_type": "OR",
                    "children": [
                        {
                            "event_id": "evt_mock_003",
                            "event_name": "电压不稳定",
                            "event_type": "BASIC",
                            "children": []
                        },
                        {
                            "event_id": "evt_mock_004",
                            "event_name": "缺相",
                            "event_type": "BASIC",
                            "children": []
                        },
                        {
                            "event_id": "evt_mock_005",
                            "event_name": "过载",
                            "event_type": "BASIC",
                            "children": []
                        }
                    ]
                },
                {
                    "event_id": "evt_mock_006",
                    "event_name": "轴承故障",
                    "event_type": "INTERMEDIATE",
                    "gate_type": "OR",
                    "children": [
                        {
                            "event_id": "evt_mock_007",
                            "event_name": "润滑不足",
                            "event_type": "BASIC",
                            "children": []
                        },
                        {
                            "event_id": "evt_mock_008",
                            "event_name": "磨损",
                            "event_type": "BASIC",
                            "children": []
                        },
                        {
                            "event_id": "evt_mock_009",
                            "event_name": "安装不当",
                            "event_type": "BASIC",
                            "children": []
                        }
                    ]
                },
                {
                    "event_id": "evt_mock_010",
                    "event_name": "绕组故障",
                    "event_type": "INTERMEDIATE",
                    "gate_type": "OR",
                    "children": [
                        {
                            "event_id": "evt_mock_011",
                            "event_name": "绝缘老化",
                            "event_type": "BASIC",
                            "children": []
                        },
                        {
                            "event_id": "evt_mock_012",
                            "event_name": "短路",
                            "event_type": "BASIC",
                            "children": []
                        }
                    ]
                }
            ],
            "_metadata": {
                "is_mock": True,
                "model": self.model
            }
        }
        
        return json.dumps(mock_data, ensure_ascii=False, indent=2)

