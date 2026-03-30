from pydantic import BaseModel
from typing import List, Optional

class EventTypeEnum:
    TOP = "TOP"
    INTERMEDIATE = "INTERMEDIATE"
    BASIC = "BASIC"

class LogicGateEnum:
    AND = "AND"
    OR = "OR"
    XOR = "XOR"

class FaultTreeSchema(BaseModel):
    event_id: str
    event_name: str
    event_type: str
    gate_type: Optional[str] = None
    children: List['FaultTreeSchema'] = []
    source_evidence: Optional[str] = None
    equipment_type: Optional[str] = None

# 解决循环引用
FaultTreeSchema.update_forward_refs()
