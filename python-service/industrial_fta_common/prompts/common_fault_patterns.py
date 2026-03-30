# 常见故障模式列表
# 按设备类型分类的常见故障模式

COMMON_FAULT_PATTERNS = {
    # 旋转设备类
    "rotating_equipment": [
        "振动异常",
        "温度过高",
        "转速异常",
        "噪声异常",
        "无法启动",
        "无法停止",
    ],

    # 流体设备类
    "fluid_equipment": [
        "流量异常",
        "压力异常",
        "泄漏",
        "堵塞",
        "汽蚀",
        "气缚",
    ],

    # 电气设备类
    "electrical_equipment": [
        "短路",
        "断路",
        "接地",
        "过载",
        "欠压",
        "过压",
        "频率异常",
    ],

    # 控制系统类
    "control_system": [
        "信号丢失",
        "控制失灵",
        "响应迟缓",
        "动作错误",
    ],
}

def get_all_patterns_string():
    """获取所有故障模式的组合字符串"""
    result = []
    for category, patterns in COMMON_FAULT_PATTERNS.items():
        category_name = {
            "rotating_equipment": "旋转设备类",
            "fluid_equipment": "流体设备类",
            "electrical_equipment": "电气设备类",
            "control_system": "控制系统类",
        }.get(category, category)
        result.append(f"{category_name}：{', '.join(patterns)}")
    return "\n".join(result)

def get_patterns_by_category(category):
    """按类别获取故障模式"""
    return COMMON_FAULT_PATTERNS.get(category, [])
