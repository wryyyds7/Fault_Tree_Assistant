// ============================================================================
// 故障树智能生成系统 - Neo4j 知识图谱初始化
// 服务: knowledge-graph-service
// ============================================================================

// ============================================================================
// 第一部分：约束和索引创建
// ============================================================================

// 事件节点约束
CREATE CONSTRAINT IF NOT EXISTS FOR (e:Event) REQUIRE e.id IS UNIQUE;

// 设备类型节点约束
CREATE CONSTRAINT IF NOT EXISTS FOR (et:EquipmentType) REQUIRE et.id IS UNIQUE;

// 故障模式节点约束
CREATE CONSTRAINT IF NOT EXISTS FOR (fm:FaultMode) REQUIRE fm.id IS UNIQUE;

// 故障模板节点约束
CREATE CONSTRAINT IF NOT EXISTS FOR (ft:FaultTemplate) REQUIRE ft.id IS UNIQUE;

// 索引创建
CREATE INDEX equipment_type_name IF NOT EXISTS FOR (et:EquipmentType) ON (et.name);
CREATE INDEX fault_mode_name IF NOT EXISTS FOR (fm:FaultMode) ON (fm.name);
CREATE INDEX fault_mode_severity IF NOT EXISTS FOR (fm:FaultMode) ON (fm.severity);
CREATE INDEX event_equipment_type IF NOT EXISTS FOR (e:Event) ON (e.equipmentType);
CREATE INDEX event_type IF NOT EXISTS FOR (e:Event) ON (e.type);
CREATE INDEX fused_causes_timestamp IF NOT EXISTS FOR ()-[r:FUSED_CAUSES]->() ON (r.fusionTimestamp);
CREATE INDEX fused_causes_conflict_resolved IF NOT EXISTS FOR ()-[r:FUSED_CAUSES]->() ON (r.conflictResolved);


// ============================================================================
// 第二部分：设备类型节点
// ============================================================================

// 电动机
CREATE (et:EquipmentType {
    id: 'electric_motor',
    name: '电动机',
    category: '旋转设备',
    description: '各类电动机设备',
    failure_rate: 0.05,
    maintenance_interval_months: 12,
    created_at: datetime()
});

// 离心泵
CREATE (et:EquipmentType {
    id: 'centrifugal_pump',
    name: '离心泵',
    category: '流体设备',
    description: '离心式泵类设备',
    failure_rate: 0.08,
    maintenance_interval_months: 6,
    created_at: datetime()
});

// 减速机
CREATE (et:EquipmentType {
    id: 'gearbox',
    name: '减速机',
    category: '传动设备',
    description: '齿轮箱/减速器设备',
    failure_rate: 0.06,
    maintenance_interval_months: 12,
    created_at: datetime()
});


// ============================================================================
// 第三部分：故障模式节点
// ============================================================================

// 过热
CREATE (fm:FaultMode {
    id: 'fm_overheating',
    name: '过热',
    description: '设备温度超过正常工作范围',
    severity: 'HIGH',
    detectability: 'MEDIUM',
    common_causes: ['冷却系统故障', '负载过大', '轴承润滑不良'],
    created_at: datetime()
});

// 异常振动
CREATE (fm:FaultMode {
    id: 'fm_vibration',
    name: '异常振动',
    description: '设备运行时振动异常',
    severity: 'MEDIUM',
    detectability: 'HIGH',
    common_causes: ['轴承磨损', '不平衡', '松动'],
    created_at: datetime()
});

// 堵塞
CREATE (fm:FaultMode {
    id: 'fm_blockage',
    name: '堵塞',
    description: '流体或物料通道堵塞',
    severity: 'HIGH',
    detectability: 'LOW',
    common_causes: ['异物进入', '沉积物积累', '滤网堵塞'],
    created_at: datetime()
});

// 无法启动
CREATE (fm:FaultMode {
    id: 'fm_startup_failure',
    name: '无法启动',
    description: '设备无法正常启动',
    severity: 'CRITICAL',
    detectability: 'HIGH',
    common_causes: ['电源故障', '电机损坏', '控制系统故障'],
    created_at: datetime()
});

// 效率下降
CREATE (fm:FaultMode {
    id: 'fm_efficiency_loss',
    name: '效率下降',
    description: '设备运行效率低于正常水平',
    severity: 'MEDIUM',
    detectability: 'LOW',
    common_causes: ['磨损', '污染', '老化'],
    created_at: datetime()
});


// ============================================================================
// 第四部分：故障模板节点
// ============================================================================

// 电动机故障模板
CREATE (ft:FaultTemplate {
    id: 'template_electric_motor',
    name: '电动机故障模板',
    equipment_type: 'electric_motor',
    top_events: ['电机过热', '电机振动异常', '电机无法启动', '电机效率下降'],
    recommended_gates: ['OR'],
    difficulty_level: 'MEDIUM',
    created_at: datetime()
});

// 离心泵故障模板
CREATE (ft:FaultTemplate {
    id: 'template_centrifugal_pump',
    name: '离心泵故障模板',
    equipment_type: 'centrifugal_pump',
    top_events: ['泵无法启动', '流量不足', '泵振动过大', '泵过热'],
    recommended_gates: ['OR', 'AND'],
    difficulty_level: 'MEDIUM',
    created_at: datetime()
});


// ============================================================================
// 第五部分：设备类型与故障模式的关系
// ============================================================================

// 电动机 - 过热
MATCH (et:EquipmentType {id: 'electric_motor'})
MATCH (fm:FaultMode {id: 'fm_overheating'})
CREATE (fm)-[:AFFECTS {
    id: 'rel_motor_overheating',
    confidence: 0.9,
    source_type: 'equipment_manual',
    created_at: datetime()
}]->(et);

// 电动机 - 异常振动
MATCH (et:EquipmentType {id: 'electric_motor'})
MATCH (fm:FaultMode {id: 'fm_vibration'})
CREATE (fm)-[:AFFECTS {
    id: 'rel_motor_vibration',
    confidence: 0.85,
    source_type: 'equipment_manual',
    created_at: datetime()
}]->(et);

// 离心泵 - 堵塞
MATCH (et:EquipmentType {id: 'centrifugal_pump'})
MATCH (fm:FaultMode {id: 'fm_blockage'})
CREATE (fm)-[:AFFECTS {
    id: 'rel_pump_blockage',
    confidence: 0.88,
    source_type: 'maintenance_record',
    created_at: datetime()
}]->(et);


// ============================================================================
// 第六部分：故障模板与故障模式的关系
// ============================================================================

MATCH (ft:FaultTemplate {id: 'template_electric_motor'})
MATCH (fm:FaultMode {id: 'fm_overheating'})
CREATE (ft)-[:INCLUDES {
    id: 'rel_template_motor_overheating',
    mandatory: true,
    probability: 0.3
}]->(fm);

MATCH (ft:FaultTemplate {id: 'template_electric_motor'})
MATCH (fm:FaultMode {id: 'fm_vibration'})
CREATE (ft)-[:INCLUDES {
    id: 'rel_template_motor_vibration',
    mandatory: false,
    probability: 0.2
}]->(fm);


// ============================================================================
// 第七部分：样本事件节点（保留原有数据）
// ============================================================================

CREATE (e1:Event {
    id: 'e001',
    name: 'Motor Failure',
    type: 'TOP',
    description: 'Motor not running',
    equipmentType: 'Electric Motor',
    created_at: datetime()
});

CREATE (e2:Event {
    id: 'e002',
    name: 'Power Supply Issue',
    type: 'BASIC',
    description: 'No power to motor',
    equipmentType: 'Electric Motor',
    created_at: datetime()
});

CREATE (e3:Event {
    id: 'e003',
    name: 'Motor Overheating',
    type: 'BASIC',
    description: 'Motor temperature too high',
    equipmentType: 'Electric Motor',
    created_at: datetime()
});

CREATE (e4:Event {
    id: 'e004',
    name: 'Pump Failure',
    type: 'TOP',
    description: 'Pump not operating',
    equipmentType: 'Centrifugal Pump',
    created_at: datetime()
});

CREATE (e5:Event {
    id: 'e005',
    name: 'Impeller Blockage',
    type: 'BASIC',
    description: 'Pump impeller blocked',
    equipmentType: 'Centrifugal Pump',
    created_at: datetime()
});


// ============================================================================
// 第八部分：事件之间的因果关系（原始版本）
// ============================================================================

MATCH (e1:Event {id: 'e001'}), (e2:Event {id: 'e002'})
CREATE (e1)-[:CAUSES {
    id: 'r001',
    gateType: 'OR',
    description: 'Power supply issue causes motor failure',
    confidence: 0.95,
    sourceType: 'equipment_manual',
    sourceDocument: 'Motor_Manual_v1.0',
    created_at: datetime()
}]->(e2);

MATCH (e1:Event {id: 'e001'}), (e3:Event {id: 'e003'})
CREATE (e1)-[:CAUSES {
    id: 'r002',
    gateType: 'OR',
    description: 'Overheating causes motor failure',
    confidence: 0.9,
    sourceType: 'equipment_manual',
    sourceDocument: 'Motor_Manual_v1.0',
    created_at: datetime()
}]->(e3);

MATCH (e4:Event {id: 'e004'}), (e5:Event {id: 'e005'})
CREATE (e4)-[:CAUSES {
    id: 'r003',
    gateType: 'OR',
    description: 'Impeller blockage causes pump failure',
    confidence: 0.85,
    sourceType: 'maintenance_record',
    sourceDocument: 'Pump_Maintenance_Log_2024',
    created_at: datetime()
}]->(e5);


// ============================================================================
// 第九部分：事件因果关系（V2版本 - 扩展版）
// ============================================================================

MATCH (e1:Event {id: 'e001'}), (e2:Event {id: 'e002'})
CREATE (e1)-[:CAUSES_V2 {
    id: 'r001_v2',
    gateType: 'OR',
    description: 'Power supply issue causes motor failure',
    confidence: 0.95,
    sourceType: 'equipment_manual',
    sourceDocument: 'Motor_Manual_v2.0',
    version: 2,
    created_at: datetime()
}]->(e2);


// ============================================================================
// 第十部分：知识融合关系（用于多文档融合溯源）
// ============================================================================

// 融合多个来源的因果关系
MATCH (e1:Event {id: 'e001'}), (e2:Event {id: 'e003'})
CREATE (e1)-[:FUSED_CAUSES {
    id: 'fused_001',
    originalRelations: ['r002', 'r002_b'],
    fusionMethod: 'credibility_weighted',
    fusedConfidence: 0.92,
    sourceTypes: ['equipment_manual', 'theory_paper', 'maintenance_record'],
    sourceDocuments: ['Motor_Manual_v2.0', 'IEEE_Motor_Reliability_Paper', '2024_Maintenance_Log'],
    fusionTimestamp: datetime(),
    conflictResolved: true,
    conflictTypes: ['OMISSION']
}]->(e2);


// ============================================================================
// 第十一部分：设备类型与事件的关系
// ============================================================================

MATCH (et:EquipmentType {id: 'electric_motor'})
MATCH (e:Event {equipmentType: 'Electric Motor'})
CREATE (e)-[:BELONGS_TO {
    id: 'rel_event_belongs_motor_' + e.id,
    created_at: datetime()
}]->(et);

MATCH (et:EquipmentType {id: 'centrifugal_pump'})
MATCH (e:Event {equipmentType: 'Centrifugal Pump'})
CREATE (e)-[:BELONGS_TO {
    id: 'rel_event_belongs_pump_' + e.id,
    created_at: datetime()
}]->(et);


// ============================================================================
// 第十二部分：返回创建结果
// ============================================================================

RETURN 'Neo4j Knowledge Graph Initialized Successfully' AS status,
       count{(MATCH (e:Event) RETURN e}) AS eventCount,
       count{(MATCH (et:EquipmentType) RETURN et}) AS equipmentTypeCount,
       count{(MATCH (fm:FaultMode) RETURN fm}) AS faultModeCount,
       count{(MATCH (ft:FaultTemplate) RETURN ft}) AS faultTemplateCount;
