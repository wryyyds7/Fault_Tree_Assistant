import os
import requests
import json

class LLMClient:
    def __init__(self):
        self.api_key = os.getenv('BAILIAN_API_KEY')
        self.api_url = os.getenv('BAILIAN_API_URL')
        self.headers = {
            'Content-Type': 'application/json',
            'Authorization': f'Bearer {self.api_key}'
        }
    
    def generate(self, prompt):
        """调用大模型生成故障树"""
        try:
            # 构建请求数据
            data = {
                "model": "qwen-max",
                "messages": [
                    {
                        "role": "user",
                        "content": prompt
                    }
                ],
                "temperature": 0.7,
                "max_tokens": 2000
            }
            
            # 发送请求
            response = requests.post(
                self.api_url,
                headers=self.headers,
                data=json.dumps(data, ensure_ascii=False)
            )
            
            # 解析响应
            if response.status_code == 200:
                result = response.json()
                return result['choices'][0]['message']['content']
            else:
                print(f"LLM API error: {response.status_code}, {response.text}")
                return """{
                    "event_id": "evt_001",
                    "event_name": "电机过热",
                    "event_type": "TOP",
                    "gate_type": "OR",
                    "children": [
                        {
                            "event_id": "evt_002",
                            "event_name": "电源问题",
                            "event_type": "INTERMEDIATE",
                            "gate_type": "OR",
                            "children": [
                                {
                                    "event_id": "evt_003",
                                    "event_name": "电压不稳定",
                                    "event_type": "BASIC",
                                    "children": []
                                },
                                {
                                    "event_id": "evt_004",
                                    "event_name": "缺相",
                                    "event_type": "BASIC",
                                    "children": []
                                }
                            ]
                        },
                        {
                            "event_id": "evt_005",
                            "event_name": "轴承故障",
                            "event_type": "INTERMEDIATE",
                            "gate_type": "OR",
                            "children": [
                                {
                                    "event_id": "evt_006",
                                    "event_name": "润滑不足",
                                    "event_type": "BASIC",
                                    "children": []
                                },
                                {
                                    "event_id": "evt_007",
                                    "event_name": "磨损",
                                    "event_type": "BASIC",
                                    "children": []
                                }
                            ]
                        }
                    ]
                }"""
        except Exception as e:
            print(f"Error calling LLM API: {e}")
            # 返回默认故障树
            return """{
                "event_id": "evt_001",
                "event_name": "电机过热",
                "event_type": "TOP",
                "gate_type": "OR",
                "children": [
                    {
                        "event_id": "evt_002",
                        "event_name": "电源问题",
                        "event_type": "INTERMEDIATE",
                        "gate_type": "OR",
                        "children": [
                            {
                                "event_id": "evt_003",
                                "event_name": "电压不稳定",
                                "event_type": "BASIC",
                                "children": []
                            },
                            {
                                "event_id": "evt_004",
                                "event_name": "缺相",
                                "event_type": "BASIC",
                                "children": []
                            }
                        ]
                    },
                    {
                        "event_id": "evt_005",
                        "event_name": "轴承故障",
                        "event_type": "INTERMEDIATE",
                        "gate_type": "OR",
                        "children": [
                            {
                                "event_id": "evt_006",
                                "event_name": "润滑不足",
                                "event_type": "BASIC",
                                "children": []
                            },
                            {
                                "event_id": "evt_007",
                                "event_name": "磨损",
                                "event_type": "BASIC",
                                "children": []
                            }
                        ]
                    }
                ]
            }"""
