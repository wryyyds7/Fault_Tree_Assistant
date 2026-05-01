# 简化的LLM客户端，替代被删除的rag_generation_service中的版本
import os
import requests
from dotenv import load_dotenv

load_dotenv()

class LLMClient:
    def __init__(self):
        self.api_key = os.getenv('BAILIAN_API_KEY', 'sk-667d6c0a4b134de1ba04a8b86c98a3a3')
        self.api_url = os.getenv('BAILIAN_API_URL', 'https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions')
        self.model = os.getenv('LLM_MODEL', 'qwen-max')
        self.temperature = float(os.getenv('LLM_TEMPERATURE', '0.7'))
        self.max_tokens = int(os.getenv('LLM_MAX_TOKENS', '2000'))
        self.timeout = int(os.getenv('LLM_TIMEOUT', '180'))

    def chat(self, messages, temperature=None, max_tokens=None):
        """发送聊天请求到LLM"""
        headers = {
            'Authorization': f'Bearer {self.api_key}',
            'Content-Type': 'application/json'
        }

        payload = {
            'model': self.model,
            'messages': messages,
            'temperature': temperature or self.temperature,
            'max_tokens': max_tokens or self.max_tokens
        }

        try:
            response = requests.post(
                self.api_url,
                headers=headers,
                json=payload,
                timeout=self.timeout
            )
            response.raise_for_status()
            result = response.json()

            if 'choices' in result and len(result['choices']) > 0:
                return result['choices'][0]['message']['content']
            return "抱歉，我无法生成回复。"

        except requests.exceptions.Timeout:
            return "抱歉，请求超时，请稍后重试。"
        except requests.exceptions.RequestException as e:
            return f"抱歉，发生了错误：{str(e)}"
        except Exception as e:
            return f"抱歉，发生了未知错误：{str(e)}"

    def chat_stream(self, messages, temperature=None, max_tokens=None):
        """流式发送聊天请求到LLM，返回生成器"""
        headers = {
            'Authorization': f'Bearer {self.api_key}',
            'Content-Type': 'application/json'
        }

        payload = {
            'model': self.model,
            'messages': messages,
            'temperature': temperature or self.temperature,
            'max_tokens': max_tokens or self.max_tokens,
            'stream': True
        }

        try:
            response = requests.post(
                self.api_url,
                headers=headers,
                json=payload,
                timeout=self.timeout,
                stream=True
            )
            response.raise_for_status()

            for line in response.iter_lines(decode_unicode=True):
                if line.startswith('data: '):
                    data = line[6:]
                    if data == '[DONE]':
                        break
                    try:
                        import json
                        chunk = json.loads(data)
                        if 'choices' in chunk and len(chunk['choices']) > 0:
                            delta = chunk['choices'][0].get('delta', {})
                            if 'content' in delta:
                                yield delta['content']
                    except json.JSONDecodeError:
                        continue

        except requests.exceptions.Timeout:
            yield "抱歉，请求超时，请稍后重试。"
        except requests.exceptions.RequestException as e:
            yield f"抱歉，发生了错误：{str(e)}"
        except Exception as e:
            yield f"抱歉，发生了未知错误：{str(e)}"

    def chat_with_history(self, user_message, system_prompt=None, conversation_history=None):
        """带历史记录的聊天"""
        messages = []

        if system_prompt:
            messages.append({'role': 'system', 'content': system_prompt})

        if conversation_history:
            for msg in conversation_history:
                if isinstance(msg, dict) and 'role' in msg and 'content' in msg:
                    messages.append({'role': msg['role'], 'content': msg['content']})
                elif isinstance(msg, dict) and 'message' in msg:
                    content = msg['message'].get('content', '') if isinstance(msg['message'], dict) else str(msg['message'])
                    role = msg['message'].get('role', 'user') if isinstance(msg['message'], dict) else 'user'
                    messages.append({'role': role, 'content': content})

        messages.append({'role': 'user', 'content': user_message})

        return self.chat(messages)
