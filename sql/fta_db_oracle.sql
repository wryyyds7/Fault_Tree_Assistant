-- ============================================================================
-- 故障树智能生成系统 - Oracle 数据库
-- 服务: vector-store-service, fault-tree-editor-service
-- ============================================================================

-- 创建表空间和用户
CREATE TABLESPACE fta_ts DATAFILE 'fta_ts.dbf' SIZE 500M AUTOEXTEND ON NEXT 50M;
CREATE USER fta_user IDENTIFIED BY fta_password DEFAULT TABLESPACE fta_ts QUOTA UNLIMITED ON fta_ts;
GRANT CONNECT, RESOURCE, CREATE SESSION, CREATE VIEW, CREATE SEQUENCE, CREATE TRIGGER TO fta_user;

-- 切换到fta_user用户
ALTER SESSION SET CURRENT_SCHEMA = fta_user;

-- ============================================================================
-- 第一部分：向量存储服务 (vector-store-service)
-- ============================================================================

-- ============================================================================
-- 1. document_metadata 表 - 文档元数据表
-- ============================================================================
CREATE TABLE document_metadata (
    id NUMBER(19,0) PRIMARY KEY,
    doc_id VARCHAR2(100) UNIQUE NOT NULL,
    file_name VARCHAR2(255) NOT NULL,
    file_type VARCHAR2(50),
    page_count NUMBER(10,0),
    upload_time TIMESTAMP,
    equipment_type VARCHAR2(100),
    status VARCHAR2(20) DEFAULT 'PENDING',
    source_type VARCHAR2(50) DEFAULT 'unknown',
    credibility_weight NUMBER(3,2) DEFAULT 0.50,
    persist_to_knowledge_base NUMBER(1,0) DEFAULT 0 CHECK (persist_to_knowledge_base IN (0, 1)),
    is_temporary NUMBER(1,0) DEFAULT 1 CHECK (is_temporary IN (0, 1)),
    expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建序列
CREATE SEQUENCE document_metadata_seq START WITH 1 INCREMENT BY 1 NOMAXVALUE;

-- 创建触发器
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

-- 创建索引
CREATE INDEX idx_dm_doc_id ON document_metadata(doc_id);
CREATE INDEX idx_dm_source_type ON document_metadata(source_type);
CREATE INDEX idx_dm_persist_to_kb ON document_metadata(persist_to_knowledge_base);
CREATE INDEX idx_dm_is_temporary ON document_metadata(is_temporary);
CREATE INDEX idx_dm_expires_at ON document_metadata(expires_at);
CREATE INDEX idx_dm_status ON document_metadata(status);

-- ============================================================================
-- 2. paragraph_metadata 表 - 段落元数据表
-- ============================================================================
CREATE TABLE paragraph_metadata (
    id NUMBER(19,0) PRIMARY KEY,
    paragraph_id VARCHAR2(100) UNIQUE NOT NULL,
    doc_id VARCHAR2(100) NOT NULL,
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

-- 创建序列
CREATE SEQUENCE paragraph_metadata_seq START WITH 1 INCREMENT BY 1 NOMAXVALUE;

-- 创建触发器
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

-- 创建索引
CREATE INDEX idx_pm_paragraph_id ON paragraph_metadata(paragraph_id);
CREATE INDEX idx_pm_doc_id ON paragraph_metadata(doc_id);
CREATE INDEX idx_pm_source_type ON paragraph_metadata(source_type);
CREATE INDEX idx_pm_credibility_weight ON paragraph_metadata(credibility_weight);

-- ============================================================================
-- 3. vector_store 表 - 向量存储表
-- ============================================================================
CREATE TABLE vector_store (
    id NUMBER(19,0) PRIMARY KEY,
    vector_id VARCHAR2(100) UNIQUE NOT NULL,
    paragraph_id VARCHAR2(100) NOT NULL,
    doc_id VARCHAR2(100) NOT NULL,
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

-- 创建序列
CREATE SEQUENCE vector_store_seq START WITH 1 INCREMENT BY 1 NOMAXVALUE;

-- 创建触发器
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

-- 创建索引
CREATE INDEX idx_vs_vector_id ON vector_store(vector_id);
CREATE INDEX idx_vs_paragraph_id ON vector_store(paragraph_id);
CREATE INDEX idx_vs_doc_id ON vector_store(doc_id);
CREATE INDEX idx_vs_embedding_model ON vector_store(embedding_model);

-- ============================================================================
-- 4. fusion_results 表 - 多文档融合结果记录表（可选，用于审计）
-- ============================================================================
CREATE TABLE fusion_results (
    id NUMBER(19,0) PRIMARY KEY,
    fusion_id VARCHAR2(100) NOT NULL UNIQUE,
    source_doc_ids CLOB NOT NULL,
    fused_paragraphs_count NUMBER(10,0) DEFAULT 0,
    total_clusters NUMBER(10,0) DEFAULT 0,
    total_conflicts NUMBER(10,0) DEFAULT 0,
    resolved_conflicts NUMBER(10,0) DEFAULT 0,
    fusion_duration_ms NUMBER(10,0) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建序列
CREATE SEQUENCE fusion_results_seq START WITH 1 INCREMENT BY 1 NOMAXVALUE;

-- 创建触发器
CREATE OR REPLACE TRIGGER fusion_results_before_insert
BEFORE INSERT ON fusion_results
FOR EACH ROW
BEGIN
    IF :NEW.id IS NULL THEN
        SELECT fusion_results_seq.NEXTVAL INTO :NEW.id FROM DUAL;
    END IF;
    IF :NEW.created_at IS NULL THEN
        :NEW.created_at := CURRENT_TIMESTAMP;
    END IF;
END;
/

-- 创建索引
CREATE INDEX idx_fr_fusion_id ON fusion_results(fusion_id);
CREATE INDEX idx_fr_created_at ON fusion_results(created_at);


-- ============================================================================
-- 第二部分：故障树编辑服务 (fault-tree-editor-service)
-- ============================================================================

-- ============================================================================
-- 5. fault_trees 表 - 故障树主表
-- ============================================================================
CREATE TABLE fault_trees (
    id NUMBER(19,0) PRIMARY KEY,
    tree_id VARCHAR2(100) UNIQUE NOT NULL,
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
    generated_by VARCHAR2(50) DEFAULT 'AI',
    template_id VARCHAR2(100),
    fusion_statistics CLOB,
    created_by VARCHAR2(50) NOT NULL,
    updated_by VARCHAR2(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建序列
CREATE SEQUENCE fault_trees_seq START WITH 1 INCREMENT BY 1 NOMAXVALUE;

-- 创建触发器
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

-- 创建索引
CREATE INDEX idx_ft_tree_id ON fault_trees(tree_id);
CREATE INDEX idx_ft_validation_status ON fault_trees(validation_status);
CREATE INDEX idx_ft_publish_status ON fault_trees(publish_status);
CREATE INDEX idx_ft_equipment_type ON fault_trees(equipment_type);
CREATE INDEX idx_ft_top_event ON fault_trees(top_event);
CREATE INDEX idx_ft_generated_by ON fault_trees(generated_by);
CREATE INDEX idx_ft_created_by ON fault_trees(created_by);
CREATE INDEX idx_ft_created_at ON fault_trees(created_at);

-- ============================================================================
-- 6. fault_tree_nodes 表 - 故障树节点表（结构化存储，便于查询）
-- ============================================================================
CREATE TABLE fault_tree_nodes (
    id NUMBER(19,0) PRIMARY KEY,
    node_id VARCHAR2(100) NOT NULL,
    tree_id VARCHAR2(100) NOT NULL,
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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ftn_tree FOREIGN KEY (tree_id) REFERENCES fault_trees(tree_id) ON DELETE CASCADE
);

-- 创建序列
CREATE SEQUENCE fault_tree_nodes_seq START WITH 1 INCREMENT BY 1 NOMAXVALUE;

-- 创建触发器
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

-- 创建唯一约束
ALTER TABLE fault_tree_nodes ADD CONSTRAINT uk_node_tree UNIQUE (node_id, tree_id);

-- 创建索引
CREATE INDEX idx_ftn_tree_id ON fault_tree_nodes(tree_id);
CREATE INDEX idx_ftn_node_type ON fault_tree_nodes(node_type);
CREATE INDEX idx_ftn_gate_type ON fault_tree_nodes(gate_type);
CREATE INDEX idx_ftn_verification_status ON fault_tree_nodes(verification_status);
CREATE INDEX idx_ftn_source_type ON fault_tree_nodes(source_type);

-- ============================================================================
-- 7. fault_tree_edges 表 - 故障树节点关系表
-- ============================================================================
CREATE TABLE fault_tree_edges (
    id NUMBER(19,0) PRIMARY KEY,
    edge_id VARCHAR2(100) NOT NULL UNIQUE,
    tree_id VARCHAR2(100) NOT NULL,
    source_node_id VARCHAR2(100) NOT NULL,
    target_node_id VARCHAR2(100) NOT NULL,
    gate_type VARCHAR2(20) DEFAULT 'OR',
    probability NUMBER(5,4),
    description VARCHAR2(500),
    confidence NUMBER(3,2) DEFAULT 0.90,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建序列
CREATE SEQUENCE fault_tree_edges_seq START WITH 1 INCREMENT BY 1 NOMAXVALUE;

-- 创建触发器
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

-- 创建唯一约束
ALTER TABLE fault_tree_edges ADD CONSTRAINT uk_edge_tree UNIQUE (edge_id, tree_id);

-- 创建索引
CREATE INDEX idx_fte_tree_id ON fault_tree_edges(tree_id);
CREATE INDEX idx_fte_source_node ON fault_tree_edges(source_node_id);
CREATE INDEX idx_fte_target_node ON fault_tree_edges(target_node_id);
CREATE INDEX idx_fte_gate_type ON fault_tree_edges(gate_type);

-- ============================================================================
-- 8. fault_tree_versions 表 - 故障树版本历史表
-- ============================================================================
CREATE TABLE fault_tree_versions (
    id NUMBER(19,0) PRIMARY KEY,
    version_id VARCHAR2(100) NOT NULL UNIQUE,
    tree_id VARCHAR2(100) NOT NULL,
    version_number NUMBER(10,0) NOT NULL,
    tree_data_snapshot CLOB NOT NULL,
    change_summary CLOB,
    changed_by VARCHAR2(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ftv_tree FOREIGN KEY (tree_id) REFERENCES fault_trees(tree_id) ON DELETE CASCADE
);

-- 创建序列
CREATE SEQUENCE fault_tree_versions_seq START WITH 1 INCREMENT BY 1 NOMAXVALUE;

-- 创建触发器
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

-- 创建唯一约束
ALTER TABLE fault_tree_versions ADD CONSTRAINT uk_tree_version UNIQUE (tree_id, version_number);

-- 创建索引
CREATE INDEX idx_ftv_tree_id ON fault_tree_versions(tree_id);
CREATE INDEX idx_ftv_version_number ON fault_tree_versions(version_number);


-- ============================================================================
-- 插入示例数据
-- ============================================================================

INSERT INTO document_metadata (doc_id, file_name, file_type, page_count, upload_time, equipment_type, status, source_type, credibility_weight, persist_to_knowledge_base, is_temporary) VALUES
('doc_001', '电机故障手册.pdf', 'PDF', 50, CURRENT_TIMESTAMP, '电动机', 'COMPLETED', 'equipment_manual', 1.0, 1, 0),
('doc_002', '维修记录_2024.txt', 'TXT', 1, CURRENT_TIMESTAMP, '电动机', 'COMPLETED', 'maintenance_record', 0.8, 0, 1);

INSERT INTO paragraph_metadata (paragraph_id, doc_id, section_title, page_number, paragraph_number, text_length, content, source_type, credibility_weight) VALUES
('para_001', 'doc_001', '电机过热原因', 10, 1, 150, '电机过热的原因包括：电源问题、轴承故障或绕组绝缘老化。', 'equipment_manual', 1.0),
('para_002', 'doc_002', '维修记录', 1, 1, 100, '根据历史维修记录，电机过热主要由轴承润滑不良引起。', 'maintenance_record', 0.8);

INSERT INTO vector_store (vector_id, paragraph_id, doc_id, vector_data, vector_dimension, similarity_score) VALUES
('vec_001', 'para_001', 'doc_001', '[0.123,0.456,...]', 768, NULL),
('vec_002', 'para_002', 'doc_002', '[0.234,0.567,...]', 768, NULL);

INSERT INTO fault_trees (tree_id, name, description, equipment_type, top_event, tree_data, created_by, updated_by, validation_status) VALUES
('tree_001', '电机故障树', '电机常见故障分析树', '电动机', '电机过热', '{"nodes":[],"edges":[]}', 'admin', 'admin', 'PENDING');

COMMIT;