-- ============================================================================
-- 故障树智能生成系统 - Oracle 数据库
-- 服务: auth-service
-- ============================================================================

-- 创建用户和表空间
CREATE TABLESPACE auth_ts DATAFILE 'auth_ts.dbf' SIZE 100M AUTOEXTEND ON NEXT 10M;
CREATE USER auth_user IDENTIFIED BY auth_password DEFAULT TABLESPACE auth_ts QUOTA UNLIMITED ON auth_ts;
GRANT CONNECT, RESOURCE, CREATE SESSION TO auth_user;

-- 切换到auth_user用户
ALTER SESSION SET CURRENT_SCHEMA = auth_user;

-- ============================================================================
-- 1. users 表 - 用户表
-- ============================================================================
CREATE TABLE users (
    id NUMBER(19,0) PRIMARY KEY,
    user_id VARCHAR2(50) UNIQUE NOT NULL,
    username VARCHAR2(100) NOT NULL,
    password VARCHAR2(255) NOT NULL,
    email VARCHAR2(100) UNIQUE,
    role VARCHAR2(20) DEFAULT 'USER' CHECK (role IN ('ADMIN', 'EXPERT', 'USER', 'GUEST')),
    enabled NUMBER(1,0) DEFAULT 1 CHECK (enabled IN (0, 1)),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login_time TIMESTAMP,
    password_change_time TIMESTAMP,
    failed_login_attempts NUMBER(3,0) DEFAULT 0,
    lock_time TIMESTAMP,
    password_history CLOB
);

-- 创建序列
CREATE SEQUENCE users_seq START WITH 1 INCREMENT BY 1 NOMAXVALUE;

-- 创建触发器
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

-- 创建更新触发器
CREATE OR REPLACE TRIGGER users_before_update
BEFORE UPDATE ON users
FOR EACH ROW
BEGIN
    :NEW.updated_at := CURRENT_TIMESTAMP;
END;
/

-- ============================================================================
-- 2. user_sessions 表 - 用户会话表（用于token管理）
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

-- 创建序列
CREATE SEQUENCE user_sessions_seq START WITH 1 INCREMENT BY 1 NOMAXVALUE;

-- 创建触发器
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

-- 创建索引
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_enabled ON users(enabled);
CREATE INDEX idx_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_sessions_expires_at ON user_sessions(expires_at);

-- ============================================================================
-- 插入示例数据
-- ============================================================================
INSERT INTO users (user_id, username, password, email, role, enabled) VALUES
('admin-001', 'admin', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'admin@example.com', 'ADMIN', 1),
('expert-001', 'expert', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'expert@example.com', 'EXPERT', 1),
('user-001', 'user', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'user@example.com', 'USER', 1);

COMMIT;
