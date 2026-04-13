-- 日志模块数据库表结构
-- 适用于 Oracle 数据库

-- 创建操作日志表
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

-- 创建序列
CREATE SEQUENCE operation_log_seq START WITH 1 INCREMENT BY 1 NOMAXVALUE;

-- 创建触发器
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

-- 创建索引以提高查询性能
CREATE INDEX idx_operation_log_user_id ON operation_log(user_id);
CREATE INDEX idx_operation_log_service_name ON operation_log(service_name);
CREATE INDEX idx_operation_log_create_time ON operation_log(create_time);
CREATE INDEX idx_operation_log_operation_type ON operation_log(operation_type);
