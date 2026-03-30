import json
from fault_tree_schema import FaultTreeSchema

class MessageUtils:
    @staticmethod
    def serialize_message(message):
        """将消息序列化为JSON字符串"""
        return json.dumps(message, ensure_ascii=False)
    
    @staticmethod
    def deserialize_message(message_str):
        """将JSON字符串反序列化为消息对象"""
        return json.loads(message_str)
    
    @staticmethod
    def convert_to_fault_tree_schema(data):
        """将字典转换为FaultTreeSchema对象"""
        return FaultTreeSchema(**data)
    
    @staticmethod
    def convert_to_dict(fault_tree):
        """将FaultTreeSchema对象转换为字典"""
        return fault_tree.dict()
