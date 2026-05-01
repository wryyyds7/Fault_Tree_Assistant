-- ============================================================================
-- 故障树智能生成系统 - Oracle 数据库完整表结构
-- 创建时间: 2026-04-08
-- 数据库: Oracle
-- 服务: auth-service, vector-store-service, fault-tree-editor-service, feedback-learning-service
-- ============================================================================

-- ============================================================================
-- 第一部分: auth-service 表结构 (用户认证服务)
-- ============================================================================

-- ============================================================================
-- 表1: USERS (用户表)
-- ============================================================================
CREATE TABLE users (
    id NUMBER(19,0) PRIMARY KEY,
    user_id VARCHAR2(50) UNIQUE NOT NULL,
    username VARCHAR2(100) UNIQUE NOT NULL,
    password VARCHAR2(255) NOT NULL,
    email VARCHAR2(255) UNIQUE,
    role VARCHAR2(50) DEFAULT 'USER',
    last_login_time TIMESTAMP,
    password_change_time TIMESTAMP,
    failed_login_attempts NUMBER(3,0) DEFAULT 0,
    lock_time TIMESTAMP,
    password_history CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE SEQUENCE users_seq START WITH 1 INCREMENT BY 1 NOMAXVALUE;

CREATE OR REPLACE TRIGGER users_before_insert
BEFORE INSERT ON users
FOR EACH ROW
BEGIN
    IF :NEW.id IS NULL THEN
        SELECT users_seq.NEXTVAL INTO :NEW.id FROM DUAL;
    END IF;
    IF :NEW.created_at IS NULL THEN
        :NEW.created_at := CURRENT_TIMESTAMP;
    END IF;
    IF :NEW.updated_at IS NULL THEN
        :NEW.updated_at := CURRENT_TIMESTAMP;
    END IF;
END;
/

CREATE OR REPLACE TRIGGER users_before_update
BEFORE UPDATE ON users
FOR EACH ROW
BEGIN
    :NEW.updated_at := CURRENT_TIMESTAMP;
END;
/

CREATE INDEX idx_users_user_id ON users(user_id);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);

-- ============================================================================
-- 表2: USER_SESSIONS (用户会话表)
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

CREATE INDEX idx_sessions_session_id ON user_sessions(session_id);
CREATE INDEX idx_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_sessions_expires_at ON user_sessions(expires_at);


-- ============================================================================
-- 第二部分: vector-store-service 表结构 (向量存储服务)
-- ============================================================================

-- ============================================================================
-- 表3: DOCUMENT_METADATA (文档元数据表)
-- ============================================================================
CREATE TABLE document_metadata (
    id NUMBER(19,0) PRIMARY KEY,
    doc_id VARCHAR2(100) UNIQUE NOT NULL,
    user_id VARCHAR2(50),
    file_name VARCHAR2(255) NOT NULL,
    file_type VARCHAR2(50),
    page_count NUMBER(10,0),
    upload_time TIMESTAMP,
    equipment_type VARCHAR2(100),
    status VARCHAR2(20) DEFAULT 'PENDING',
    source_type VARCHAR2(50) DEFAULT 'unknown',
    credibility_weight NUMBER(3,2) DEFAULT 0.50,
    persist_to_knowledge_base NUMBER(1,0) DEFAULT 0,
    is_temporary NUMBER(1,0) DEFAULT 1,
    expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE SEQUENCE document_metadata_seq START WITH 1 INCREMENT BY 1 NOMAXVALUE;

CREATE OR REPLACE TRIGGER document_metadata_before_insert
BEFORE INSERT ON document_metadata
FOR EACH ROW
BEGIN
    IF :NEW.id IS NULL THEN
        SELECT document_metadata_seq.NEXTVAL INTO :NEW.id FROM DUAL;
    END IF;
    IF :NEW.created_at IS NULL THEN
        :NEW.created_at := CURRENT_TIMESTAMP;
    END IF;
    IF :NEW.updated_at IS NULL THEN
        :NEW.updated_at := CURRENT_TIMESTAMP;
    END IF;
END;
/

CREATE OR REPLACE TRIGGER document_metadata_before_update
BEFORE UPDATE ON document_metadata
FOR EACH ROW
BEGIN
    :NEW.updated_at := CURRENT_TIMESTAMP;
END;
/

CREATE INDEX idx_dm_doc_id ON document_metadata(doc_id);
CREATE INDEX idx_dm_user_id ON document_metadata(user_id);
CREATE INDEX idx_dm_equipment_type ON document_metadata(equipment_type);
CREATE INDEX idx_dm_status ON document_metadata(status);
CREATE INDEX idx_dm_source_type ON document_metadata(source_type);
CREATE INDEX idx_dm_persist_to_kb ON document_metadata(persist_to_knowledge_base);

-- ============================================================================
-- 表4: PARAGRAPH_METADATA (段落元数据表)
-- ============================================================================
CREATE TABLE paragraph_metadata (
    id NUMBER(19,0) PRIMARY KEY,
    paragraph_id VARCHAR2(100) UNIQUE NOT NULL,
    doc_id VARCHAR2(100) NOT NULL,
    user_id VARCHAR2(50),
    section_title VARCHAR2(255),
    page_number NUMBER(10,0),
    paragraph_number NUMBER(10,0),
    text_length NUMBER(10,0),
    keywords VARCHAR2(500),
    confidence_score NUMBER(3,2),
    content CLOB NOT NULL,
    source_type VARCHAR2(50) DEFAULT 'unknown',
    credibility_weight NUMBER(3,2) DEFAULT 0.50,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pm_doc FOREIGN KEY (doc_id) REFERENCES document_metadata(doc_id) ON DELETE CASCADE
);

CREATE SEQUENCE paragraph_metadata_seq START WITH 1 INCREMENT BY 1 NOMAXVALUE;

CREATE OR REPLACE TRIGGER paragraph_metadata_before_insert
BEFORE INSERT ON paragraph_metadata
FOR EACH ROW
BEGIN
    IF :NEW.id IS NULL THEN
        SELECT paragraph_metadata_seq.NEXTVAL INTO :NEW.id FROM DUAL;
    END IF;
    IF :NEW.created_at IS NULL THEN
        :NEW.created_at := CURRENT_TIMESTAMP;
    END IF;
    IF :NEW.updated_at IS NULL THEN
        :NEW.updated_at := CURRENT_TIMESTAMP;
    END IF;
END;
/

CREATE OR REPLACE TRIGGER paragraph_metadata_before_update
BEFORE UPDATE ON paragraph_metadata
FOR EACH ROW
BEGIN
    :NEW.updated_at := CURRENT_TIMESTAMP;
END;
/

CREATE INDEX idx_pm_paragraph_id ON paragraph_metadata(paragraph_id);
CREATE INDEX idx_pm_doc_id ON paragraph_metadata(doc_id);
CREATE INDEX idx_pm_user_id ON paragraph_metadata(user_id);
CREATE INDEX idx_pm_source_type ON paragraph_metadata(source_type);

-- ============================================================================
-- 表5: VECTOR_STORE (向量存储表)
-- ============================================================================
CREATE TABLE vector_store (
    id NUMBER(19,0) PRIMARY KEY,
    vector_id VARCHAR2(100) UNIQUE NOT NULL,
    paragraph_id VARCHAR2(100) NOT NULL,
    doc_id VARCHAR2(100) NOT NULL,
    user_id VARCHAR2(50),
    vector_data CLOB NOT NULL,
    vector_dimension NUMBER(10,0) DEFAULT 768,
    embedding_model VARCHAR2(50) DEFAULT 'BGE-M3',
    similarity_score NUMBER(5,4),
    created_by VARCHAR2(50) DEFAULT 'system',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vs_paragraph FOREIGN KEY (paragraph_id) REFERENCES paragraph_metadata(paragraph_id) ON DELETE CASCADE,
    CONSTRAINT fk_vs_doc FOREIGN KEY (doc_id) REFERENCES document_metadata(doc_id) ON DELETE CASCADE
);

CREATE SEQUENCE vector_store_seq START WITH 1 INCREMENT BY 1 NOMAXVALUE;

CREATE OR REPLACE TRIGGER vector_store_before_insert
BEFORE INSERT ON vector_store
FOR EACH ROW
BEGIN
    IF :NEW.id IS NULL THEN
        SELECT vector_store_seq.NEXTVAL INTO :NEW.id FROM DUAL;
    END IF;
    IF :NEW.created_at IS NULL THEN
        :NEW.created_at := CURRENT_TIMESTAMP;
    END IF;
    IF :NEW.updated_at IS NULL THEN
        :NEW.updated_at := CURRENT_TIMESTAMP;
    END IF;
END;
/

CREATE OR REPLACE TRIGGER vector_store_before_update
BEFORE UPDATE ON vector_store
FOR EACH ROW
BEGIN
    :NEW.updated_at := CURRENT_TIMESTAMP;
END;
/

CREATE INDEX idx_vs_vector_id ON vector_store(vector_id);
CREATE INDEX idx_vs_paragraph_id ON vector_store(paragraph_id);
CREATE INDEX idx_vs_doc_id ON vector_store(doc_id);
CREATE INDEX idx_vs_user_id ON vector_store(user_id);
CREATE INDEX idx_vs_embedding_model ON vector_store(embedding_model);


-- ============================================================================
-- 第三部分: fault-tree-editor-service 表结构 (故障树编辑服务)
-- ============================================================================

-- ============================================================================
-- 表6: FAULT_TREES (故障树主表)
-- ============================================================================
CREATE TABLE fault_trees (
    id NUMBER(19,0) PRIMARY KEY,
    tree_id VARCHAR2(100) UNIQUE NOT NULL,
    user_id VARCHAR2(50),
    name VARCHAR2(255) NOT NULL,
    description CLOB,
    equipment_type VARCHAR2(100),
    top_event VARCHAR2(255),
    tree_data CLOB NOT NULL,
    version NUMBER(10,0) DEFAULT 1,
    validation_status VARCHAR2(20) DEFAULT 'PENDING',
    validation_message CLOB,
    source_doc_ids CLOB,
    source_detail CLOB,
    publish_status VARCHAR2(20) DEFAULT 'DRAFT',
    fusion_statistics CLOB,
    generated_by VARCHAR2(50) DEFAULT 'AI',
    template_id VARCHAR2(100),
    created_by VARCHAR2(50) NOT NULL,
    updated_by VARCHAR2(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE SEQUENCE fault_trees_seq START WITH 1 INCREMENT BY 1 NOMAXVALUE;

CREATE OR REPLACE TRIGGER fault_trees_before_insert
BEFORE INSERT ON fault_trees
FOR EACH ROW
BEGIN
    IF :NEW.id IS NULL THEN
        SELECT fault_trees_seq.NEXTVAL INTO :NEW.id FROM DUAL;
    END IF;
    IF :NEW.created_at IS NULL THEN
        :NEW.created_at := CURRENT_TIMESTAMP;
    END IF;
    IF :NEW.updated_at IS NULL THEN
        :NEW.updated_at := CURRENT_TIMESTAMP;
    END IF;
END;
/

CREATE OR REPLACE TRIGGER fault_trees_before_update
BEFORE UPDATE ON fault_trees
FOR EACH ROW
BEGIN
    :NEW.updated_at := CURRENT_TIMESTAMP;
END;
/

CREATE INDEX idx_ft_tree_id ON fault_trees(tree_id);
CREATE INDEX idx_ft_user_id ON fault_trees(user_id);
CREATE INDEX idx_ft_equipment_type ON fault_trees(equipment_type);
CREATE INDEX idx_ft_validation_status ON fault_trees(validation_status);
CREATE INDEX idx_ft_publish_status ON fault_trees(publish_status);
CREATE INDEX idx_ft_created_by ON fault_trees(created_by);
CREATE INDEX idx_ft_top_event ON fault_trees(top_event);

-- ============================================================================
-- 表7: FAULT_TREE_NODES (故障树节点表)
-- ============================================================================
CREATE TABLE fault_tree_nodes (
    id NUMBER(19,0) PRIMARY KEY,
    node_id VARCHAR2(100) NOT NULL,
    tree_id VARCHAR2(100) NOT NULL,
    user_id VARCHAR2(50),
    node_name VARCHAR2(255) NOT NULL,
    node_type VARCHAR2(20) NOT NULL,
    gate_type VARCHAR2(20),
    description CLOB,
    confidence NUMBER(3,2) DEFAULT 0.85,
    source_type VARCHAR2(50) DEFAULT 'AI_GENERATED',
    source_detail CLOB,
    paragraph_id VARCHAR2(100),
    verification_status VARCHAR2(20) DEFAULT 'PENDING',
    verified_by VARCHAR2(50),
    verified_at TIMESTAMP,
    position_x NUMBER(10,0) DEFAULT 0,
    position_y NUMBER(10,0) DEFAULT 0,
    ai_generated NUMBER(1,0) DEFAULT 1,
    generation_mode VARCHAR2(20) DEFAULT 'hybrid',
    section_title VARCHAR2(255),
    similarity_score NUMBER(5,4) DEFAULT 0,
    source_evidence CLOB,
    event_id VARCHAR2(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ftn_node_tree UNIQUE (node_id, tree_id),
    CONSTRAINT fk_ftn_tree FOREIGN KEY (tree_id) REFERENCES fault_trees(tree_id) ON DELETE CASCADE
);

CREATE SEQUENCE fault_tree_nodes_seq START WITH 1 INCREMENT BY 1 NOMAXVALUE;

CREATE OR REPLACE TRIGGER fault_tree_nodes_before_insert
BEFORE INSERT ON fault_tree_nodes
FOR EACH ROW
BEGIN
    IF :NEW.id IS NULL THEN
        SELECT fault_tree_nodes_seq.NEXTVAL INTO :NEW.id FROM DUAL;
    END IF;
    IF :NEW.created_at IS NULL THEN
        :NEW.created_at := CURRENT_TIMESTAMP;
    END IF;
    IF :NEW.updated_at IS NULL THEN
        :NEW.updated_at := CURRENT_TIMESTAMP;
    END IF;
END;
/

CREATE OR REPLACE TRIGGER fault_tree_nodes_before_update
BEFORE UPDATE ON fault_tree_nodes
FOR EACH ROW
BEGIN
    :NEW.updated_at := CURRENT_TIMESTAMP;
END;
/

CREATE INDEX idx_ftn_tree_id ON fault_tree_nodes(tree_id);
CREATE INDEX idx_ftn_node_type ON fault_tree_nodes(node_type);
CREATE INDEX idx_ftn_gate_type ON fault_tree_nodes(gate_type);
CREATE INDEX idx_ftn_verification_status ON fault_tree_nodes(verification_status);

-- ============================================================================
-- 表8: FAULT_TREE_EDGES (故障树节点关系表)
-- ============================================================================
CREATE TABLE fault_tree_edges (
    id NUMBER(19,0) PRIMARY KEY,
    edge_id VARCHAR2(100) UNIQUE NOT NULL,
    tree_id VARCHAR2(100) NOT NULL,
    user_id VARCHAR2(50),
    source_node_id VARCHAR2(100) NOT NULL,
    target_node_id VARCHAR2(100) NOT NULL,
    gate_type VARCHAR2(20) DEFAULT 'OR',
    probability NUMBER(5,4),
    description VARCHAR2(500),
    confidence NUMBER(3,2) DEFAULT 0.90,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_fte_edge_tree UNIQUE (edge_id, tree_id),
    CONSTRAINT fk_fte_tree FOREIGN KEY (tree_id) REFERENCES fault_trees(tree_id) ON DELETE CASCADE
);

CREATE SEQUENCE fault_tree_edges_seq START WITH 1 INCREMENT BY 1 NOMAXVALUE;

CREATE OR REPLACE TRIGGER fault_tree_edges_before_insert
BEFORE INSERT ON fault_tree_edges
FOR EACH ROW
BEGIN
    IF :NEW.id IS NULL THEN
        SELECT fault_tree_edges_seq.NEXTVAL INTO :NEW.id FROM DUAL;
    END IF;
    IF :NEW.created_at IS NULL THEN
        :NEW.created_at := CURRENT_TIMESTAMP;
    END IF;
END;
/

CREATE INDEX idx_fte_tree_id ON fault_tree_edges(tree_id);
CREATE INDEX idx_fte_source_node ON fault_tree_edges(source_node_id);
CREATE INDEX idx_fte_target_node ON fault_tree_edges(target_node_id);
CREATE INDEX idx_fte_gate_type ON fault_tree_edges(gate_type);

-- ============================================================================
-- 表9: FAULT_TREE_VERSIONS (故障树版本历史表)
-- ============================================================================
CREATE TABLE fault_tree_versions (
    id NUMBER(19,0) PRIMARY KEY,
    version_id VARCHAR2(100) UNIQUE NOT NULL,
    tree_id VARCHAR2(100) NOT NULL,
    user_id VARCHAR2(50),
    version_number NUMBER(10,0) NOT NULL,
    tree_data_snapshot CLOB NOT NULL,
    change_summary CLOB,
    changed_by VARCHAR2(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ftv_tree_version UNIQUE (tree_id, version_number),
    CONSTRAINT fk_ftv_tree FOREIGN KEY (tree_id) REFERENCES fault_trees(tree_id) ON DELETE CASCADE
);

CREATE SEQUENCE fault_tree_versions_seq START WITH 1 INCREMENT BY 1 NOMAXVALUE;

CREATE OR REPLACE TRIGGER fault_tree_versions_before_insert
BEFORE INSERT ON fault_tree_versions
FOR EACH ROW
BEGIN
    IF :NEW.id IS NULL THEN
        SELECT fault_tree_versions_seq.NEXTVAL INTO :NEW.id FROM DUAL;
    END IF;
    IF :NEW.created_at IS NULL THEN
        :NEW.created_at := CURRENT_TIMESTAMP;
    END IF;
END;
/

CREATE INDEX idx_ftv_tree_id ON fault_tree_versions(tree_id);
CREATE INDEX idx_ftv_version_number ON fault_tree_versions(version_number);


-- ============================================================================
-- 第四部分: feedback-learning-service 表结构 (反馈学习服务)
-- ============================================================================

-- ============================================================================
-- 表10: FEEDBACK (反馈表)
-- ============================================================================
CREATE TABLE feedback (
    id NUMBER(19,0) PRIMARY KEY,
    feedback_id VARCHAR2(100) UNIQUE NOT NULL,
    tree_id VARCHAR2(100) NOT NULL,
    user_id VARCHAR2(50) NOT NULL,
    feedback_type VARCHAR2(20) DEFAULT 'CORRECTION',
    rating NUMBER(1,0),
    content CLOB,
    comments CLOB,
    suggestions CLOB,
    suggested_changes CLOB,
    accuracy_score NUMBER(3,2),
    completeness_score NUMBER(3,2),
    clarity_score NUMBER(3,2),
    status VARCHAR2(20) DEFAULT 'PENDING',
    processed_by VARCHAR2(50),
    processed_at TIMESTAMP,
    applied_to_model NUMBER(1,0) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE SEQUENCE feedback_seq START WITH 1 INCREMENT BY 1 NOMAXVALUE;

CREATE OR REPLACE TRIGGER feedback_before_insert
BEFORE INSERT ON feedback
FOR EACH ROW
BEGIN
    IF :NEW.id IS NULL THEN
        SELECT feedback_seq.NEXTVAL INTO :NEW.id FROM DUAL;
    END IF;
    IF :NEW.created_at IS NULL THEN
        :NEW.created_at := CURRENT_TIMESTAMP;
    END IF;
END;
/

CREATE INDEX idx_feedback_feedback_id ON feedback(feedback_id);
CREATE INDEX idx_feedback_tree_id ON feedback(tree_id);
CREATE INDEX idx_feedback_user_id ON feedback(user_id);
CREATE INDEX idx_feedback_status ON feedback(status);
CREATE INDEX idx_feedback_feedback_type ON feedback(feedback_type);
CREATE INDEX idx_feedback_created_at ON feedback(created_at);

-- ============================================================================
-- 表11: FEEDBACK_PROCESS_LOG (反馈处理记录表)
-- ============================================================================
CREATE TABLE feedback_process_log (
    id NUMBER(19,0) PRIMARY KEY,
    process_id VARCHAR2(100) UNIQUE NOT NULL,
    feedback_id VARCHAR2(100) NOT NULL,
    action VARCHAR2(50) NOT NULL,
    operator VARCHAR2(50) NOT NULL,
    details CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fpl_feedback FOREIGN KEY (feedback_id) REFERENCES feedback(feedback_id) ON DELETE CASCADE
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

CREATE INDEX idx_fpl_process_id ON feedback_process_log(process_id);
CREATE INDEX idx_fpl_feedback_id ON feedback_process_log(feedback_id);


-- ============================================================================
-- 第六部分: 补充表结构 (从complete_oracle_schema.sql合并)
-- ============================================================================

-- ============================================================================
-- 表12: OPERATION_LOG (操作日志表)
-- 功能校验: 存储系统操作日志、请求响应信息、执行时间
-- 关联服务: log-service, 所有服务通过Feign调用
-- ============================================================================
CREATE TABLE operation_log (
    id NUMBER(19,0) PRIMARY KEY,
    user_id VARCHAR2(64),
    username VARCHAR2(128),
    service_name VARCHAR2(64) NOT NULL,
    log_level VARCHAR2(16) NOT NULL,
    operation_type VARCHAR2(64) NOT NULL,
    operation_detail CLOB,
    ip_address VARCHAR2(64),
    request_method VARCHAR2(10),
    request_path VARCHAR2(512),
    request_params CLOB,
    response_status NUMBER(10),
    execution_time NUMBER(20),
    create_time TIMESTAMP NOT NULL
);

CREATE SEQUENCE operation_log_seq START WITH 1 INCREMENT BY 1 NOMAXVALUE;

CREATE OR REPLACE TRIGGER operation_log_before_insert
BEFORE INSERT ON operation_log
FOR EACH ROW
BEGIN
    IF :NEW.id IS NULL THEN
        SELECT operation_log_seq.NEXTVAL INTO :NEW.id FROM DUAL;
    END IF;
    IF :NEW.create_time IS NULL THEN
        :NEW.create_time := CURRENT_TIMESTAMP;
    END IF;
END;
/

CREATE INDEX idx_op_log_user ON operation_log(user_id);
CREATE INDEX idx_op_log_service ON operation_log(service_name);
CREATE INDEX idx_op_log_time ON operation_log(create_time);
CREATE INDEX idx_op_log_type ON operation_log(operation_type);

-- ============================================================================
-- 表13: NODE_SOURCE_TRACKING (节点溯源追踪表)
-- 功能校验: 存储AI生成故障树节点的溯源信息，包括来源文档、段落、相似度等
-- 关联服务: fault-tree-editor-service, rag-generation-service
-- ============================================================================
CREATE TABLE node_source_tracking (
    id NUMBER(19,0) PRIMARY KEY,
    tracking_id VARCHAR2(100) UNIQUE NOT NULL,
    node_id VARCHAR2(100) NOT NULL,
    tree_id VARCHAR2(100) NOT NULL,
    event_id VARCHAR2(100),
    source_type VARCHAR2(50) NOT NULL,
    document_name VARCHAR2(255),
    document_id VARCHAR2(100),
    page_number NUMBER(10,0),
    paragraph_id VARCHAR2(100),
    section_title VARCHAR2(255),
    similarity_score NUMBER(5,4) DEFAULT 0,
    source_evidence CLOB,
    confidence NUMBER(3,2) DEFAULT 0.85,
    generation_mode VARCHAR2(20) DEFAULT 'hybrid',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_nst_tree FOREIGN KEY (tree_id) REFERENCES fault_trees(tree_id) ON DELETE CASCADE
);

CREATE SEQUENCE node_source_tracking_seq START WITH 1 INCREMENT BY 1 NOMAXVALUE;

CREATE OR REPLACE TRIGGER node_source_tracking_before_insert
BEFORE INSERT ON node_source_tracking
FOR EACH ROW
BEGIN
    IF :NEW.id IS NULL THEN
        SELECT node_source_tracking_seq.NEXTVAL INTO :NEW.id FROM DUAL;
    END IF;
    IF :NEW.created_at IS NULL THEN
        :NEW.created_at := CURRENT_TIMESTAMP;
    END IF;
    IF :NEW.updated_at IS NULL THEN
        :NEW.updated_at := CURRENT_TIMESTAMP;
    END IF;
END;
/

CREATE INDEX idx_nst_tracking ON node_source_tracking(tracking_id);
CREATE INDEX idx_nst_node ON node_source_tracking(node_id);
CREATE INDEX idx_nst_tree ON node_source_tracking(tree_id);
CREATE INDEX idx_nst_source_type ON node_source_tracking(source_type);
CREATE INDEX idx_nst_document ON node_source_tracking(document_id);

-- ============================================================================
-- 表14: FUSION_STATISTICS_HISTORY (融合统计历史表)
-- 功能校验: 存储故障树融合生成的统计信息，包括知识驱动和数据驱动的融合情况
-- 关联服务: fault-tree-editor-service, rag-generation-service
-- ============================================================================
CREATE TABLE fusion_statistics_history (
    id NUMBER(19,0) PRIMARY KEY,
    stats_id VARCHAR2(100) UNIQUE NOT NULL,
    tree_id VARCHAR2(100) NOT NULL,
    total_paragraphs NUMBER(10,0) DEFAULT 0,
    retrieved_count NUMBER(10,0) DEFAULT 0,
    kg_constraints_used NUMBER(1,0) DEFAULT 0,
    generation_mode VARCHAR2(20) DEFAULT 'hybrid',
    average_confidence NUMBER(3,2) DEFAULT 0,
    generation_duration_ms NUMBER(20,0) DEFAULT 0,
    fusion_details CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fsh_tree FOREIGN KEY (tree_id) REFERENCES fault_trees(tree_id) ON DELETE CASCADE
);

CREATE SEQUENCE fusion_statistics_history_seq START WITH 1 INCREMENT BY 1 NOMAXVALUE;

CREATE OR REPLACE TRIGGER fusion_statistics_history_before_insert
BEFORE INSERT ON fusion_statistics_history
FOR EACH ROW
BEGIN
    IF :NEW.id IS NULL THEN
        SELECT fusion_statistics_history_seq.NEXTVAL INTO :NEW.id FROM DUAL;
    END IF;
    IF :NEW.created_at IS NULL THEN
        :NEW.created_at := CURRENT_TIMESTAMP;
    END IF;
END;
/

CREATE INDEX idx_fsh_stats ON fusion_statistics_history(stats_id);
CREATE INDEX idx_fsh_tree ON fusion_statistics_history(tree_id);

-- ============================================================================
-- 初始化示例数据
-- ============================================================================

-- 用户数据 (密码是 BCrypt 加密的 "password123")
INSERT INTO users (user_id, username, password, email, role) VALUES
('admin-001', 'admin', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'admin@example.com', 'ADMIN'),
('user-001', 'testuser', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'user@example.com', 'USER');

COMMIT;

-- ============================================================================
-- 表总数: 14
-- 总序列数: 14
-- 总触发器数: 14
-- ============================================================================
