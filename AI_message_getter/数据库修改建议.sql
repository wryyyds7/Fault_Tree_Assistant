================================================================================
                    故障树智能生成系统 - 数据库修改建议
================================================================================

【修改时间】2026-03-26
【分析范围】Java后端(MySQL/Oracle) + Python服务 + Neo4j知识图谱

================================================================================
一、现状分析
================================================================================

┌─────────────────────────────────────────────────────────────────────────────────┐
│                              现有数据库概览                                       │
└─────────────────────────────────────────────────────────────────────────────────┘

  服务                          │ 数据库    │ 技术栈   │ 用途
  ─────────────────────────────┼──────────┼─────────┼────────────────────
  vector-store-service          │ fta_db   │ MySQL   │ 向量存储
  fault-tree-editor-service     │ fta_db   │ MySQL   │ 故障树持久化
  feedback-learning-service     │ feedback_db│ Oracle │ 反馈数据
  auth-service                 │ auth_db   │ Oracle  │ 用户认证
  knowledge-graph-service       │ (Neo4j)  │ Neo4j   │ 知识图谱
  rag-generation-service        │ (内存)    │ Python  │ RAG生成
  fusion-service               │ (无)      │ Python  │ 多文档融合
  evaluation-service           │ (文件)    │ Python  │ 评估服务


================================================================================
二、问题总结：实体类与数据库不匹配
================================================================================

┌─────────────────────────────────────────────────────────────────────────────────┐
│  ⚠️ 严重问题：实体类定义的字段在 Mapper XML 中未映射！                            │
└─────────────────────────────────────────────────────────────────────────────────┘

【问题1】DocumentMetadata 实体类 [L14-18] 有但 Mapper 缺失的字段：

  ┌─────────────────────────────────────────┬──────────────────────────────────────┐
  │ 字段名                                  │ 说明                                  │
  ├─────────────────────────────────────────┼──────────────────────────────────────┤
  │ source_type                             │ 来源类型（设备手册/维修记录/行业标准） │
  │ credibility_weight                      │ 可信度权重（0.5-1.2）                 │
  │ persist_to_knowledge_base               │ 是否持久化到知识图谱                  │
  │ is_temporary                            │ 是否临时文档                          │
  │ expires_at                              │ 过期时间                              │
  └─────────────────────────────────────────┴──────────────────────────────────────┘

【问题2】ParagraphMetadata 实体类 [L17-18] 有但 Mapper 缺失的字段：

  ┌─────────────────────────────────────────┬──────────────────────────────────────┐
  │ 字段名                                  │ 说明                                  │
  ├─────────────────────────────────────────┼──────────────────────────────────────┤
  │ source_type                             │ 段落来源类型                          │
  │ credibility_weight                      │ 段落可信度权重                        │
  └─────────────────────────────────────────┴──────────────────────────────────────┘

【问题3】feedback_db.sql [L14-16] 中 feedback 表缺失的字段：

  ┌─────────────────────────────────────────┬──────────────────────────────────────┐
  │ 字段名                                  │ 说明                                  │
  ├─────────────────────────────────────────┼──────────────────────────────────────┤
  │ accuracy_score                          │ 准确性评分 (0-1)                      │
  │ completeness_score                      │ 完整性评分 (0-1)                      │
  │ clarity_score                           │ 清晰度评分 (0-1)                      │
  └─────────────────────────────────────────┴──────────────────────────────────────┘


================================================================================
三、数据库修改 SQL（按服务分类）
================================================================================

┌─────────────────────────────────────────────────────────────────────────────────┐
│  方案选择说明                                                                    │
├─────────────────────────────────────────────────────────────────────────────────┤
│  • 如果是全新部署：执行【完整SQL】部分的 CREATE TABLE                           │
│  • 如果是增量部署：执行【增量ALTER】部分的 ALTER TABLE ADD COLUMN                │
│  • 建议：先在测试环境验证，确认无误后再在生产环境执行                             │
└─────────────────────────────────────────────────────────────────────────────────┘


================================================================================
【A】MySQL 修改（vector-store-service）
================================================================================

┌─────────────────────────────────────────────────────────────────────────────────┐
│  MySQL - fta_db 数据库                                                          │
│  建议库名：fta_vector_store                                                     │
└─────────────────────────────────────────────────────────────────────────────────┘


-- ============================================================================
-- A1. document_metadata 表 - 新增字段
-- ============================================================================

ALTER TABLE document_metadata
ADD COLUMN source_type VARCHAR(50) DEFAULT 'unknown' COMMENT '文档来源类型：industry_standard/equipment_manual/theory_paper/maintenance_record/user_feedback/unknown',
ADD COLUMN credibility_weight DECIMAL(3,2) DEFAULT 0.50 COMMENT '可信度权重：行业标准1.2，设备手册1.0，理论论文0.9，维修记录0.8，用户反馈0.6，未知0.5',
ADD COLUMN persist_to_knowledge_base TINYINT(1) DEFAULT 0 COMMENT '是否持久化到知识图谱：0-否，1-是',
ADD COLUMN is_temporary TINYINT(1) DEFAULT 1 COMMENT '是否临时文档：0-否，1-是',
ADD COLUMN expires_at DATETIME DEFAULT NULL COMMENT '过期时间，临时文档过期后可被清理',
ADD INDEX idx_source_type (source_type),
ADD INDEX idx_persist_to_kb (persist_to_knowledge_base),
ADD INDEX idx_is_temporary (is_temporary),
ADD INDEX idx_expires_at (expires_at);


-- ============================================================================
-- A2. paragraph_metadata 表 - 新增字段
-- ============================================================================

ALTER TABLE paragraph_metadata
ADD COLUMN source_type VARCHAR(50) DEFAULT 'unknown' COMMENT '段落来源类型：industry_standard/equipment_manual/theory_paper/maintenance_record/user_feedback/unknown',
ADD COLUMN credibility_weight DECIMAL(3,2) DEFAULT 0.50 COMMENT '段落可信度权重，用于多文档融合时冲突消解',
ADD INDEX idx_source_type (source_type),
ADD INDEX idx_credibility_weight (credibility_weight);


-- ============================================================================
-- A3. vector_store 表 - 可选优化字段
-- ============================================================================

ALTER TABLE vector_store
ADD COLUMN embedding_model VARCHAR(50) DEFAULT 'BGE-M3' COMMENT '生成向量的模型名称',
ADD COLUMN created_by VARCHAR(50) DEFAULT 'system' COMMENT '创建者：system-系统，user_xxx-用户';


-- ============================================================================
-- A4. 新增：融合结果记录表（可选，用于审计）
-- ============================================================================

CREATE TABLE IF NOT EXISTS fusion_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fusion_id VARCHAR(100) NOT NULL UNIQUE COMMENT '融合任务ID',
    source_doc_ids JSON NOT NULL COMMENT '源文档ID列表',
    fused_paragraphs_count INT DEFAULT 0 COMMENT '融合后段落数',
    total_clusters INT DEFAULT 0 COMMENT '聚类总数',
    total_conflicts INT DEFAULT 0 COMMENT '检测到的冲突总数',
    resolved_conflicts INT DEFAULT 0 COMMENT '已解决的冲突数',
    fusion_duration_ms INT DEFAULT 0 COMMENT '融合耗时(毫秒)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_created_at (created_at),
    INDEX idx_fusion_id (fusion_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多文档融合结果记录表';


================================================================================
【B】MySQL 修改（fault-tree-editor-service）
================================================================================

┌─────────────────────────────────────────────────────────────────────────────────┐
│  MySQL - fta_db 数据库                                                          │
│  建议库名：fta_editor                                                           │
└─────────────────────────────────────────────────────────────────────────────────┘


-- ============================================================================
-- B1. fault_trees 表 - 新增字段（支持完整功能链）
-- ============================================================================

ALTER TABLE fault_trees
ADD COLUMN version INT DEFAULT 1 COMMENT '故障树版本号',
ADD COLUMN validation_status VARCHAR(20) DEFAULT 'PENDING' COMMENT '校验状态：PENDING-待校验，VALID-有效，INVALID-无效',
ADD COLUMN validation_message TEXT DEFAULT NULL COMMENT '校验失败的具体原因',
ADD COLUMN source_doc_ids JSON DEFAULT NULL COMMENT '生成该故障树的源文档ID列表',
ADD COLUMN source_detail JSON DEFAULT NULL COMMENT '每个节点的来源详情：{eventId: {sourceType, documentName, paragraphId, confidence}}',
ADD COLUMN publish_status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '发布状态：DRAFT-草稿，PUBLISHED-已发布，ARCHIVED-已归档',
ADD COLUMN equipment_type VARCHAR(100) DEFAULT NULL COMMENT '设备类型（冗余字段，用于快速检索）',
ADD COLUMN top_event VARCHAR(255) DEFAULT NULL COMMENT '顶事件名称（冗余字段，用于快速检索）',
ADD COLUMN fusion_statistics JSON DEFAULT NULL COMMENT '融合统计信息：{totalClusters, totalConflicts, resolvedConflicts}',
ADD COLUMN generated_by VARCHAR(50) DEFAULT 'AI' COMMENT '生成方式：AI-人工智能，MANUAL-人工创建，HYBRID-混合',
ADD COLUMN template_id VARCHAR(100) DEFAULT NULL COMMENT '使用的模板ID（如果有）',
ADD COLUMN INDEX idx_validation_status (validation_status),
ADD INDEX idx_publish_status (publish_status),
ADD INDEX idx_equipment_type (equipment_type),
ADD INDEX idx_top_event (top_event),
ADD INDEX idx_generated_by (generated_by),
ADD INDEX idx_created_by (created_by);


-- ============================================================================
-- B2. 新增：故障树节点表（替代 JSON blob，便于查询和索引）
-- ============================================================================

CREATE TABLE IF NOT EXISTS fault_tree_nodes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    node_id VARCHAR(100) NOT NULL COMMENT '节点ID',
    tree_id VARCHAR(100) NOT NULL COMMENT '所属故障树ID',
    node_name VARCHAR(255) NOT NULL COMMENT '节点名称',
    node_type VARCHAR(20) NOT NULL COMMENT '节点类型：TOP-顶事件，INTERMEDIATE-中间事件，BASIC-底事件',
    gate_type VARCHAR(20) DEFAULT NULL COMMENT '逻辑门类型：AND/OR/XOR/K-of-N（中间事件或顶事件时）',
    description TEXT DEFAULT NULL COMMENT '节点描述',
    confidence DECIMAL(3,2) DEFAULT 0.85 COMMENT '置信度',
    source_type VARCHAR(50) DEFAULT 'AI_GENERATED' COMMENT '来源类型',
    source_detail JSON DEFAULT NULL COMMENT '来源详情',
    paragraph_id VARCHAR(100) DEFAULT NULL COMMENT '来源段落ID',
    verification_status VARCHAR(20) DEFAULT 'PENDING' COMMENT '验证状态：PENDING待验证，VERIFIED已验证，REJECTED已拒绝',
    verified_by VARCHAR(50) DEFAULT NULL COMMENT '验证人',
    verified_at DATETIME DEFAULT NULL COMMENT '验证时间',
    position_x INT DEFAULT 0 COMMENT '前端画布X坐标',
    position_y INT DEFAULT 0 COMMENT '前端画布Y坐标',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_node_tree (node_id, tree_id),
    INDEX idx_tree_id (tree_id),
    INDEX idx_node_type (node_type),
    INDEX idx_parent_nodes (node_id),
    CONSTRAINT fk_ftn_tree FOREIGN KEY (tree_id) REFERENCES fault_trees(tree_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='故障树节点表';


-- ============================================================================
-- B3. 新增：故障树节点关系表
-- ============================================================================

CREATE TABLE IF NOT EXISTS fault_tree_edges (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    edge_id VARCHAR(100) NOT NULL UNIQUE COMMENT '边ID',
    tree_id VARCHAR(100) NOT NULL COMMENT '所属故障树ID',
    source_node_id VARCHAR(100) NOT NULL COMMENT '源节点ID（父节点）',
    target_node_id VARCHAR(100) NOT NULL COMMENT '目标节点ID（子节点）',
    gate_type VARCHAR(20) DEFAULT 'OR' COMMENT '逻辑门类型',
    probability DECIMAL(5,4) DEFAULT NULL COMMENT '该边的概率（可选）',
    description VARCHAR(500) DEFAULT NULL COMMENT '关系描述',
    confidence DECIMAL(3,2) DEFAULT 0.90 COMMENT '关系置信度',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tree_id (tree_id),
    INDEX idx_source_node (source_node_id),
    INDEX idx_target_node (target_node_id),
    UNIQUE KEY uk_edge_tree (edge_id, tree_id),
    CONSTRAINT fk_fte_tree FOREIGN KEY (tree_id) REFERENCES fault_trees(tree_id) ON DELETE CASCADE,
    CONSTRAINT fk_fte_source FOREIGN KEY (source_node_id, tree_id) REFERENCES fault_tree_nodes(node_id, tree_id) ON DELETE CASCADE,
    CONSTRAINT fk_fte_target FOREIGN KEY (target_node_id, tree_id) REFERENCES fault_tree_nodes(node_id, tree_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='故障树节点关系表';


-- ============================================================================
-- B4. 新增：故障树版本历史表
-- ============================================================================

CREATE TABLE IF NOT EXISTS fault_tree_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id VARCHAR(100) NOT NULL UNIQUE COMMENT '版本ID',
    tree_id VARCHAR(100) NOT NULL COMMENT '故障树ID',
    version_number INT NOT NULL COMMENT '版本号',
    tree_data_snapshot LONGTEXT NOT NULL COMMENT '故障树数据快照（JSON）',
    change_summary TEXT DEFAULT NULL COMMENT '变更摘要',
    changed_by VARCHAR(50) NOT NULL COMMENT '变更人',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tree_id (tree_id),
    INDEX idx_version_number (version_number),
    UNIQUE KEY uk_tree_version (tree_id, version_number),
    CONSTRAINT fk_ftv_tree FOREIGN KEY (tree_id) REFERENCES fault_trees(tree_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='故障树版本历史表';


================================================================================
【C】Oracle 修改（feedback-learning-service）
================================================================================

┌─────────────────────────────────────────────────────────────────────────────────┐
│  Oracle - feedback_db 数据库                                                   │
│  建议用户名：feedback_user                                                     │
└─────────────────────────────────────────────────────────────────────────────────┘


-- ============================================================================
-- C1. feedback 表 - 新增评分字段
-- ============================================================================

ALTER TABLE feedback
ADD (
    accuracy_score NUMBER(3,2) DEFAULT 0 CHECK (accuracy_score >= 0 AND accuracy_score <= 1),
    completeness_score NUMBER(3,2) DEFAULT 0 CHECK (completeness_score >= 0 AND completeness_score <= 1),
    clarity_score NUMBER(3,2) DEFAULT 0 CHECK (clarity_score >= 0 AND clarity_score <= 1)
);


-- ============================================================================
-- C2. feedback 表 - 新增反馈类型和状态字段
-- ============================================================================

ALTER TABLE feedback
ADD (
    feedback_type VARCHAR(20) DEFAULT 'RATING' CHECK (feedback_type IN ('RATING', 'CORRECTION', 'SUGGESTION', 'BUG_REPORT')),
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PROCESSED', 'REJECTED', 'APPLIED')),
    processed_by VARCHAR(50) DEFAULT NULL,
    processed_at TIMESTAMP DEFAULT NULL,
    applied_to_model NUMBER(1) DEFAULT 0 CHECK (applied_to_model IN (0, 1))
);


-- ============================================================================
-- C3. 新增：反馈处理记录表
-- ============================================================================

CREATE TABLE feedback_process_log (
    id NUMBER(19,0) PRIMARY KEY,
    process_id VARCHAR2(100) UNIQUE NOT NULL,
    feedback_id VARCHAR2(100) NOT NULL,
    action VARCHAR2(50) NOT NULL,
    operator VARCHAR2(50) NOT NULL,
    details CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE SEQUENCE feedback_process_log_seq START WITH 1 INCREMENT BY 1 NOMAXVALUE;

CREATE OR REPLACE TRIGGER feedback_process_log_before_insert
BEFORE INSERT ON feedback_process_log
FOR EACH ROW
BEGIN
    IF :NEW.id IS NULL THEN
        SELECT feedback_process_log_seq.NEXTVAL INTO :NEW.id FROM DUAL;
    END IF;
    IF :NEW.created_at IS NULL THEN
        :NEW.created_at := CURRENT_TIMESTAMP;
    END IF;
END;
/


================================================================================
【D】Oracle 修改（auth-service）
================================================================================

┌─────────────────────────────────────────────────────────────────────────────────┐
│  Oracle - auth_db 数据库                                                       │
│  建议用户名：auth_user                                                         │
└─────────────────────────────────────────────────────────────────────────────────┘


-- ============================================================================
-- D1. users 表 - 新增安全相关字段
-- ============================================================================

ALTER TABLE users
ADD (
    last_login_time TIMESTAMP DEFAULT NULL,
    password_change_time TIMESTAMP DEFAULT NULL,
    failed_login_attempts NUMBER(3,0) DEFAULT 0,
    lock_time TIMESTAMP DEFAULT NULL,
    password_history CLOB DEFAULT NULL
);


-- ============================================================================
-- D2. 新增：用户会话表（可选，用于token管理）
-- ============================================================================

CREATE TABLE user_sessions (
    id NUMBER(19,0) PRIMARY KEY,
    session_id VARCHAR2(100) UNIQUE NOT NULL,
    user_id VARCHAR2(50) NOT NULL,
    token VARCHAR2(500) NOT NULL,
    refresh_token VARCHAR2(500),
    device_info VARCHAR2(200),
    ip_address VARCHAR2(50),
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_us_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE SEQUENCE user_sessions_seq START WITH 1 INCREMENT BY 1 NOMAXVALUE;

CREATE OR REPLACE TRIGGER user_sessions_before_insert
BEFORE INSERT ON user_sessions
FOR EACH ROW
BEGIN
    IF :NEW.id IS NULL THEN
        SELECT user_sessions_seq.NEXTVAL INTO :NEW.id FROM DUAL;
    END IF;
    IF :NEW.created_at IS NULL THEN
        :NEW.created_at := CURRENT_TIMESTAMP;
    END IF;
END;
/


================================================================================
【E】Neo4j 知识图谱修改
================================================================================

┌─────────────────────────────────────────────────────────────────────────────────┐
│  Neo4j - knowledge-graph-service                                               │
└─────────────────────────────────────────────────────────────────────────────────┘


-- ============================================================================
-- E1. 创建设备类型节点和索引
-- ============================================================================

CREATE CONSTRAINT IF NOT EXISTS FOR (et:EquipmentType) REQUIRE et.id IS UNIQUE;
CREATE CONSTRAINT IF NOT EXISTS FOR (ft:FaultTemplate) REQUIRE ft.id IS UNIQUE;

CREATE INDEX equipment_type_name IF NOT EXISTS FOR (et:EquipmentType) ON (et.name);
CREATE INDEX fault_mode_name IF NOT EXISTS FOR (fm:FaultMode) ON (fm.name);


-- ============================================================================
-- E2. 创建设备类型节点
-- ============================================================================

CREATE (et:EquipmentType {
    id: 'electric_motor',
    name: '电动机',
    category: '旋转设备',
    description: '各类电动机设备',
    failure_rate: 0.05,
    maintenance_interval_months: 12
});

CREATE (et:EquipmentType {
    id: 'centrifugal_pump',
    name: '离心泵',
    category: '流体设备',
    description: '离心式泵类设备',
    failure_rate: 0.08,
    maintenance_interval_months: 6
});

CREATE (et:EquipmentType {
    id: 'gearbox',
    name: '减速机',
    category: '传动设备',
    description: '齿轮箱/减速器设备',
    failure_rate: 0.06,
    maintenance_interval_months: 12
});


-- ============================================================================
-- E3. 创建故障模式节点
-- ============================================================================

CREATE (fm:FaultMode {
    id: 'fm_overheating',
    name: '过热',
    description: '设备温度超过正常工作范围',
    severity: 'HIGH',
    detectability: 'MEDIUM'
});

CREATE (fm:FaultMode {
    id: 'fm_vibration',
    name: '异常振动',
    description: '设备运行时振动异常',
    severity: 'MEDIUM',
    detectability: 'HIGH'
});

CREATE (fm:FaultMode {
    id: 'fm_blockage',
    name: '堵塞',
    description: '流体或物料通道堵塞',
    severity: 'HIGH',
    detectability: 'LOW'
});


-- ============================================================================
-- E4. 创建因果关系模板
-- ============================================================================

MATCH (overheating:FaultMode {id: 'fm_overheating'})
MATCH (motor:EquipmentType {id: 'electric_motor'})
CREATE (overheating)-[:AFFECTS {confidence: 0.9}]->(motor);


-- ============================================================================
-- E5. 扩展现有事件节点的关系类型
-- ============================================================================

MATCH (e1:Event {id: 'e001'}), (e2:Event {id: 'e002'})
CREATE (e1)-[:CAUSES_V2 {
    id: 'r001_v2',
    gateType: 'OR',
    description: 'Power supply issue causes motor failure',
    confidence: 0.95,
    sourceType: 'equipment_manual',
    sourceDocument: 'Motor_Manual_v2.0',
    createdAt: datetime()
}]->(e2);


-- ============================================================================
-- E6. 创建知识融合关系（用于多文档融合溯源）
-- ============================================================================

MATCH (e1:Event {id: 'e001'}), (e2:Event {id: 'e003'})
CREATE (e1)-[:FUSED_CAUSES {
    id: 'fused_001',
    originalRelations: ['r001', 'r002'],
    fusionMethod: 'credibility_weighted',
    fusedConfidence: 0.92,
    sourceTypes: ['equipment_manual', 'theory_paper']
}]->(e2);


================================================================================
【F】Python 服务数据库（新增）
================================================================================

┌─────────────────────────────────────────────────────────────────────────────────┐
│  方案说明：                                                                     │
│  • 推荐使用 PostgreSQL + PGVector（统一数据库，支持向量索引）                    │
│  • 也可使用 SQLite（轻量级，仅适合单机部署）                                     │
│  • 评估历史和黄金标准建议持久化到数据库，便于管理和查询                          │
└─────────────────────────────────────────────────────────────────────────────────┘


-- ============================================================================
-- F1. 评估服务数据库 - 评估历史表
-- ============================================================================

CREATE TABLE evaluation_history (
    id SERIAL PRIMARY KEY,
    evaluation_id VARCHAR(100) UNIQUE NOT NULL,
    tree_id VARCHAR(100) NOT NULL,
    gold_standard_id VARCHAR(100),
    overall_score DECIMAL(5,4) NOT NULL,
    structure_accuracy DECIMAL(5,4) NOT NULL,
    event_precision DECIMAL(5,4) NOT NULL,
    event_recall DECIMAL(5,4) NOT NULL,
    event_f1 DECIMAL(5,4) NOT NULL,
    relation_accuracy DECIMAL(5,4) NOT NULL,
    gate_accuracy DECIMAL(5,4) NOT NULL,
    missing_events JSON,
    extra_events JSON,
    wrong_relations JSON,
    detailed_report JSON,
    evaluation_duration_ms INTEGER,
    evaluated_by VARCHAR(50) DEFAULT 'SYSTEM',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_evaluation_id (evaluation_id),
    INDEX idx_tree_id (tree_id),
    INDEX idx_overall_score (overall_score),
    INDEX idx_created_at (created_at)
);

COMMENT ON TABLE evaluation_history IS '故障树评估历史记录表';


-- ============================================================================
-- F2. 评估服务数据库 - 黄金标准表（替代文件系统）
-- ============================================================================

CREATE TABLE gold_standards (
    id SERIAL PRIMARY KEY,
    tree_id VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    equipment_type VARCHAR(100) NOT NULL,
    fault_mode VARCHAR(100),
    difficulty_level VARCHAR(20) CHECK (difficulty_level IN ('EASY', 'MEDIUM', 'HARD', 'EXPERT')),
    tree_data JSON NOT NULL,
    metadata JSON,
    version INTEGER DEFAULT 1,
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DEPRECATED', 'VALIDATING')),
    created_by VARCHAR(50) NOT NULL,
    validated_by VARCHAR(50),
    validated_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tree_id (tree_id),
    INDEX idx_equipment_type (equipment_type),
    INDEX idx_fault_mode (fault_mode),
    INDEX idx_status (status)
);

COMMENT ON TABLE gold_standards IS '故障树黄金标准表（专家标准答案）';


-- ============================================================================
-- F3. 融合服务数据库 - 冲突记录表（可选，用于审计分析）
-- ============================================================================

CREATE TABLE conflict_records (
    id SERIAL PRIMARY KEY,
    conflict_id VARCHAR(100) UNIQUE NOT NULL,
    cluster_id VARCHAR(100) NOT NULL,
    conflict_type VARCHAR(20) NOT NULL CHECK (conflict_type IN ('CONTRADICTION', 'OMISSION', 'DISCREPANCY')),
    severity VARCHAR(10) NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    description TEXT,
    involved_paragraphs JSON NOT NULL,
    resolution VARCHAR(50),
    resolved_by VARCHAR(20) DEFAULT 'AUTO' CHECK (resolved_by IN ('AUTO', 'MANUAL', 'UNRESOLVED')),
    resolution_details TEXT,
    fusion_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conflict_id (conflict_id),
    INDEX idx_cluster_id (cluster_id),
    INDEX idx_conflict_type (conflict_type),
    INDEX idx_severity (severity)
);

COMMENT ON TABLE conflict_records IS '多文档融合冲突记录表';


================================================================================
四、完整建表SQL（新建服务时使用）
================================================================================

┌─────────────────────────────────────────────────────────────────────────────────┐
│  【完整建表SQL】如果是从零开始创建数据库，执行以下SQL                              │
│  如果已有数据库，执行第三节的 ALTER TABLE 语句                                   │
└─────────────────────────────────────────────────────────────────────────────────┘


-- ============================================================================
-- MySQL 完整建表（vector-store-service 使用）
-- ============================================================================

CREATE DATABASE IF NOT EXISTS fta_vector_store DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE fta_vector_store;

CREATE TABLE document_metadata (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id VARCHAR(100) UNIQUE NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(50),
    page_count INT,
    upload_time DATETIME,
    equipment_type VARCHAR(100),
    status VARCHAR(20) DEFAULT 'PENDING',
    source_type VARCHAR(50) DEFAULT 'unknown',
    credibility_weight DECIMAL(3,2) DEFAULT 0.50,
    persist_to_knowledge_base TINYINT(1) DEFAULT 0,
    is_temporary TINYINT(1) DEFAULT 1,
    expires_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_doc_id (doc_id),
    INDEX idx_source_type (source_type),
    INDEX idx_persist_to_kb (persist_to_knowledge_base),
    INDEX idx_is_temporary (is_temporary)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档元数据表';

CREATE TABLE paragraph_metadata (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    paragraph_id VARCHAR(100) UNIQUE NOT NULL,
    doc_id VARCHAR(100) NOT NULL,
    section_title VARCHAR(255),
    page_number INT,
    paragraph_number INT,
    text_length INT,
    keywords VARCHAR(500),
    confidence_score DECIMAL(3,2),
    content TEXT NOT NULL,
    source_type VARCHAR(50) DEFAULT 'unknown',
    credibility_weight DECIMAL(3,2) DEFAULT 0.50,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_paragraph_id (paragraph_id),
    INDEX idx_doc_id (doc_id),
    INDEX idx_source_type (source_type),
    CONSTRAINT fk_pm_doc FOREIGN KEY (doc_id) REFERENCES document_metadata(doc_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='段落元数据表';

CREATE TABLE vector_store (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vector_id VARCHAR(100) UNIQUE NOT NULL,
    paragraph_id VARCHAR(100) NOT NULL,
    doc_id VARCHAR(100) NOT NULL,
    vector_data TEXT NOT NULL,
    vector_dimension INT DEFAULT 768,
    embedding_model VARCHAR(50) DEFAULT 'BGE-M3',
    similarity_score DECIMAL(5,4),
    created_by VARCHAR(50) DEFAULT 'system',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_vector_id (vector_id),
    INDEX idx_paragraph_id (paragraph_id),
    INDEX idx_doc_id (doc_id),
    CONSTRAINT fk_vs_paragraph FOREIGN KEY (paragraph_id) REFERENCES paragraph_metadata(paragraph_id) ON DELETE CASCADE,
    CONSTRAINT fk_vs_doc FOREIGN KEY (doc_id) REFERENCES document_metadata(doc_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='向量存储表';


-- ============================================================================
-- MySQL 完整建表（fault-tree-editor-service 使用）
-- ============================================================================

CREATE DATABASE IF NOT EXISTS fta_editor DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE fta_editor;

CREATE TABLE fault_trees (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tree_id VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    equipment_type VARCHAR(100),
    top_event VARCHAR(255),
    tree_data LONGTEXT NOT NULL,
    version INT DEFAULT 1,
    validation_status VARCHAR(20) DEFAULT 'PENDING',
    validation_message TEXT,
    source_doc_ids JSON,
    source_detail JSON,
    publish_status VARCHAR(20) DEFAULT 'DRAFT',
    generated_by VARCHAR(50) DEFAULT 'AI',
    template_id VARCHAR(100),
    fusion_statistics JSON,
    created_by VARCHAR(50) NOT NULL,
    updated_by VARCHAR(50) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tree_id (tree_id),
    INDEX idx_validation_status (validation_status),
    INDEX idx_publish_status (publish_status),
    INDEX idx_equipment_type (equipment_type),
    INDEX idx_top_event (top_event),
    INDEX idx_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='故障树主表';

CREATE TABLE fault_tree_nodes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    node_id VARCHAR(100) NOT NULL,
    tree_id VARCHAR(100) NOT NULL,
    node_name VARCHAR(255) NOT NULL,
    node_type VARCHAR(20) NOT NULL,
    gate_type VARCHAR(20),
    description TEXT,
    confidence DECIMAL(3,2) DEFAULT 0.85,
    source_type VARCHAR(50) DEFAULT 'AI_GENERATED',
    source_detail JSON,
    paragraph_id VARCHAR(100),
    verification_status VARCHAR(20) DEFAULT 'PENDING',
    verified_by VARCHAR(50),
    verified_at DATETIME,
    position_x INT DEFAULT 0,
    position_y INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_node_tree (node_id, tree_id),
    INDEX idx_tree_id (tree_id),
    INDEX idx_node_type (node_type),
    CONSTRAINT fk_ftn_tree FOREIGN KEY (tree_id) REFERENCES fault_trees(tree_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='故障树节点表';

CREATE TABLE fault_tree_edges (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    edge_id VARCHAR(100) UNIQUE NOT NULL,
    tree_id VARCHAR(100) NOT NULL,
    source_node_id VARCHAR(100) NOT NULL,
    target_node_id VARCHAR(100) NOT NULL,
    gate_type VARCHAR(20) DEFAULT 'OR',
    probability DECIMAL(5,4),
    description VARCHAR(500),
    confidence DECIMAL(3,2) DEFAULT 0.90,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_edge_tree (edge_id, tree_id),
    INDEX idx_tree_id (tree_id),
    INDEX idx_source_node (source_node_id),
    INDEX idx_target_node (target_node_id),
    CONSTRAINT fk_fte_tree FOREIGN KEY (tree_id) REFERENCES fault_trees(tree_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='故障树节点关系表';

CREATE TABLE fault_tree_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id VARCHAR(100) UNIQUE NOT NULL,
    tree_id VARCHAR(100) NOT NULL,
    version_number INT NOT NULL,
    tree_data_snapshot LONGTEXT NOT NULL,
    change_summary TEXT,
    changed_by VARCHAR(50) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tree_id (tree_id),
    INDEX idx_version_number (version_number),
    UNIQUE KEY uk_tree_version (tree_id, version_number),
    CONSTRAINT fk_ftv_tree FOREIGN KEY (tree_id) REFERENCES fault_trees(tree_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='故障树版本历史表';


================================================================================
五、来源类型与可信度权重配置（建议写入配置中心）
================================================================================

┌─────────────────────────────────────────────────────────────────────────────────┐
│  来源类型 (source_type) 与 可信度权重 (credibility_weight) 对照表                │
└─────────────────────────────────────────────────────────────────────────────────┘

  来源类型              │ 可信度权重 │ 说明                  │ 适用场景
  ─────────────────────┼───────────┼──────────────────────┼────────────────────
  industry_standard    │   1.2     │ 行业标准，权威性最高   │ 国标/行标/国际标准
  equipment_manual     │   1.0     │ 设备手册，标准文档     │ 厂家提供的技术手册
  theory_paper        │   0.9     │ 理论论文，有参考价值   │ 学术论文/研究报告
  maintenance_record   │   0.8     │ 维修记录，实践经验     │ 历史维修数据
  user_feedback        │   0.6     │ 用户反馈，可信度较低   │ 用户评价/反馈
  unknown             │   0.5     │ 未知来源（默认）       │ 无法确定来源时

  ⚠️ 注意：这些权重值建议在 Nacos 配置中心统一管理，不要硬编码在代码中


================================================================================
六、数据库修改优先级建议
================================================================================

┌─────────────────────────────────────────────────────────────────────────────────┐
│  【P0 - 必须立即修改】解决实体类与数据库不匹配的问题                              │
└─────────────────────────────────────────────────────────────────────────────────┘
  序号 │ 服务                    │ 修改内容
  ─────┼────────────────────────┼─────────────────────────────────
  1    │ vector-store-service   │ DocumentMetadata 表新增字段
  2    │ vector-store-service   │ ParagraphMetadata 表新增字段
  3    │ feedback-learning      │ feedback 表新增评分字段

┌─────────────────────────────────────────────────────────────────────────────────┐
│  【P1 - 强烈建议】支持完整功能链                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
  序号 │ 服务                    │ 修改内容
  ─────┼────────────────────────┼─────────────────────────────────
  4    │ fault-tree-editor     │ fault_trees 表新增字段（版本/校验状态等）
  5    │ fault-tree-editor     │ 新建 fault_tree_nodes 表（结构化节点）
  6    │ fault-tree-editor     │ 新建 fault_tree_edges 表（关系存储）
  7    │ auth-service          │ users 表新增安全字段

┌─────────────────────────────────────────────────────────────────────────────────┐
│  【P2 - 建议】优化运维和审计                                                     │
└─────────────────────────────────────────────────────────────────────────────────┘
  序号 │ 服务                    │ 修改内容
  ─────┼────────────────────────┼─────────────────────────────────
  8    │ knowledge-graph        │ Neo4j 新增节点类型和关系
  9    │ vector-store-service   │ 新建 fusion_results 表（可选）
  10   │ Python 服务            │ 新建评估服务数据库（可选）

┌─────────────────────────────────────────────────────────────────────────────────┐
│  【P3 - 长期规划】完整迁移到统一数据库                                            │
└─────────────────────────────────────────────────────────────────────────────────┘
  序号 │ 服务                    │ 修改内容
  ─────┼────────────────────────┼─────────────────────────────────
  11   │ Python 评估服务         │ 黄金标准从文件系统迁移到数据库
  12   │ Python 融合服务         │ 冲突记录持久化到数据库


================================================================================
七、相关文件路径索引
================================================================================

┌─────────────────────────────────────────────────────────────────────────────────┐
│  Java 实体类（需要与数据库保持一致）                                             │
└─────────────────────────────────────────────────────────────────────────────────┘

  DocumentMetadata.java:
    📁 vector-store-service/src/main/java/com/cxyaqcdm/fta/vector/entity/DocumentMetadata.java
    ⚠️ 实体类已有 sourceType, credibilityWeight, persistToKnowledgeBase, isTemporary, expiresAt
    ⚠️ Mapper XML 缺失这些字段的映射

  ParagraphMetadata.java:
    📁 vector-store-service/src/main/java/com/cxyaqcdm/fta/vector/entity/ParagraphMetadata.java
    ⚠️ 实体类已有 sourceType, credibilityWeight
    ⚠️ Mapper XML 缺失这些字段的映射

  VectorStore.java:
    📁 vector-store-service/src/main/java/com/cxyaqcdm/fta/vector/entity/VectorStore.java

  FaultTreeEntity.java:
    📁 fault-tree-editor-service/src/main/java/com/cxyaqcdm/fta/editor/entity/FaultTreeEntity.java

  FeedbackEntity.java:
    📁 feedback-learning-service/src/main/java/com/cxyaqcdm/fta/feedback/entity/FeedbackEntity.java


┌─────────────────────────────────────────────────────────────────────────────────┐
│  Mapper XML（需要更新以匹配实体类）                                              │
└─────────────────────────────────────────────────────────────────────────────────┘

  DocumentMetadataMapper.xml:
    📁 vector-store-service/src/main/resources/mapper/DocumentMetadataMapper.xml

  ParagraphMetadataMapper.xml:
    📁 vector-store-service/src/main/resources/mapper/ParagraphMetadataMapper.xml

  VectorStoreMapper.xml:
    📁 vector-store-service/src/main/resources/mapper/VectorStoreMapper.xml


┌─────────────────────────────────────────────────────────────────────────────────┐
│  SQL 文件（已完整同步）                                                          │
└─────────────────────────────────────────────────────────────────────────────────┘

  fta_db.sql:
    📁 sql/fta_db.sql
    ✅ 已完整包含：document_metadata, paragraph_metadata, vector_store, fusion_results, fault_trees, fault_tree_nodes, fault_tree_edges, fault_tree_versions

  feedback_db.sql:
    📁 sql/feedback_db.sql
    ✅ 已包含 accuracy_score, completeness_score, clarity_score 字段

  auth_db.sql:
    📁 sql/auth_db.sql
    ✅ 已完整，包含 users, user_sessions 表

  neo4j_init.cypher:
    📁 sql/neo4j_init.cypher
    ✅ 已补充 FaultMode 约束及 FUSED_CAUSES 相关索引

  evaluation_db.sql:
    📁 sql/evaluation_db.sql (新增)
    ✅ 已创建 evaluation_history, gold_standards, conflict_records, fusion_results, fusion_clusters, source_type_configs 表


┌─────────────────────────────────────────────────────────────────────────────────┐
│  Python 服务（数据库已创建）                                                      │
└─────────────────────────────────────────────────────────────────────────────────┘

  evaluation_db.sql:
    📁 sql/evaluation_db.sql
    ✅ 已创建 PostgreSQL 表结构，支持评估历史和黄金标准持久化

  FusionEngine:
    📁 python-service/industrial_fta_common/fusion/fusion_engine.py
    💡 可选：添加数据库持久化（conflict_records 表已创建）

  FaultTreeEvaluator:
    📁 python-service/industrial_fta_common/evaluation/fault_tree_evaluator.py
    💡 可选：添加数据库持久化（evaluation_history 表已创建）

  GoldStandardManager:
    📁 python-service/industrial_fta_common/evaluation/gold_standard.py
    💡 可选：迁移到数据库（gold_standards 表已创建）


================================================================================
