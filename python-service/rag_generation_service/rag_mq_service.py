import pika
import json
import os
import uuid
from dotenv import load_dotenv

from rag_service.llm_client import LLMClient
from rag_service.vector_retriever import VectorRetriever
from rag_service.fault_tree_generator import FaultTreeGenerator
from rag_service.hybrid_generator import HybridFaultTreeGenerator
from rag_service.knowledge_graph_client import KnowledgeGraphClient

load_dotenv()


class RAGServiceWithMQ:
    """
    基于RabbitMQ的RAG故障树生成服务

    功能：
    1. 消费RabbitMQ消息队列中的生成请求
    2. 使用HybridGenerator（知识驱动+数据驱动）生成故障树
    3. 将结果发布到结果队列
    4. 同时支持HTTP API调用（通过rag_api.py）
    """

    def __init__(self):
        # 初始化组件
        self.llm_client = LLMClient()
        self.vector_retriever = VectorRetriever()
        self.knowledge_graph_client = KnowledgeGraphClient()
        self.hybrid_generator = HybridFaultTreeGenerator(
            knowledge_graph_client=self.knowledge_graph_client,
            vector_retriever=self.vector_retriever,
            llm_client=self.llm_client
        )

        # 初始化RabbitMQ连接
        self._init_rabbitmq()

    def _init_rabbitmq(self):
        """初始化RabbitMQ连接"""
        self.connection = pika.BlockingConnection(
            pika.ConnectionParameters(
                host=os.getenv('RABBITMQ_HOST', 'localhost'),
                port=int(os.getenv('RABBITMQ_PORT', 5672)),
                credentials=pika.PlainCredentials(
                    os.getenv('RABBITMQ_USER', 'guest'),
                    os.getenv('RABBITMQ_PASSWORD', 'guest')
                )
            )
        )
        self.channel = self.connection.channel()

        # 声明队列
        self.channel.queue_declare(queue='queue.rag.generate', durable=True)
        self.channel.queue_declare(queue='queue.rag.result', durable=True)

        # 声明交换机
        self.channel.exchange_declare(exchange='exchange.rag', exchange_type='topic', durable=True)

        # 绑定队列到交换机
        self.channel.queue_bind(
            exchange='exchange.document',
            queue='queue.rag.generate',
            routing_key='document.parsed'
        )

        print("RabbitMQ connection initialized")

    def start(self):
        """启动服务，开始消费消息"""
        print("RAG Generation Service (MQ Mode) started")
        print(f"Listening on queue: queue.rag.generate")

        # 设置预取数量，避免处理过载
        self.channel.basic_qos(prefetch_count=1)

        # 开始消费消息
        self.channel.basic_consume(
            queue='queue.rag.generate',
            on_message_callback=self._handle_message,
            auto_ack=False  # 手动确认
        )

        print("Waiting for messages...")
        try:
            self.channel.start_consuming()
        except KeyboardInterrupt:
            print("Stopping service...")
            self.channel.stop_consuming()
        finally:
            self.connection.close()

    def _handle_message(self, ch, method, properties, body):
        """
        处理接收到的消息

        消息格式：
        {
            "taskId": "task_xxx",
            "topEvent": "电机过热停机",
            "docIds": ["doc1", "doc2"],
            "template": {},
            "equipmentType": "motor",
            "userPreferences": "优先考虑轴承故障"
        }
        """
        try:
            # 解析消息
            message = json.loads(body)
            print(f"[MQ] Received message: taskId={message.get('taskId')}")

            task_id = message.get('taskId', f"task_{uuid.uuid4().hex[:16]}")
            top_event = message.get('topEvent')
            doc_ids = message.get('docIds', [])
            template = message.get('template', {})
            equipment_type = message.get('equipmentType', 'general')
            user_preferences = message.get('userPreferences')

            # 调用混合生成器生成故障树
            fault_tree, statistics = self.hybrid_generator.generate(
                top_event=top_event,
                doc_ids=doc_ids,
                knowledge_template=template,
                user_preferences=user_preferences
            )

            # 转换为字典
            fault_tree_dict = fault_tree.dict() if hasattr(fault_tree, 'dict') else fault_tree

            # 驼峰命名转换
            from rag_api import RagApiConverter
            fault_tree_dict = RagApiConverter.convert_to_camel_case(fault_tree_dict)

            # 填充溯源信息
            RagApiConverter.populate_source_details(fault_tree_dict, [])

            # 构建结果消息
            result = {
                'taskId': task_id,
                'status': 'completed',
                'faultTree': fault_tree_dict,
                'fusionStatistics': statistics,
                'equipmentType': equipment_type,
                'generatedAt': self._get_timestamp()
            }

            # 发布结果到结果队列
            self._publish_result(task_id, result)

            # 手动确认消息
            ch.basic_ack(delivery_tag=method.delivery_tag)

            print(f"[MQ] Task completed: taskId={task_id}")

        except Exception as e:
            print(f"[MQ] Error handling message: {e}")
            # 拒绝消息，重新入队
            ch.basic_nack(delivery_tag=method.delivery_tag, requeue=True)

    def _publish_result(self, task_id: str, result: dict):
        """发布故障树生成结果到消息队列"""
        try:
            message = json.dumps(result, ensure_ascii=False)

            self.channel.basic_publish(
                exchange='exchange.rag',
                routing_key='rag.generated',
                body=message,
                properties=pika.BasicProperties(
                    delivery_mode=2,  # 持久化消息
                    content_type='application/json'
                )
            )

            print(f"[MQ] Published result for task: {task_id}")

        except Exception as e:
            print(f"[MQ] Error publishing result: {e}")

    def _get_timestamp(self) -> str:
        """获取当前时间戳"""
        from datetime import datetime
        return datetime.now().isoformat()

    def publish_generation_request(self, top_event: str, doc_ids: list = None,
                                   equipment_type: str = 'general',
                                   user_preferences: str = None) -> str:
        """
        主动发布一个生成请求到队列（供外部调用）

        Returns:
            task_id: 任务ID，用于查询结果
        """
        task_id = f"task_{uuid.uuid4().hex[:16]}"

        message = {
            'taskId': task_id,
            'topEvent': top_event,
            'docIds': doc_ids or [],
            'equipmentType': equipment_type,
            'userPreferences': user_preferences
        }

        self.channel.basic_publish(
            exchange='exchange.rag',
            routing_key='rag.generate',
            body=json.dumps(message, ensure_ascii=False),
            properties=pika.BasicProperties(
                delivery_mode=2,
                content_type='application/json'
            )
        )

        print(f"[MQ] Published generation request: taskId={task_id}")
        return task_id


def create_and_start_service():
    """创建并启动服务"""
    service = RAGServiceWithMQ()
    service.start()
    return service


if __name__ == "__main__":
    create_and_start_service()
