
// ============================================================================
// 故障树智能生成系统 - Neo4j 知识图谱初始化 - 扩展版
// 基于"通用型驱动系统故障数据"文档生成
// 服务: knowledge-graph-service
//
// 设计说明:
// 1. 节点类型:
//    - UserEvent: 用户级事件节点
//    - GlobalEvent: 全局/公共事件节点
//    - EquipmentType: 设备类型节点
//    - FaultTemplate: 故障模板节点
//    - FaultCode: 故障代码节点
//    - Component: 组件节点
//    - Parameter: 参数节点
//
// 2. 关系类型:
//    - CAUSES: 因果关系
//    - IS_A: 实例关系
//    - PART_OF: 组成关系
//    - HAS_FAULT: 组件具有故障
//    - RELATED_TO: 相关关系
//
// ============================================================================

// ============================================================================
// 第一部分：约束和索引创建
// ============================================================================

// 事件节点约束 (用户级事件)
CREATE CONSTRAINT IF NOT EXISTS FOR (e:UserEvent) REQUIRE e.id IS UNIQUE;

// 全局事件节点约束
CREATE CONSTRAINT IF NOT EXISTS FOR (g:GlobalEvent) REQUIRE g.id IS UNIQUE;

// 设备类型节点约束
CREATE CONSTRAINT IF NOT EXISTS FOR (et:EquipmentType) REQUIRE et.id IS UNIQUE;

// 故障模板节点约束
CREATE CONSTRAINT IF NOT EXISTS FOR (ft:FaultTemplate) REQUIRE ft.id IS UNIQUE;

// 故障代码节点约束
CREATE CONSTRAINT IF NOT EXISTS FOR (fc:FaultCode) REQUIRE fc.id IS UNIQUE;

// 组件节点约束
CREATE CONSTRAINT IF NOT EXISTS FOR (c:Component) REQUIRE c.id IS UNIQUE;

// 参数节点约束
CREATE CONSTRAINT IF NOT EXISTS FOR (p:Parameter) REQUIRE p.id IS UNIQUE;

// 索引创建
CREATE INDEX user_event_user_id IF NOT EXISTS FOR (e:UserEvent) ON (e.userId);
CREATE INDEX user_event_name IF NOT EXISTS FOR (e:UserEvent) ON (e.name);
CREATE INDEX user_event_equipment_type IF NOT EXISTS FOR (e:UserEvent) ON (e.equipmentType);
CREATE INDEX global_event_name IF NOT EXISTS FOR (g:GlobalEvent) ON (g.name);
CREATE INDEX global_event_equipment_type IF NOT EXISTS FOR (g:GlobalEvent) ON (g.equipmentType);
CREATE INDEX equipment_type_name IF NOT EXISTS FOR (et:EquipmentType) ON (et.name);
CREATE INDEX fault_code_name IF NOT EXISTS FOR (fc:FaultCode) ON (fc.code);
CREATE INDEX component_name IF NOT EXISTS FOR (c:Component) ON (c.name);
CREATE INDEX parameter_name IF NOT EXISTS FOR (p:Parameter) ON (p.name);

// ============================================================================
// 第二部分：设备类型节点 (扩展版)
// ============================================================================

MERGE (et:EquipmentType {id: 'drive_system'})
SET et.name = '驱动系统', et.category = '工业驱动', et.description = '通用型驱动系统，包含控制单元、功率单元、编码器等组件',
    et.failure_rate = 0.12, et.maintenance_interval_months = 12, et.created_at = datetime();

MERGE (et:EquipmentType {id: 'control_unit'})
SET et.name = '控制单元(CU)', et.category = '电子设备', et.description = '驱动系统控制单元',
    et.failure_rate = 0.08, et.maintenance_interval_months = 18, et.created_at = datetime();

MERGE (et:EquipmentType {id: 'power_unit'})
SET et.name = '功率单元', et.category = '电力设备', et.description = '驱动系统功率单元/电机模块',
    et.failure_rate = 0.10, et.maintenance_interval_months = 12, et.created_at = datetime();

MERGE (et:EquipmentType {id: 'encoder_module'})
SET et.name = '编码器模块', et.category = '传感器设备', et.description = '驱动系统编码器模块',
    et.failure_rate = 0.07, et.maintenance_interval_months = 24, et.created_at = datetime();

MERGE (et:EquipmentType {id: 'motor_module'})
SET et.name = '电机模块(MM)', et.category = '电力设备', et.description = '驱动系统电机模块',
    et.failure_rate = 0.09, et.maintenance_interval_months = 12, et.created_at = datetime();

MERGE (et:EquipmentType {id: 'hydraulic_module'})
SET et.name = '液压模块(HLA)', et.category = '液压设备', et.description = '驱动系统液压模块',
    et.failure_rate = 0.11, et.maintenance_interval_months = 6, et.created_at = datetime();

MERGE (et:EquipmentType {id: 'communication_module'})
SET et.name = '通讯模块', et.category = '通讯设备', et.description = 'PROFINET/PROFIBUS/DRIVE-CLiQ通讯模块',
    et.failure_rate = 0.06, et.maintenance_interval_months = 18, et.created_at = datetime();

MERGE (et:EquipmentType {id: 'brake_module'})
SET et.name = '制动模块', et.category = '机械设备', et.description = '电机抱闸及安全制动适配器',
    et.failure_rate = 0.08, et.maintenance_interval_months = 12, et.created_at = datetime();

MERGE (et:EquipmentType {id: 'fan_component'})
SET et.name = '风扇组件', et.category = '辅助设备', et.description = '控制单元及功率单元散热风扇',
    et.failure_rate = 0.15, et.maintenance_interval_months = 6, et.created_at = datetime();

MERGE (et:EquipmentType {id: 'encoder_sensor'})
SET et.name = '编码器传感器', et.category = '传感器设备', et.description = '位置、转速、温度等各类传感器',
    et.failure_rate = 0.06, et.maintenance_interval_months = 24, et.created_at = datetime();

MERGE (et:EquipmentType {id: 'memory_module'})
SET et.name = '存储器模块', et.category = '电子设备', et.description = '参数存储及固件存储器',
    et.failure_rate = 0.05, et.maintenance_interval_months = 36, et.created_at = datetime();

MERGE (et:EquipmentType {id: 'power_supply'})
SET et.name = '电源供应', et.category = '电力设备', et.description = '驱动系统电源供应模块',
    et.failure_rate = 0.07, et.maintenance_interval_months = 24, et.created_at = datetime();

MERGE (et:EquipmentType {id: 'switching_device'})
SET et.name = '开关设备', et.category = '电力设备', et.description = '各类开关、接触器等',
    et.failure_rate = 0.09, et.maintenance_interval_months = 12, et.created_at = datetime();

MERGE (et:EquipmentType {id: 'cable_connection'})
SET et.name = '电缆连接', et.category = '辅助设备', et.description = '各类通讯及动力电缆连接',
    et.failure_rate = 0.08, et.maintenance_interval_months = 18, et.created_at = datetime();

// ============================================================================
// 第三部分：组件节点 (扩展版)
// ============================================================================

MERGE (c:Component {id: 'cu_mainboard'})
SET c.name = '控制单元主板', c.type = '电路板', c.equipmentType = '控制单元(CU)', c.description = '控制单元核心电路板',
    c.failure_rate = 0.06, c.is_global = true, c.created_by = 'SYSTEM', c.created_at = datetime();

MERGE (c:Component {id: 'cu_memory_chip'})
SET c.name = '控制单元存储器芯片', c.type = '芯片', c.equipmentType = '控制单元(CU)', c.description = 'RAM/ROM存储芯片',
    c.failure_rate = 0.04, c.is_global = true, c.created_by = 'SYSTEM', c.created_at = datetime();

MERGE (c:Component {id: 'cu_fan'})
SET c.name = '控制单元风扇', c.type = '风扇', c.equipmentType = '风扇组件', c.description = '控制单元散热风扇',
    c.failure_rate = 0.15, c.is_global = true, c.created_by = 'SYSTEM', c.created_at = datetime();

MERGE (c:Component {id: 'drive_cliq_interface'})
SET c.name = 'DRIVE-CLiQ接口', c.type = '通讯接口', c.equipmentType = '通讯模块', c.description = 'DRIVE-CLiQ通讯接口',
    c.failure_rate = 0.05, c.is_global = true, c.created_by = 'SYSTEM', c.created_at = datetime();

MERGE (c:Component {id: 'profinet_interface'})
SET c.name = 'PROFINET接口', c.type = '通讯接口', c.equipmentType = '通讯模块', c.description = 'PROFINET通讯接口',
    c.failure_rate = 0.06, c.is_global = true, c.created_by = 'SYSTEM', c.created_at = datetime();

MERGE (c:Component {id: 'profibus_interface'})
SET c.name = 'PROFIBUS接口', c.type = '通讯接口', c.equipmentType = '通讯模块', c.description = 'PROFIBUS通讯接口',
    c.failure_rate = 0.06, c.is_global = true, c.created_by = 'SYSTEM', c.created_at = datetime();

MERGE (c:Component {id: 'storage_card'})
SET c.name = '存储卡', c.type = '存储设备', c.equipmentType = '存储器模块', c.description = '参数及固件存储存储卡',
    c.failure_rate = 0.03, c.is_global = true, c.created_by = 'SYSTEM', c.created_at = datetime();

MERGE (c:Component {id: 'motor_encoder'})
SET c.name = '电机编码器', c.type = '传感器', c.equipmentType = '编码器传感器', c.description = '电机位置及转速编码器',
    c.failure_rate = 0.07, c.is_global = true, c.created_by = 'SYSTEM', c.created_at = datetime();

MERGE (c:Component {id: 'temperature_sensor'})
SET c.name = '温度传感器', c.type = '传感器', c.equipmentType = '编码器传感器', c.description = '设备温度监测传感器',
    c.failure_rate = 0.05, c.is_global = true, c.created_by = 'SYSTEM', c.created_at = datetime();

MERGE (c:Component {id: 'motor_brake'})
SET c.name = '电机抱闸', c.type = '机械部件', c.equipmentType = '制动模块', c.description = '电机抱闸装置',
    c.failure_rate = 0.09, c.is_global = true, c.created_by = 'SYSTEM', c.created_at = datetime();

MERGE (c:Component {id: 'safety_brake_adapter'})
SET c.name = '安全制动适配器', c.type = '电子部件', c.equipmentType = '制动模块', c.description = 'SBC安全制动适配器',
    c.failure_rate = 0.06, c.is_global = true, c.created_by = 'SYSTEM', c.created_at = datetime();

MERGE (c:Component {id: 'shutoff_valve'})
SET c.name = '断流阀', c.type = '液压部件', c.equipmentType = '液压模块(HLA)', c.description = '液压断流阀组件',
    c.failure_rate = 0.10, c.is_global = true, c.created_by = 'SYSTEM', c.created_at = datetime();

MERGE (c:Component {id: 'power_unit_igbt'})
SET c.name = '功率单元IGBT', c.type = '电力电子', c.equipmentType = '功率单元', c.description = '功率单元IGBT模块',
    c.failure_rate = 0.08, c.is_global = true, c.created_by = 'SYSTEM', c.created_at = datetime();

MERGE (c:Component {id: 'motor_stator'})
SET c.name = '电机定子', c.type = '电机部件', c.equipmentType = '电机模块(MM)', c.description = '电机定子绕组',
    c.failure_rate = 0.05, c.is_global = true, c.created_by = 'SYSTEM', c.created_at = datetime();

MERGE (c:Component {id: 'motor_rotor'})
SET c.name = '电机转子', c.type = '电机部件', c.equipmentType = '电机模块(MM)', c.description = '电机转子轴',
    c.failure_rate = 0.04, c.is_global = true, c.created_by = 'SYSTEM', c.created_at = datetime();

// ============================================================================
// 第四部分：故障代码节点 (扩展版 - 基于文档)
// ============================================================================

MERGE (fc:FaultCode {id: 'F01000'})
SET fc.code = 'F01000', fc.name = '控制单元硬件故障', fc.category = '硬件/软件故障', 
    fc.component = '控制单元(CU)', fc.description = '控制单元硬件故障，模块故障',
    fc.fault_value_param = 'r0949', fc.response = 'OFF2', fc.acknowledge = '上电',
    fc.is_global = true, fc.created_by = 'SYSTEM', fc.created_at = datetime();

MERGE (fc:FaultCode {id: 'F01002'})
SET fc.code = 'F01002', fc.name = '控制单元内部错误', fc.category = '硬件/软件故障', 
    fc.component = '控制单元(CU)', fc.description = '控制单元内部错误',
    fc.fault_value_param = 'r0949', fc.response = 'OFF2', fc.acknowledge = '立即',
    fc.is_global = true, fc.created_by = 'SYSTEM', fc.created_at = datetime();

MERGE (fc:FaultCode {id: 'F01003'})
SET fc.code = 'F01003', fc.name = '存储器访问延迟', fc.category = '硬件/软件故障', 
    fc.component = '控制单元(CU)', fc.description = '访问存储器时出现应答延迟',
    fc.fault_value_param = 'r0949', fc.response = 'OFF2', fc.acknowledge = '立即',
    fc.is_global = true, fc.created_by = 'SYSTEM', fc.created_at = datetime();

MERGE (fc:FaultCode {id: 'F01014'})
SET fc.code = 'F01014', fc.name = 'DRIVE-CLiQ组件属性变化', fc.category = '硬件/软件故障', 
    fc.component = '控制单元(CU)', fc.description = '拓扑：DRIVE-CLiQ组件属性变化',
    fc.fault_value_param = 'r0949', fc.response = '无', fc.acknowledge = '立即',
    fc.is_global = true, fc.created_by = 'SYSTEM', fc.created_at = datetime();

MERGE (fc:FaultCode {id: 'F01015'})
SET fc.code = 'F01015', fc.name = '内部软件错误', fc.category = '硬件/软件故障', 
    fc.component = '控制单元(CU)', fc.description = '内部软件错误',
    fc.fault_value_param = 'r0949', fc.response = 'OFF2', fc.acknowledge = '上电',
    fc.is_global = true, fc.created_by = 'SYSTEM', fc.created_at = datetime();

MERGE (fc:FaultCode {id: 'F01020'})
SET fc.code = 'F01020', fc.name = '写RAM失败', fc.category = '硬件/软件故障', 
    fc.component = '控制单元(CU)', fc.description = '写RAM失败',
    fc.fault_value_param = 'r0949', fc.response = '无', fc.acknowledge = '无',
    fc.is_global = true, fc.created_by = 'SYSTEM', fc.created_at = datetime();

MERGE (fc:FaultCode {id: 'F01023'})
SET fc.code = 'F01023', fc.name = '内部软件超时', fc.category = '硬件/软件故障', 
    fc.component = '控制单元(CU)', fc.description = '内部软件超时',
    fc.fault_value_param = 'r0949', fc.response = '无', fc.acknowledge = '立即',
    fc.is_global = true, fc.created_by = 'SYSTEM', fc.created_at = datetime();

MERGE (fc:FaultCode {id: 'A01013'})
SET fc.code = 'A01013', fc.name = '风扇寿命警告', fc.category = '一般驱动故障', 
    fc.component = '风扇组件', fc.description = '控制单元：达到或超过风扇的使用寿命',
    fc.fault_value_param = 'r2124', fc.response = '无', fc.acknowledge = '无',
    fc.is_global = true, fc.created_by = 'SYSTEM', fc.created_at = datetime();

MERGE (fc:FaultCode {id: 'A01016'})
SET fc.code = 'A01016', fc.name = '固件修改警告', fc.category = '硬件固件', 
    fc.component = '存储器模块', fc.description = '固件被修改',
    fc.fault_value_param = 'r2124', fc.response = '无', fc.acknowledge = '无',
    fc.is_global = true, fc.created_by = 'SYSTEM', fc.created_at = datetime();

MERGE (fc:FaultCode {id: 'A01017'})
SET fc.code = 'A01017', fc.name = '组件列表改变', fc.category = '硬件/软件故障', 
    fc.component = '控制单元(CU)', fc.description = '组件列表被更改',
    fc.fault_value_param = 'r2124', fc.response = '无', fc.acknowledge = '无',
    fc.is_global = true, fc.created_by = 'SYSTEM', fc.created_at = datetime();

MERGE (fc:FaultCode {id: 'A01032'})
SET fc.code = 'A01032', fc.name = '需要保存参数', fc.category = '硬件/软件故障', 
    fc.component = '控制单元(CU)', fc.description = 'ACX：需要存储所有参数',
    fc.fault_value_param = 'r2124', fc.response = '无', fc.acknowledge = '无',
    fc.is_global = true, fc.created_by = 'SYSTEM', fc.created_at = datetime();

// 安全功能相关故障代码
MERGE (fc:FaultCode {id: 'F01600'})
SET fc.code = 'F01600', fc.name = 'SI CU STOP A触发', fc.category = '安全监控通道故障', 
    fc.component = '控制单元(CU)', fc.description = 'SI CU: STOP A被触发',
    fc.fault_value_param = 'r0949', fc.response = 'OFF1/OFF2/OFF3', fc.acknowledge = '立即/上电',
    fc.is_global = true, fc.created_by = 'SYSTEM', fc.created_at = datetime();

MERGE (fc:FaultCode {id: 'F01611'})
SET fc.code = 'F01611', fc.name = 'SI P1监控通道故障', fc.category = '安全监控通道故障', 
    fc.component = '液压模块(HLA)', fc.description = 'SI P1(CU): 某一监控通道故障',
    fc.fault_value_param = 'r0949', fc.response = 'OFF1/OFF2/OFF3', fc.acknowledge = '立即/上电',
    fc.is_global = true, fc.created_by = 'SYSTEM', fc.created_at = datetime();

MERGE (fc:FaultCode {id: 'F01612'})
SET fc.code = 'F01612', fc.name = '并联功率单元STO输入不同', fc.category = '安全监控通道故障', 
    fc.component = '功率单元', fc.description = 'SI P1(CU): 并联功率单元上STO输入不同',
    fc.fault_value_param = 'r0949', fc.response = '无', fc.acknowledge = '立即',
    fc.is_global = true, fc.created_by = 'SYSTEM', fc.created_at = datetime();

MERGE (fc:FaultCode {id: 'F01630'})
SET fc.code = 'F01630', fc.name = '制动控制出错', fc.category = '安全监控通道故障', 
    fc.component = '制动模块', fc.description = 'SI P1(CU): 制动控制出错',
    fc.fault_value_param = 'r0949', fc.response = 'OFF2', fc.acknowledge = '立即/上电',
    fc.is_global = true, fc.created_by = 'SYSTEM', fc.created_at = datetime();

MERGE (fc:FaultCode {id: 'F01632'})
SET fc.code = 'F01632', fc.name = '断流阀控制/反馈出错', fc.category = '安全监控通道故障', 
    fc.component = '液压模块(HLA)', fc.description = 'SI P1(CU): 断流阀控制/反馈出错',
    fc.fault_value_param = 'r0949', fc.response = 'OFF2', fc.acknowledge = '立即/上电',
    fc.is_global = true, fc.created_by = 'SYSTEM', fc.created_at = datetime();

MERGE (fc:FaultCode {id: 'A01631'})
SET fc.code = 'A01631', fc.name = '电机抱闸/SBC配置无意义', fc.category = '制动控制配置', 
    fc.component = '制动模块', fc.description = 'SI P1(CU): 电机抱闸/SBC配置无意义',
    fc.fault_value_param = 'r2124', fc.response = '无', fc.acknowledge = '无',
    fc.is_global = true, fc.created_by = 'SYSTEM', fc.created_at = datetime();

// 编码器相关故障代码
MERGE (fc:FaultCode {id: 'F01034'})
SET fc.code = 'F01034', fc.name = '单位转换参考值更改后参数计算失败', fc.category = '参数设置错误', 
    fc.component = '编码器模块', fc.description = '单位转换：参考值更改后参数值计算失败',
    fc.fault_value_param = 'r0949', fc.response = '无', fc.acknowledge = '立即',
    fc.is_global = true, fc.created_by = 'SYSTEM', fc.created_at = datetime();

MERGE (fc:FaultCode {id: 'F01033'})
SET fc.code = 'F01033', fc.name = '单位转换参考参数无效', fc.category = '参数设置错误', 
    fc.component = '编码器模块', fc.description = '单位转换：参考参数无效',
    fc.fault_value_param = 'r0949', fc.response = '无', fc.acknowledge = '立即',
    fc.is_global = true, fc.created_by = 'SYSTEM', fc.created_at = datetime();

MERGE (fc:FaultCode {id: 'F01010'})
SET fc.code = 'F01010', fc.name = '驱动类型不明', fc.category = '参数设置错误', 
    fc.component = '电机模块(MM)', fc.description = '驱动类型不明',
    fc.fault_value_param = 'r0949', fc.response = '无', fc.acknowledge = '立即',
    fc.is_global = true, fc.created_by = 'SYSTEM', fc.created_at = datetime();

MERGE (fc:FaultCode {id: 'F01005'})
SET fc.code = 'F01005', fc.name = '下载DRIVE-CLiQ组件固件失败', fc.category = '硬件/软件故障', 
    fc.component = '编码器模块', fc.description = '下载DRIVE-CLiQ组件的固件失败(含编码器模块)',
    fc.fault_value_param = 'r0949', fc.response = '无', fc.acknowledge = '立即',
    fc.is_global = true, fc.created_by = 'SYSTEM', fc.created_at = datetime();

// ============================================================================
// 第五部分：参数节点 (扩展版)
// ============================================================================

MERGE (p:Parameter {id: 'p0976'})
SET p.name = 'p0976', p.display_name = '热启动/复位', p.category = '参数操作', 
    p.description = '热启动/复位参数', p.value_range = '0-3', p.default_value = '0',
    p.is_global = true, p.created_by = 'SYSTEM', p.created_at = datetime();

MERGE (p:Parameter {id: 'p0977'})
SET p.name = 'p0977', p.display_name = '保存所有参数', p.category = '参数操作', 
    p.description = '保存所有参数到非易失性存储器', p.value_range = '0/1', p.default_value = '0',
    p.is_global = true, p.created_by = 'SYSTEM', p.created_at = datetime();

MERGE (p:Parameter {id: 'p9930'})
SET p.name = 'p9930', p.display_name = '激活系统日志', p.category = '系统配置', 
    p.description = '激活系统日志，记录系统运行及故障信息', p.value_range = '0/1', p.default_value = '0',
    p.is_global = true, p.created_by = 'SYSTEM', p.created_at = datetime();

MERGE (p:Parameter {id: 'p9702'})
SET p.name = 'p9702', p.display_name = 'SI确认组件更换', p.category = '安全功能', 
    p.description = 'SI确认组件更换', p.value_range = '0-31', p.default_value = '0',
    p.is_global = true, p.created_by = 'SYSTEM', p.created_at = datetime();

MERGE (p:Parameter {id: 'r0949'})
SET p.name = 'r0949', p.display_name = '故障值存储', p.category = '诊断', 
    p.description = '故障值存储参数，用于记录各类故障的补充信息', p.value_range = '0-65535', p.default_value = '0',
    p.is_global = true, p.created_by = 'SYSTEM', p.created_at = datetime();

MERGE (p:Parameter {id: 'r2124'})
SET p.name = 'r2124', p.display_name = '报警值存储', p.category = '诊断', 
    p.description = '报警值存储参数，用于记录各类报警的补充信息', p.value_range = '0-999', p.default_value = '0',
    p.is_global = true, p.created_by = 'SYSTEM', p.created_at = datetime();

MERGE (p:Parameter {id: 'p9601'})
SET p.name = 'p9601', p.display_name = 'SI安全功能使能', p.category = '安全功能', 
    p.description = 'SI安全功能使能', p.value_range = '0/1', p.default_value = '0',
    p.is_global = true, p.created_by = 'SYSTEM', p.created_at = datetime();

MERGE (p:Parameter {id: 'p9602'})
SET p.name = 'p9602', p.display_name = 'SI安全制动控制使能', p.category = '安全功能', 
    p.description = 'SI安全制动控制使能', p.value_range = '0/1', p.default_value = '0',
    p.is_global = true, p.created_by = 'SYSTEM', p.created_at = datetime();

MERGE (p:Parameter {id: 'p1215'})
SET p.name = 'p1215', p.display_name = '电机抱闸配置', p.category = '电机参数', 
    p.description = '电机抱闸配置', p.value_range = '0-3', p.default_value = '0',
    p.is_global = true, p.created_by = 'SYSTEM', p.created_at = datetime();

MERGE (p:Parameter {id: 'p1278'})
SET p.name = 'p1278', p.display_name = '制动控制设置', p.category = '电机参数', 
    p.description = '制动控制设置', p.value_range = '0-1', p.default_value = '0',
    p.is_global = true, p.created_by = 'SYSTEM', p.created_at = datetime();

MERGE (p:Parameter {id: 'p0304'})
SET p.name = 'p0304', p.display_name = '电机额定电压', p.category = '电机参数', 
    p.description = '电机额定电压', p.value_range = '0-2000', p.default_value = '400',
    p.is_global = true, p.created_by = 'SYSTEM', p.created_at = datetime();

MERGE (p:Parameter {id: 'p0305'})
SET p.name = 'p0305', p.display_name = '电机额定电流', p.category = '电机参数', 
    p.description = '电机额定电流', p.value_range = '0-1000', p.default_value = '10',
    p.is_global = true, p.created_by = 'SYSTEM', p.created_at = datetime();

MERGE (p:Parameter {id: 'p0310'})
SET p.name = 'p0310', p.display_name = '电机额定转速', p.category = '电机参数', 
    p.description = '电机额定转速', p.value_range = '0-30000', p.default_value = '1500',
    p.is_global = true, p.created_by = 'SYSTEM', p.created_at = datetime();

// ============================================================================
// 第六部分：全局事件节点 (扩展版)
// ============================================================================

MERGE (g:GlobalEvent {id: 'global_drive_hardware_fault'})
SET g.name = '驱动系统硬件故障', g.type = '顶事件', g.description = '驱动系统硬件出现故障',
    g.equipmentType = '驱动系统', g.severity = 'CRITICAL', g.probability = 0.15,
    g.isGlobal = true, g.created_by = 'SYSTEM', g.created_at = datetime();

MERGE (g:GlobalEvent {id: 'global_cu_hardware_fault'})
SET g.name = '控制单元硬件故障', g.type = '中间事件', g.description = '控制单元硬件出现故障',
    g.equipmentType = '控制单元(CU)', g.severity = 'HIGH', g.probability = 0.10,
    g.isGlobal = true, g.created_by = 'SYSTEM', g.created_at = datetime();

MERGE (g:GlobalEvent {id: 'global_power_unit_fault'})
SET g.name = '功率单元故障', g.type = '中间事件', g.description = '功率单元出现故障',
    g.equipmentType = '功率单元', g.severity = 'HIGH', g.probability = 0.12,
    g.isGlobal = true, g.created_by = 'SYSTEM', g.created_at = datetime();

MERGE (g:GlobalEvent {id: 'global_encoder_fault'})
SET g.name = '编码器故障', g.type = '中间事件', g.description = '编码器模块出现故障',
    g.equipmentType = '编码器模块', g.severity = 'MEDIUM', g.probability = 0.08,
    g.isGlobal = true, g.created_by = 'SYSTEM', g.created_at = datetime();

MERGE (g:GlobalEvent {id: 'global_communication_fault'})
SET g.name = '通讯故障', g.type = '中间事件', g.description = '系统通讯出现故障',
    g.equipmentType = '通讯模块', g.severity = 'HIGH', g.probability = 0.09,
    g.isGlobal = true, g.created_by = 'SYSTEM', g.created_at = datetime();

MERGE (g:GlobalEvent {id: 'global_safety_function_fault'})
SET g.name = '安全功能故障', g.type = '中间事件', g.description = '安全功能出现故障',
    g.equipmentType = '驱动系统', g.severity = 'CRITICAL', g.probability = 0.07,
    g.isGlobal = true, g.created_by = 'SYSTEM', g.created_at = datetime();

MERGE (g:GlobalEvent {id: 'global_cu_memory_fault'})
SET g.name = '控制单元存储器故障', g.type = '底事件', g.description = '控制单元存储器访问失败或损坏',
    g.equipmentType = '控制单元(CU)', g.severity = 'HIGH', g.probability = 0.05,
    g.isGlobal = true, g.created_by = 'SYSTEM', g.created_at = datetime();

MERGE (g:GlobalEvent {id: 'global_cu_fan_fault'})
SET g.name = '控制单元风扇故障', g.type = '底事件', g.description = '控制单元风扇损坏或寿命到期',
    g.equipmentType = '风扇组件', g.severity = 'MEDIUM', g.probability = 0.15,
    g.isGlobal = true, g.created_by = 'SYSTEM', g.created_at = datetime();

MERGE (g:GlobalEvent {id: 'global_drive_cliq_fault'})
SET g.name = 'DRIVE-CLiQ通讯故障', g.type = '底事件', g.description = 'DRIVE-CLiQ接口通讯失败',
    g.equipmentType = '通讯模块', g.severity = 'MEDIUM', g.probability = 0.06,
    g.isGlobal = true, g.created_by = 'SYSTEM', g.created_at = datetime();

MERGE (g:GlobalEvent {id: 'global_profinet_fault'})
SET g.name = 'PROFINET通讯故障', g.type = '底事件', g.description = 'PROFINET接口通讯失败',
    g.equipmentType = '通讯模块', g.severity = 'HIGH', g.probability = 0.07,
    g.isGlobal = true, g.created_by = 'SYSTEM', g.created_at = datetime();

MERGE (g:GlobalEvent {id: 'global_motor_brake_fault'})
SET g.name = '电机抱闸故障', g.type = '底事件', g.description = '电机抱闸出现故障',
    g.equipmentType = '制动模块', g.severity = 'HIGH', g.probability = 0.09,
    g.isGlobal = true, g.created_by = 'SYSTEM', g.created_at = datetime();

MERGE (g:GlobalEvent {id: 'global_encoder_sensor_fault'})
SET g.name = '编码器传感器故障', g.type = '底事件', g.description = '编码器传感器信号异常',
    g.equipmentType = '编码器传感器', g.severity = 'MEDIUM', g.probability = 0.07,
    g.isGlobal = true, g.created_by = 'SYSTEM', g.created_at = datetime();

MERGE (g:GlobalEvent {id: 'global_temperature_sensor_fault'})
SET g.name = '温度传感器故障', g.type = '底事件', g.description = '温度传感器读数异常',
    g.equipmentType = '编码器传感器', g.severity = 'LOW', g.probability = 0.05,
    g.isGlobal = true, g.created_by = 'SYSTEM', g.created_at = datetime();

MERGE (g:GlobalEvent {id: 'global_power_igbt_fault'})
SET g.name = '功率单元IGBT故障', g.type = '底事件', g.description = '功率单元IGBT模块损坏',
    g.equipmentType = '功率单元', g.severity = 'HIGH', g.probability = 0.08,
    g.isGlobal = true, g.created_by = 'SYSTEM', g.created_at = datetime();

MERGE (g:GlobalEvent {id: 'global_motor_stator_fault'})
SET g.name = '电机定子故障', g.type = '底事件', g.description = '电机定子绕组损坏',
    g.equipmentType = '电机模块(MM)', g.severity = 'HIGH', g.probability = 0.05,
    g.isGlobal = true, g.created_by = 'SYSTEM', g.created_at = datetime();

MERGE (g:GlobalEvent {id: 'global_motor_rotor_fault'})
SET g.name = '电机转子故障', g.type = '底事件', g.description = '电机转子轴损坏',
    g.equipmentType = '电机模块(MM)', g.severity = 'MEDIUM', g.probability = 0.04,
    g.isGlobal = true, g.created_by = 'SYSTEM', g.created_at = datetime();

MERGE (g:GlobalEvent {id: 'global_parameter_error'})
SET g.name = '参数设置错误', g.type = '底事件', g.description = '系统参数设置错误或不匹配',
    g.equipmentType = '驱动系统', g.severity = 'MEDIUM', g.probability = 0.10,
    g.isGlobal = true, g.created_by = 'SYSTEM', g.created_at = datetime();

MERGE (g:GlobalEvent {id: 'global_firmware_error'})
SET g.name = '固件错误', g.type = '底事件', g.description = '系统固件损坏或版本不匹配',
    g.equipmentType = '存储器模块', g.severity = 'HIGH', g.probability = 0.06,
    g.isGlobal = true, g.created_by = 'SYSTEM', g.created_at = datetime();

MERGE (g:GlobalEvent {id: 'global_si_stop_a_triggered'})
SET g.name = 'SI STOP A触发', g.type = '底事件', g.description = '安全功能STOP A被触发',
    g.equipmentType = '控制单元(CU)', g.severity = 'CRITICAL', g.probability = 0.05,
    g.isGlobal = true, g.created_by = 'SYSTEM', g.created_at = datetime();

// ============================================================================
// 第七部分：组件与设备的组成关系
// ============================================================================

MATCH (et:EquipmentType {id: 'control_unit'})
MATCH (c:Component {id: 'cu_mainboard'})
MERGE (c)-[:PART_OF {confidence: 1.0, description: '控制单元主板属于控制单元'}]->(et);

MATCH (et:EquipmentType {id: 'control_unit'})
MATCH (c:Component {id: 'cu_memory_chip'})
MERGE (c)-[:PART_OF {confidence: 1.0, description: '控制单元存储器芯片属于控制单元'}]->(et);

MATCH (et:EquipmentType {id: 'control_unit'})
MATCH (c:Component {id: 'cu_fan'})
MERGE (c)-[:PART_OF {confidence: 1.0, description: '控制单元风扇属于控制单元'}]->(et);

MATCH (et:EquipmentType {id: 'communication_module'})
MATCH (c:Component {id: 'drive_cliq_interface'})
MERGE (c)-[:PART_OF {confidence: 1.0, description: 'DRIVE-CLiQ接口属于通讯模块'}]->(et);

MATCH (et:EquipmentType {id: 'communication_module'})
MATCH (c:Component {id: 'profinet_interface'})
MERGE (c)-[:PART_OF {confidence: 1.0, description: 'PROFINET接口属于通讯模块'}]->(et);

MATCH (et:EquipmentType {id: 'communication_module'})
MATCH (c:Component {id: 'profibus_interface'})
MERGE (c)-[:PART_OF {confidence: 1.0, description: 'PROFIBUS接口属于通讯模块'}]->(et);

MATCH (et:EquipmentType {id: 'memory_module'})
MATCH (c:Component {id: 'storage_card'})
MERGE (c)-[:PART_OF {confidence: 1.0, description: '存储卡属于存储器模块'}]->(et);

MATCH (et:EquipmentType {id: 'encoder_sensor'})
MATCH (c:Component {id: 'motor_encoder'})
MERGE (c)-[:PART_OF {confidence: 1.0, description: '电机编码器属于编码器传感器'}]->(et);

MATCH (et:EquipmentType {id: 'encoder_sensor'})
MATCH (c:Component {id: 'temperature_sensor'})
MERGE (c)-[:PART_OF {confidence: 1.0, description: '温度传感器属于编码器传感器'}]->(et);

MATCH (et:EquipmentType {id: 'brake_module'})
MATCH (c:Component {id: 'motor_brake'})
MERGE (c)-[:PART_OF {confidence: 1.0, description: '电机抱闸属于制动模块'}]->(et);

MATCH (et:EquipmentType {id: 'brake_module'})
MATCH (c:Component {id: 'safety_brake_adapter'})
MERGE (c)-[:PART_OF {confidence: 1.0, description: '安全制动适配器属于制动模块'}]->(et);

MATCH (et:EquipmentType {id: 'hydraulic_module'})
MATCH (c:Component {id: 'shutoff_valve'})
MERGE (c)-[:PART_OF {confidence: 1.0, description: '断流阀属于液压模块'}]->(et);

MATCH (et:EquipmentType {id: 'power_unit'})
MATCH (c:Component {id: 'power_unit_igbt'})
MERGE (c)-[:PART_OF {confidence: 1.0, description: '功率单元IGBT属于功率单元'}]->(et);

MATCH (et:EquipmentType {id: 'motor_module'})
MATCH (c:Component {id: 'motor_stator'})
MERGE (c)-[:PART_OF {confidence: 1.0, description: '电机定子属于电机模块'}]->(et);

MATCH (et:EquipmentType {id: 'motor_module'})
MATCH (c:Component {id: 'motor_rotor'})
MERGE (c)-[:PART_OF {confidence: 1.0, description: '电机转子属于电机模块'}]->(et);

// ============================================================================
// 第八部分：故障代码与组件的关系
// ============================================================================

MATCH (c:Component {id: 'cu_mainboard'})
MATCH (fc:FaultCode {id: 'F01000'})
MERGE (c)-[:HAS_FAULT {confidence: 0.9, description: '控制单元主板可能触发F01000故障'}]->(fc);

MATCH (c:Component {id: 'cu_memory_chip'})
MATCH (fc:FaultCode {id: 'F01003'})
MERGE (c)-[:HAS_FAULT {confidence: 0.85, description: '控制单元存储器芯片可能触发F01003故障'}]->(fc);

MATCH (c:Component {id: 'cu_fan'})
MATCH (fc:FaultCode {id: 'A01013'})
MERGE (c)-[:HAS_FAULT {confidence: 0.95, description: '控制单元风扇可能触发A01013警告'}]->(fc);

MATCH (c:Component {id: 'drive_cliq_interface'})
MATCH (fc:FaultCode {id: 'F01014'})
MERGE (c)-[:HAS_FAULT {confidence: 0.9, description: 'DRIVE-CLiQ接口可能触发F01014故障'}]->(fc);

MATCH (c:Component {id: 'motor_brake'})
MATCH (fc:FaultCode {id: 'F01630'})
MERGE (c)-[:HAS_FAULT {confidence: 0.85, description: '电机抱闸可能触发F01630故障'}]->(fc);

MATCH (c:Component {id: 'safety_brake_adapter'})
MATCH (fc:FaultCode {id: 'A01631'})
MERGE (c)-[:HAS_FAULT {confidence: 0.9, description: '安全制动适配器可能触发A01631警告'}]->(fc);

MATCH (c:Component {id: 'shutoff_valve'})
MATCH (fc:FaultCode {id: 'F01632'})
MERGE (c)-[:HAS_FAULT {confidence: 0.85, description: '断流阀可能触发F01632故障'}]->(fc);

MATCH (c:Component {id: 'motor_encoder'})
MATCH (fc:FaultCode {id: 'F01005'})
MERGE (c)-[:HAS_FAULT {confidence: 0.9, description: '电机编码器可能触发F01005故障'}]->(fc);

MATCH (c:Component {id: 'storage_card'})
MATCH (fc:FaultCode {id: 'A01016'})
MERGE (c)-[:HAS_FAULT {confidence: 0.8, description: '存储卡可能触发A01016警告'}]->(fc);

// ============================================================================
// 第九部分：参数与故障的关系
// ============================================================================

MATCH (p:Parameter {id: 'p0977'})
MATCH (fc:FaultCode {id: 'A01032'})
MERGE (p)-[:RELATED_TO {confidence: 0.95, description: 'p0977与A01032警告相关，保存所有参数可消除警告'}]->(fc);

MATCH (p:Parameter {id: 'p9601'})
MATCH (fc:FaultCode {id: 'F01600'})
MERGE (p)-[:RELATED_TO {confidence: 0.9, description: 'p9601与F01600故障相关，安全功能使能参数'}]->(fc);

MATCH (p:Parameter {id: 'p9602'})
MATCH (fc:FaultCode {id: 'F01630'})
MERGE (p)-[:RELATED_TO {confidence: 0.9, description: 'p9602与F01630故障相关，安全制动控制使能参数'}]->(fc);

MATCH (p:Parameter {id: 'p1215'})
MATCH (fc:FaultCode {id: 'A01631'})
MERGE (p)-[:RELATED_TO {confidence: 0.95, description: 'p1215与A01631警告相关，电机抱闸配置参数'}]->(fc);

MATCH (p:Parameter {id: 'p1278'})
MATCH (fc:FaultCode {id: 'A01631'})
MERGE (p)-[:RELATED_TO {confidence: 0.9, description: 'p1278与A01631警告相关，制动控制设置参数'}]->(fc);

MATCH (p:Parameter {id: 'p0304'})
MATCH (fc:FaultCode {id: 'F01033'})
MERGE (p)-[:RELATED_TO {confidence: 0.85, description: 'p0304与F01033故障相关，电机额定电压用于单位转换'}]->(fc);

MATCH (p:Parameter {id: 'p0305'})
MATCH (fc:FaultCode {id: 'F01033'})
MERGE (p)-[:RELATED_TO {confidence: 0.85, description: 'p0305与F01033故障相关，电机额定电流用于单位转换'}]->(fc);

MATCH (p:Parameter {id: 'p0310'})
MATCH (fc:FaultCode {id: 'F01033'})
MERGE (p)-[:RELATED_TO {confidence: 0.85, description: 'p0310与F01033故障相关，电机额定转速用于单位转换'}]->(fc);

MATCH (p:Parameter {id: 'r0949'})
MATCH (fc:FaultCode {id: 'F01000'})
MERGE (p)-[:RELATED_TO {confidence: 1.0, description: 'r0949是F01000的故障值存储参数'}]->(fc);

MATCH (p:Parameter {id: 'r2124'})
MATCH (fc:FaultCode {id: 'A01013'})
MERGE (p)-[:RELATED_TO {confidence: 1.0, description: 'r2124是A01013的报警值存储参数'}]->(fc);

// ============================================================================
// 第十部分：全局事件因果关系 (扩展版 - 基于文档)
// ============================================================================

// 控制单元故障树
MATCH (g1:GlobalEvent {id: 'global_cu_memory_fault'})
MATCH (g2:GlobalEvent {id: 'global_cu_hardware_fault'})
MERGE (g1)-[:CAUSES {gateType: 'OR', description: '控制单元存储器故障导致控制单元硬件故障', confidence: 0.9, isGlobal: true, created_at: datetime()}]->(g2);

MATCH (g1:GlobalEvent {id: 'global_firmware_error'})
MATCH (g2:GlobalEvent {id: 'global_cu_hardware_fault'})
MERGE (g1)-[:CAUSES {gateType: 'OR', description: '固件错误导致控制单元硬件故障', confidence: 0.85, isGlobal: true, created_at: datetime()}]->(g2);

MATCH (g1:GlobalEvent {id: 'global_cu_hardware_fault'})
MATCH (g2:GlobalEvent {id: 'global_drive_hardware_fault'})
MERGE (g1)-[:CAUSES {gateType: 'OR', description: '控制单元硬件故障导致驱动系统硬件故障', confidence: 0.95, isGlobal: true, created_at: datetime()}]->(g2);

// 通讯故障树
MATCH (g1:GlobalEvent {id: 'global_drive_cliq_fault'})
MATCH (g2:GlobalEvent {id: 'global_communication_fault'})
MERGE (g1)-[:CAUSES {gateType: 'OR', description: 'DRIVE-CLiQ通讯故障导致通讯故障', confidence: 0.9, isGlobal: true, created_at: datetime()}]->(g2);

MATCH (g1:GlobalEvent {id: 'global_profinet_fault'})
MATCH (g2:GlobalEvent {id: 'global_communication_fault'})
MERGE (g1)-[:CAUSES {gateType: 'OR', description: 'PROFINET通讯故障导致通讯故障', confidence: 0.95, isGlobal: true, created_at: datetime()}]->(g2);

MATCH (g1:GlobalEvent {id: 'global_communication_fault'})
MATCH (g2:GlobalEvent {id: 'global_drive_hardware_fault'})
MERGE (g1)-[:CAUSES {gateType: 'OR', description: '通讯故障导致驱动系统硬件故障', confidence: 0.9, isGlobal: true, created_at: datetime()}]->(g2);

// 编码器故障树
MATCH (g1:GlobalEvent {id: 'global_encoder_sensor_fault'})
MATCH (g2:GlobalEvent {id: 'global_encoder_fault'})
MERGE (g1)-[:CAUSES {gateType: 'OR', description: '编码器传感器故障导致编码器故障', confidence: 0.9, isGlobal: true, created_at: datetime()}]->(g2);

MATCH (g1:GlobalEvent {id: 'global_temperature_sensor_fault'})
MATCH (g2:GlobalEvent {id: 'global_encoder_fault'})
MERGE (g1)-[:CAUSES {gateType: 'OR', description: '温度传感器故障导致编码器故障', confidence: 0.7, isGlobal: true, created_at: datetime()}]->(g2);

MATCH (g1:GlobalEvent {id: 'global_encoder_fault'})
MATCH (g2:GlobalEvent {id: 'global_drive_hardware_fault'})
MERGE (g1)-[:CAUSES {gateType: 'OR', description: '编码器故障导致驱动系统硬件故障', confidence: 0.85, isGlobal: true, created_at: datetime()}]->(g2);

// 功率单元故障树
MATCH (g1:GlobalEvent {id: 'global_power_igbt_fault'})
MATCH (g2:GlobalEvent {id: 'global_power_unit_fault'})
MERGE (g1)-[:CAUSES {gateType: 'OR', description: '功率单元IGBT故障导致功率单元故障', confidence: 0.95, isGlobal: true, created_at: datetime()}]->(g2);

MATCH (g1:GlobalEvent {id: 'global_motor_stator_fault'})
MATCH (g2:GlobalEvent {id: 'global_power_unit_fault'})
MERGE (g1)-[:CAUSES {gateType: 'OR', description: '电机定子故障导致功率单元故障', confidence: 0.9, isGlobal: true, created_at: datetime()}]->(g2);

MATCH (g1:GlobalEvent {id: 'global_motor_rotor_fault'})
MATCH (g2:GlobalEvent {id: 'global_power_unit_fault'})
MERGE (g1)-[:CAUSES {gateType: 'OR', description: '电机转子故障导致功率单元故障', confidence: 0.85, isGlobal: true, created_at: datetime()}]->(g2);

MATCH (g1:GlobalEvent {id: 'global_power_unit_fault'})
MATCH (g2:GlobalEvent {id: 'global_drive_hardware_fault'})
MERGE (g1)-[:CAUSES {gateType: 'OR', description: '功率单元故障导致驱动系统硬件故障', confidence: 0.95, isGlobal: true, created_at: datetime()}]->(g2);

// 安全功能故障树
MATCH (g1:GlobalEvent {id: 'global_si_stop_a_triggered'})
MATCH (g2:GlobalEvent {id: 'global_safety_function_fault'})
MERGE (g1)-[:CAUSES {gateType: 'OR', description: 'SI STOP A触发导致安全功能故障', confidence: 0.95, isGlobal: true, created_at: datetime()}]->(g2);

MATCH (g1:GlobalEvent {id: 'global_motor_brake_fault'})
MATCH (g2:GlobalEvent {id: 'global_safety_function_fault'})
MERGE (g1)-[:CAUSES {gateType: 'OR', description: '电机抱闸故障导致安全功能故障', confidence: 0.85, isGlobal: true, created_at: datetime()}]->(g2);

MATCH (g1:GlobalEvent {id: 'global_safety_function_fault'})
MATCH (g2:GlobalEvent {id: 'global_drive_hardware_fault'})
MERGE (g1)-[:CAUSES {gateType: 'OR', description: '安全功能故障导致驱动系统硬件故障', confidence: 0.95, isGlobal: true, created_at: datetime()}]->(g2);

// 参数错误相关因果关系
MATCH (g1:GlobalEvent {id: 'global_parameter_error'})
MATCH (g2:GlobalEvent {id: 'global_encoder_fault'})
MERGE (g1)-[:CAUSES {gateType: 'OR', description: '参数设置错误导致编码器故障', confidence: 0.8, isGlobal: true, created_at: datetime()}]->(g2);

MATCH (g1:GlobalEvent {id: 'global_parameter_error'})
MATCH (g2:GlobalEvent {id: 'global_safety_function_fault'})
MERGE (g1)-[:CAUSES {gateType: 'OR', description: '参数设置错误导致安全功能故障', confidence: 0.85, isGlobal: true, created_at: datetime()}]->(g2);

// ============================================================================
// 第十一部分：事件与故障代码的关系
// ============================================================================

MATCH (g:GlobalEvent {id: 'global_cu_hardware_fault'})
MATCH (fc:FaultCode {id: 'F01000'})
MERGE (g)-[:IS_A {confidence: 0.95, description: '控制单元硬件故障对应F01000故障代码'}]->(fc);

MATCH (g:GlobalEvent {id: 'global_cu_memory_fault'})
MATCH (fc:FaultCode {id: 'F01003'})
MERGE (g)-[:IS_A {confidence: 0.9, description: '控制单元存储器故障对应F01003故障代码'}]->(fc);

MATCH (g:GlobalEvent {id: 'global_cu_fan_fault'})
MATCH (fc:FaultCode {id: 'A01013'})
MERGE (g)-[:IS_A {confidence: 0.95, description: '控制单元风扇故障对应A01013警告代码'}]->(fc);

MATCH (g:GlobalEvent {id: 'global_drive_cliq_fault'})
MATCH (fc:FaultCode {id: 'F01014'})
MERGE (g)-[:IS_A {confidence: 0.9, description: 'DRIVE-CLiQ通讯故障对应F01014故障代码'}]->(fc);

MATCH (g:GlobalEvent {id: 'global_si_stop_a_triggered'})
MATCH (fc:FaultCode {id: 'F01600'})
MERGE (g)-[:IS_A {confidence: 0.95, description: 'SI STOP A触发对应F01600故障代码'}]->(fc);

MATCH (g:GlobalEvent {id: 'global_motor_brake_fault'})
MATCH (fc:FaultCode {id: 'F01630'})
MERGE (g)-[:IS_A {confidence: 0.9, description: '电机抱闸故障对应F01630故障代码'}]->(fc);

MATCH (g:GlobalEvent {id: 'global_encoder_sensor_fault'})
MATCH (fc:FaultCode {id: 'F01005'})
MERGE (g)-[:IS_A {confidence: 0.85, description: '编码器传感器故障对应F01005故障代码'}]->(fc);

MATCH (g:GlobalEvent {id: 'global_parameter_error'})
MATCH (fc:FaultCode {id: 'F01033'})
MERGE (g)-[:IS_A {confidence: 0.9, description: '参数设置错误对应F01033故障代码'}]->(fc);

// ============================================================================
// 第十二部分：验证查询
// ============================================================================

// 返回所有节点数量统计
MATCH (n)
RETURN labels(n)[0] AS nodeType, count(n) AS nodeCount
ORDER BY nodeCount DESC;

// 返回所有关系数量统计
MATCH ()-[r]->()
RETURN type(r) AS relationshipType, count(r) AS relationshipCount
ORDER BY relationshipCount DESC;

// 返回完整的驱动系统硬件故障树
MATCH path = (g:GlobalEvent {id: 'global_drive_hardware_fault'})<-[:CAUSES*1..5]-(cause:GlobalEvent)
RETURN path
ORDER BY length(path);

