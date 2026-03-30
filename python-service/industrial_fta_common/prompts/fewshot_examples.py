# Few-Shot 示例
# 提供完整的故障树生成示例，用于帮助 LLM 理解输出格式

FEWSHOT_MOTOR_OVERHEATING = """
## 示例：电机过热故障树生成

### 输入信息
- **顶事件**：电机过热
- **工业知识**：
  1. 电机过热可能是由电源问题、轴承故障或绕组故障引起的。
  2. 电源问题包括电压不稳定、缺相、过载等。
  3. 轴承故障可能是由于润滑不足、磨损、安装不当等原因。
  4. 绕组故障可能是由于绝缘老化、短路、过载等原因。

### 期望的故障树结构
电机过热 (TOP, OR门)
├── 电源问题 (INTERMEDIATE, OR门)
│   ├── 电压不稳定 (BASIC)
│   ├── 缺相 (BASIC)
│   └── 电源过载 (BASIC)
├── 轴承故障 (INTERMEDIATE, OR门)
│   ├── 润滑不足 (BASIC)
│   ├── 轴承磨损 (BASIC)
│   └── 安装不当 (BASIC)
└── 绕组故障 (INTERMEDIATE, OR门)
    ├── 绝缘老化 (BASIC)
    ├── 绕组短路 (BASIC)
    └── 绕组过载 (BASIC)

### 完整的 JSON 输出示例
{
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
        {"event_id": "evt_003", "event_name": "电压不稳定", "event_type": "BASIC", "children": []},
        {"event_id": "evt_004", "event_name": "缺相", "event_type": "BASIC", "children": []},
        {"event_id": "evt_005", "event_name": "电源过载", "event_type": "BASIC", "children": []}
      ]
    },
    {
      "event_id": "evt_006",
      "event_name": "轴承故障",
      "event_type": "INTERMEDIATE",
      "gate_type": "OR",
      "children": [
        {"event_id": "evt_007", "event_name": "润滑不足", "event_type": "BASIC", "children": []},
        {"event_id": "evt_008", "event_name": "轴承磨损", "event_type": "BASIC", "children": []},
        {"event_id": "evt_009", "event_name": "安装不当", "event_type": "BASIC", "children": []}
      ]
    },
    {
      "event_id": "evt_010",
      "event_name": "绕组故障",
      "event_type": "INTERMEDIATE",
      "gate_type": "OR",
      "children": [
        {"event_id": "evt_011", "event_name": "绝缘老化", "event_type": "BASIC", "children": []},
        {"event_id": "evt_012", "event_name": "绕组短路", "event_type": "BASIC", "children": []},
        {"event_id": "evt_013", "event_name": "绕组过载", "event_type": "BASIC", "children": []}
      ]
    }
  ]
}

### 示例说明
1. **顶事件**："电机过热"是最终的故障现象，使用 OR 门连接三个主要原因
2. **中间事件**："电源问题"、"轴承故障"、"绕组故障"是三个主要原因分类
3. **底事件**：每个具体故障原因（如"电压不稳定"、"润滑不足"）都是底事件
4. **逻辑门**：同一层次的多个原因使用 OR 门（如电源问题的三个子原因）
"""

FEWSHOT_PUMP_FAILURE = """
## 示例：泵失效故障树生成（展示 AND 门）

### 输入信息
- **顶事件**：泵无法正常工作
- **工业知识**：
  1. 泵的正常工作需要满足两个条件：液压油供应正常 AND 动力传递正常。
  2. 液压油供应问题可能包括：进口阀门关闭、滤网堵塞、油箱无油。
  3. 动力传递问题可能包括：电机未启动、联轴器损坏、轴断裂。

### 期望的故障树结构
泵无法正常工作 (TOP, AND门)
├── 液压油供应问题 (INTERMEDIATE, OR门)
│   ├── 进口阀门关闭 (BASIC)
│   ├── 滤网堵塞 (BASIC)
│   └── 油箱无油 (BASIC)
└── 动力传递问题 (INTERMEDIATE, OR门)
    ├── 电机未启动 (BASIC)
    ├── 联轴器损坏 (BASIC)
    └── 轴断裂 (BASIC)

### 示例说明
1. **顶事件**："泵无法正常工作"使用 AND 门，因为需要同时满足两个条件
2. **中间事件**："液压油供应问题"和"动力传递问题"是两个独立的故障路径
3. **逻辑门选择**：
   - 泵无法工作是"且"的关系：必须液压 AND 动力同时出问题
   - 液压问题的三个原因是"或"的关系：任一都会导致液压问题
"""

def get_all_examples():
    """获取所有示例的组合字符串"""
    return FEWSHOT_MOTOR_OVERHEATING + "\n\n" + FEWSHOT_PUMP_FAILURE
