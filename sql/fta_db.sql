-- ============================================================================
-- 故障树智能生成系统 - MySQL 数据库
-- 服务: vector-store-service, fault-tree-editor-service
-- ============================================================================

-- ============================================================================
-- 第一部分：向量存储服务 (vector-store-service)
-- ============================================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS fta_vector_store DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE fta_vector_store;

-- ============================================================================
-- 1. document_metadata 表 - 文档元数据表
-- ============================================================================
CREATE TABLE IF NOT EXISTS document_metadata (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id VARCHAR(100) UNIQUE NOT NULL COMMENT '文档唯一标识ID',
    file_name VARCHAR(255) NOT NULL COMMENT '文件名',
    file_type VARCHAR(50) COMMENT '文件类型：PDF/DOCX/TXT',
    page_count INT COMMENT '页数',
    upload_time DATETIME COMMENT '上传时间',
    equipment_type VARCHAR(100) COMMENT '设备类型',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态：PENDING-待处理，PROCESSING-处理中，COMPLETED-完成，FAILED-失败',
    source_type VARCHAR(50) DEFAULT 'unknown' COMMENT '来源类型：industry_standard/equipment_manual/theory_paper/maintenance_record/user_feedback/unknown',
    credibility_weight DECIMAL(3,2) DEFAULT 0.50 COMMENT '可信度权重：行业标准1.2，设备手册1.0，理论论文0.9，维修记录0.8，用户反馈0.6，未知0.5',
    persist_to_knowledge_base TINYINT(1) DEFAULT 0 COMMENT '是否持久化到知识图谱：0-否，1-是',
    is_temporary TINYINT(1) DEFAULT 1 COMMENT '是否临时文档：0-否，1-是',
    expires_at DATETIME DEFAULT NULL COMMENT '过期时间，临时文档过期后可被清理',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_doc_id (doc_id),
    INDEX idx_source_type (source_type),
    INDEX idx_persist_to_kb (persist_to_knowledge_base),
    INDEX idx_is_temporary (is_temporary),
    INDEX idx_expires_at (expires_at),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档元数据表';

-- ============================================================================
-- 2. paragraph_metadata 表 - 段落元数据表
-- ============================================================================
CREATE TABLE IF NOT EXISTS paragraph_metadata (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    paragraph_id VARCHAR(100) UNIQUE NOT NULL COMMENT '段落唯一标识ID',
    doc_id VARCHAR(100) NOT NULL COMMENT '所属文档ID',
    section_title VARCHAR(255) COMMENT '章节标题',
    page_number INT COMMENT '页码',
    paragraph_number INT COMMENT '段落编号',
    text_length INT COMMENT '文本长度',
    keywords VARCHAR(500) COMMENT '关键词',
    confidence_score DECIMAL(3,2) COMMENT '置信度评分',
    content TEXT NOT NULL COMMENT '段落内容',
    source_type VARCHAR(50) DEFAULT 'unknown' COMMENT '段落来源类型：industry_standard/equipment_manual/theory_paper/maintenance_record/user_feedback/unknown',
    credibility_weight DECIMAL(3,2) DEFAULT 0.50 COMMENT '段落可信度权重，用于多文档融合时冲突消解',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_paragraph_id (paragraph_id),
    INDEX idx_doc_id (doc_id),
    INDEX idx_source_type (source_type),
    INDEX idx_credibility_weight (credibility_weight),
    CONSTRAINT fk_pm_doc FOREIGN KEY (doc_id) REFERENCES document_metadata(doc_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='段落元数据表';

-- ============================================================================
-- 3. vector_store 表 - 向量存储表
-- ============================================================================
CREATE TABLE IF NOT EXISTS vector_store (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vector_id VARCHAR(100) UNIQUE NOT NULL COMMENT '向量唯一标识ID',
    paragraph_id VARCHAR(100) NOT NULL COMMENT '对应段落ID',
    doc_id VARCHAR(100) NOT NULL COMMENT '对应文档ID',
    vector_data TEXT NOT NULL COMMENT '向量数据（JSON格式）',
    vector_dimension INT DEFAULT 768 COMMENT '向量维度',
    embedding_model VARCHAR(50) DEFAULT 'BGE-M3' COMMENT '生成向量的模型名称',
    similarity_score DECIMAL(5,4) COMMENT '相似度评分',
    created_by VARCHAR(50) DEFAULT 'system' COMMENT '创建者：system-系统，user_xxx-用户',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_vector_id (vector_id),
    INDEX idx_paragraph_id (paragraph_id),
    INDEX idx_doc_id (doc_id),
    INDEX idx_embedding_model (embedding_model),
    CONSTRAINT fk_vs_paragraph FOREIGN KEY (paragraph_id) REFERENCES paragraph_metadata(paragraph_id) ON DELETE CASCADE,
    CONSTRAINT fk_vs_doc FOREIGN KEY (doc_id) REFERENCES document_metadata(doc_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='向量存储表';

-- ============================================================================
-- 4. fusion_results 表 - 多文档融合结果记录表（可选，用于审计）
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
    INDEX idx_fusion_id (fusion_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多文档融合结果记录表';


-- ============================================================================
-- 第二部分：故障树编辑服务 (fault-tree-editor-service)
-- ============================================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS fta_editor DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE fta_editor;

-- ============================================================================
-- 5. fault_trees 表 - 故障树主表
-- ============================================================================
CREATE TABLE IF NOT EXISTS fault_trees (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tree_id VARCHAR(100) UNIQUE NOT NULL COMMENT '故障树唯一标识ID',
    name VARCHAR(255) NOT NULL COMMENT '故障树名称',
    description TEXT COMMENT '故障树描述',
    equipment_type VARCHAR(100) COMMENT '设备类型（冗余字段，用于快速检索）',
    top_event VARCHAR(255) COMMENT '顶事件名称（冗余字段，用于快速检索）',
    tree_data LONGTEXT NOT NULL COMMENT '故障树完整数据（JSON格式）',
    version INT DEFAULT 1 COMMENT '故障树版本号',
    validation_status VARCHAR(20) DEFAULT 'PENDING' COMMENT '校验状态：PENDING-待校验，VALID-有效，INVALID-无效',
    validation_message TEXT COMMENT '校验失败的具体原因',
    source_doc_ids JSON DEFAULT NULL COMMENT '生成该故障树的源文档ID列表',
    source_detail JSON DEFAULT NULL COMMENT '每个节点的来源详情：{eventId: {sourceType, documentName, paragraphId, confidence}}',
    publish_status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '发布状态：DRAFT-草稿，PUBLISHED-已发布，ARCHIVED-已归档',
    generated_by VARCHAR(50) DEFAULT 'AI' COMMENT '生成方式：AI-人工智能，MANUAL-人工创建，HYBRID-混合',
    template_id VARCHAR(100) COMMENT '使用的模板ID（如果有）',
    fusion_statistics JSON DEFAULT NULL COMMENT '融合统计信息：{totalClusters, totalConflicts, resolvedConflicts}',
    created_by VARCHAR(50) NOT NULL COMMENT '创建人',
    updated_by VARCHAR(50) NOT NULL COMMENT '更新人',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tree_id (tree_id),
    INDEX idx_validation_status (validation_status),
    INDEX idx_publish_status (publish_status),
    INDEX idx_equipment_type (equipment_type),
    INDEX idx_top_event (top_event),
    INDEX idx_generated_by (generated_by),
    INDEX idx_created_by (created_by),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='故障树主表';

-- ============================================================================
-- 6. fault_tree_nodes 表 - 故障树节点表（结构化存储，便于查询）
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
    source_type VARCHAR(50) DEFAULT 'AI_GENERATED' COMMENT '来源类型：AI_GENERATED/EQUIPMENT_MANUAL/MAINTENANCE_RECORD等',
    source_detail JSON DEFAULT NULL COMMENT '来源详情：{sourceType, documentName, paragraphId, confidence}',
    paragraph_id VARCHAR(100) DEFAULT NULL COMMENT '来源段落ID（溯源用）',
    verification_status VARCHAR(20) DEFAULT 'PENDING' COMMENT '验证状态：PENDING-待验证，VERIFIED-已验证，REJECTED-已拒绝',
    verified_by VARCHAR(50) DEFAULT NULL COMMENT '验证人',
    verified_at DATETIME DEFAULT NULL COMMENT '验证时间',
    position_x INT DEFAULT 0 COMMENT '前端画布X坐标',
    position_y INT DEFAULT 0 COMMENT '前端画布Y坐标',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_node_tree (node_id, tree_id),
    INDEX idx_tree_id (tree_id),
    INDEX idx_node_type (node_type),
    INDEX idx_gate_type (gate_type),
    INDEX idx_verification_status (verification_status),
    INDEX idx_source_type (source_type),
    CONSTRAINT fk_ftn_tree FOREIGN KEY (tree_id) REFERENCES fault_trees(tree_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='故障树节点表';

-- ============================================================================
-- 7. fault_tree_edges 表 - 故障树节点关系表
-- ============================================================================
CREATE TABLE IF NOT EXISTS fault_tree_edges (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    edge_id VARCHAR(100) UNIQUE NOT NULL COMMENT '边ID',
    tree_id VARCHAR(100) NOT NULL COMMENT '所属故障树ID',
    source_node_id VARCHAR(100) NOT NULL COMMENT '源节点ID（父节点）',
    target_node_id VARCHAR(100) NOT NULL COMMENT '目标节点ID（子节点）',
    gate_type VARCHAR(20) DEFAULT 'OR' COMMENT '逻辑门类型：AND/OR/XOR/K-of-N',
    probability DECIMAL(5,4) DEFAULT NULL COMMENT '该边的概率（可选）',
    description VARCHAR(500) DEFAULT NULL COMMENT '关系描述',
    confidence DECIMAL(3,2) DEFAULT 0.90 COMMENT '关系置信度',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tree_id (tree_id),
    INDEX idx_source_node (source_node_id),
    INDEX idx_target_node (target_node_id),
    INDEX idx_gate_type (gate_type),
    UNIQUE KEY uk_edge_tree (edge_id, tree_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='故障树节点关系表';

-- ============================================================================
-- 8. fault_tree_versions 表 - 故障树版本历史表
-- ============================================================================
CREATE TABLE IF NOT EXISTS fault_tree_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id VARCHAR(100) UNIQUE NOT NULL COMMENT '版本ID',
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


-- ============================================================================
-- 插入示例数据
-- ============================================================================

-- 切换到 fta_vector_store 数据库
USE fta_vector_store;

INSERT INTO document_metadata (doc_id, file_name, file_type, page_count, upload_time, equipment_type, status, source_type, credibility_weight, persist_to_knowledge_base, is_temporary) VALUES
('doc_001', '电机故障手册.pdf', 'PDF', 50, NOW(), '电动机', 'COMPLETED', 'equipment_manual', 1.0, 1, 0),
('doc_002', '维修记录_2024.txt', 'TXT', 1, NOW(), '电动机', 'COMPLETED', 'maintenance_record', 0.8, 0, 1);

INSERT INTO paragraph_metadata (paragraph_id, doc_id, section_title, page_number, paragraph_number, text_length, content, source_type, credibility_weight) VALUES
('para_001', 'doc_001', '电机过热原因', 10, 1, 150, '电机过热的原因包括：电源问题、轴承故障或绕组绝缘老化。', 'equipment_manual', 1.0),
('para_002', 'doc_002', '维修记录', 1, 1, 100, '根据历史维修记录，电机过热主要由轴承润滑不良引起。', 'maintenance_record', 0.8);

INSERT INTO vector_store (vector_id, paragraph_id, doc_id, vector_data, vector_dimension, similarity_score) VALUES
('vec_001', 'para_001', 'doc_001', '[0.123,0.456,...]', 768, NULL),
('vec_002', 'para_002', 'doc_002', '[0.234,0.567,...]', 768, NULL);

-- 切换到 fta_editor 数据库
USE fta_editor;

INSERT INTO fault_trees (tree_id, name, description, equipment_type, top_event, tree_data, created_by, updated_by, validation_status) VALUES
('tree_001', '电机故障树', '电机常见故障分析树', '电动机', '电机过热', '{"nodes":[],"edges":[]}', 'admin', 'admin', 'PENDING');

COMMIT;
