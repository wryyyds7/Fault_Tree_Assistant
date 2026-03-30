-- ============================================================================
-- 故障树智能生成系统 - Oracle 数据库
-- 服务: feedback-learning-service
-- ============================================================================

-- 创建用户和表空间
CREATE TABLESPACE feedback_ts DATAFILE 'feedback_ts.dbf' SIZE 100M AUTOEXTEND ON NEXT 10M;
CREATE USER feedback_user IDENTIFIED BY feedback_password DEFAULT TABLESPACE feedback_ts QUOTA UNLIMITED ON feedback_ts;
GRANT CONNECT, RESOURCE, CREATE SESSION TO feedback_user;

-- 切换到feedback_user用户
ALTER SESSION SET CURRENT_SCHEMA = feedback_user;

-- ============================================================================
-- 1. feedback 表 - 反馈表
-- ============================================================================
CREATE TABLE feedback (
    id NUMBER(19,0) PRIMARY KEY,
    feedback_id VARCHAR2(100) UNIQUE NOT NULL,
    tree_id VARCHAR2(100) NOT NULL,
    user_id VARCHAR2(50) NOT NULL,
    rating NUMBER(1,0) CHECK (rating BETWEEN 1 AND 5),
    comments CLOB,
    suggested_changes CLOB,
    created_at TIMESTAMP NOT NULL,
    feedback_type VARCHAR2(20) DEFAULT 'RATING' CHECK (feedback_type IN ('RATING', 'CORRECTION', 'SUGGESTION', 'BUG_REPORT')),
    status VARCHAR2(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PROCESSED', 'REJECTED', 'APPLIED')),
    accuracy_score NUMBER(3,2) DEFAULT 0 CHECK (accuracy_score >= 0 AND accuracy_score <= 1),
    completeness_score NUMBER(3,2) DEFAULT 0 CHECK (completeness_score >= 0 AND completeness_score <= 1),
    clarity_score NUMBER(3,2) DEFAULT 0 CHECK (clarity_score >= 0 AND clarity_score <= 1),
    processed_by VARCHAR2(50),
    processed_at TIMESTAMP,
    applied_to_model NUMBER(1) DEFAULT 0 CHECK (applied_to_model IN (0, 1))
);

-- 创建序列
CREATE SEQUENCE feedback_seq START WITH 1 INCREMENT BY 1 NOMAXVALUE;

-- 创建触发器
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

-- ============================================================================
-- 2. feedback_process_log 表 - 反馈处理记录表
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

-- 创建序列
CREATE SEQUENCE feedback_process_log_seq START WITH 1 INCREMENT BY 1 NOMAXVALUE;

-- 创建触发器
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

-- 创建索引
CREATE INDEX idx_feedback_tree_id ON feedback(tree_id);
CREATE INDEX idx_feedback_user_id ON feedback(user_id);
CREATE INDEX idx_feedback_status ON feedback(status);
CREATE INDEX idx_feedback_created_at ON feedback(created_at);
CREATE INDEX idx_feedback_process_feedback_id ON feedback_process_log(feedback_id);

-- ============================================================================
-- 插入示例数据
-- ============================================================================
INSERT INTO feedback (feedback_id, tree_id, user_id, rating, accuracy_score, completeness_score, clarity_score, comments, suggested_changes, created_at) VALUES
('feedback-001', 'tree-001', 'user-001', 5, 0.9, 0.8, 0.95, '故障树结构清晰，分析全面', '建议增加更多故障类型', CURRENT_TIMESTAMP);

COMMIT;
