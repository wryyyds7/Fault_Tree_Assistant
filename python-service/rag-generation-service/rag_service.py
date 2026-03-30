import pika
import json
import os
from dotenv import load_dotenv
from industrial_fta_common.message_utils import MessageUtils
from industrial_fta_common.llm_prompt_templates import LLMPromptTemplates
from industrial_fta_common.fault_tree_schema import FaultTreeSchema

from rag_service.llm_client import LLMClient
from rag_service.vector_retriever import VectorRetriever
from rag_service.fault_tree_generator import FaultTreeGenerator

# 加载环境变量
load_dotenv()

class RAGService:
    def __init__(self):
        # 初始化组件
        self.llm_client = LLMClient()
        self.vector_retriever = VectorRetriever()
        self.fault_tree_generator = FaultTreeGenerator()
        
        # 初始化RabbitMQ连接
        self.connection = pika.BlockingConnection(
            pika.ConnectionParameters(
                host=os.getenv('RABBITMQ_HOST'),
                port=int(os.getenv('RABBITMQ_PORT')),
                credentials=pika.PlainCredentials(
                    os.getenv('RABBITMQ_USER'),
                    os.getenv('RABBITMQ_PASSWORD')
                )
            )
        )
        self.channel = self.connection.channel()
        
        # 声明队列
        self.channel.queue_declare(queue='queue.rag.generate')
        self.channel.exchange_declare(exchange='exchange.rag', exchange_type='topic')
        
        # 绑定队列
        self.channel.queue_bind(
            exchange='exchange.document',
            queue='queue.rag.generate',
            routing_key='document.parsed'
        )
    
    def start(self):
        print("RAG Generation Service started")
        # 开始消费消息
        self.channel.basic_consume(
            queue='queue.rag.generate',
            on_message_callback=self.handle_message,
            auto_ack=True
        )
        
        print("Waiting for messages...")
        self.channel.start_consuming()
    
    def handle_message(self, ch, method, properties, body):
        try:
            # 解析消息
            message = json.loads(body)
            print(f"Received message: {message}")
            
            # 提取消息内容
            task_id = message.get('taskId', f"task_{os.urandom(8).hex()}")
            top_event = message.get('topEvent')
            doc_ids = message.get('docIds', [])
            template = message.get('template', {})
            
            # 处理文档内容
            document_content = self._get_document_content(doc_ids)
            
            # 向量检索相关段落
            relevant_paragraphs = self.vector_retriever.retrieve_relevant_paragraphs(
                query=top_event,
                documents=document_content
            )
            
            # 构建提示词
            prompt = LLMPromptTemplates.FAULT_TREE_GENERATION.format(
                top_event=top_event,
                industrial_knowledge='\n'.join(relevant_paragraphs),
                kg_template=json.dumps(template)
            )
            
            # 调用大模型
            llm_response = self.llm_client.generate(prompt)
            
            # 解析大模型输出，生成故障树
            fault_tree = self.fault_tree_generator.generate(llm_response)
            
            # 发布结果
            self._publish_result(task_id, fault_tree)
            
        except Exception as e:
            print(f"Error handling message: {e}")
    
    def _get_document_content(self, doc_ids):
        """从document-ingest-service获取文档内容"""
        # 这里应该调用document-ingest-service的API获取文档内容
        # 暂时返回模拟数据
        return [
            "电机过热可能是由电源问题、轴承故障或绕组故障引起的。",
            "电源问题包括电压不稳定、缺相、过载等。",
            "轴承故障可能是由于润滑不足、磨损、安装不当等原因。",
            "绕组故障可能是由于绝缘老化、短路、过载等原因。"
        ]
    
    def _publish_result(self, task_id, fault_tree):
        """发布故障树生成结果"""
        # 转换为字典
        fault_tree_dict = fault_tree.dict()
        
        # 构建消息
        message = {
            'taskId': task_id,
            'faultTree': fault_tree_dict,
            'status': 'completed'
        }
        
        # 发布消息
        self.channel.basic_publish(
            exchange='exchange.rag',
            routing_key='rag.generated',
            body=json.dumps(message, ensure_ascii=False)
        )
        
        print(f"Published result for task {task_id}")

if __name__ == "__main__":
    service = RAGService()
    service.start()
