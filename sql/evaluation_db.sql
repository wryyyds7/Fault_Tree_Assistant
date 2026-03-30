-- ============================================================================
-- 故障树智能生成系统 - PostgreSQL 数据库（Python服务）
-- 服务: evaluation-service, fusion-service
-- ============================================================================

-- 推荐使用 PostgreSQL + PGVector（统一数据库，支持向量索引）
-- 也可使用 SQLite（轻量级，仅适合单机部署）

-- ============================================================================
-- 第一部分：评估服务数据库
-- ============================================================================

-- ============================================================================
-- 1. evaluation_history 表 - 评估历史记录表
-- ============================================================================
CREATE TABLE IF NOT EXISTS evaluation_history (
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
-- 2. gold_standards 表 - 黄金标准表（专家标准答案）
-- ============================================================================
CREATE TABLE IF NOT EXISTS gold_standards (
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
-- 第二部分：融合服务数据库
-- ============================================================================

-- ============================================================================
-- 3. conflict_records 表 - 多文档融合冲突记录表（用于审计分析）
-- ============================================================================
CREATE TABLE IF NOT EXISTS conflict_records (
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

-- ============================================================================
-- 4. fusion_results 表 - 融合结果记录表
-- ============================================================================
CREATE TABLE IF NOT EXISTS fusion_results (
    id SERIAL PRIMARY KEY,
    fusion_id VARCHAR(100) UNIQUE NOT NULL,
    source_doc_ids JSON NOT NULL,
    fused_paragraphs_count INT DEFAULT 0,
    total_clusters INT DEFAULT 0,
    total_conflicts INT DEFAULT 0,
    resolved_conflicts INT DEFAULT 0,
    fusion_duration_ms INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_fusion_id (fusion_id),
    INDEX idx_created_at (created_at)
);

COMMENT ON TABLE fusion_results IS '多文档融合结果记录表';

-- ============================================================================
-- 5. fusion_clusters 表 - 融合聚类记录表
-- ============================================================================
CREATE TABLE IF NOT EXISTS fusion_clusters (
    id SERIAL PRIMARY KEY,
    cluster_id VARCHAR(100) UNIQUE NOT NULL,
    cluster_theme VARCHAR(255),
    paragraph_count INT DEFAULT 0,
    source_types JSON,
    avg_credibility DECIMAL(3,2),
    fusion_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_cluster_id (cluster_id),
    INDEX idx_fusion_id (fusion_id)
);

COMMENT ON TABLE fusion_clusters IS '多文档融合聚类记录表';

-- ============================================================================
-- 第三部分：来源类型与可信度权重配置表
-- ============================================================================

-- ============================================================================
-- 6. source_type_configs 表 - 来源类型配置表
-- ============================================================================
CREATE TABLE IF NOT EXISTS source_type_configs (
    id SERIAL PRIMARY KEY,
    source_type VARCHAR(50) UNIQUE NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    credibility_weight DECIMAL(3,2) NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE source_type_configs IS '来源类型与可信度权重配置表';

-- 插入默认配置
INSERT INTO source_type_configs (source_type, display_name, credibility_weight, description) VALUES
('industry_standard', '行业标准', 1.20, '国标/行标/国际标准，权威性最高'),
('equipment_manual', '设备手册', 1.00, '厂家提供的技术手册'),
('theory_paper', '理论论文', 0.90, '学术论文/研究报告'),
('maintenance_record', '维修记录', 0.80, '历史维修数据，实践经验'),
('user_feedback', '用户反馈', 0.60, '用户评价/反馈，可信度较低'),
('unknown', '未知来源', 0.50, '无法确定来源时的默认值');

COMMIT;