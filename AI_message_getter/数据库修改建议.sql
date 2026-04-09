================================================================================
                    故障树智能生成系统 - 数据库修改建议
================================================================================

【更新日期】2026-04-08
【项目路径】d:\bianchenglianxi\project\Fault_Tree_Assistant
【数据库】Oracle XEPDB1

================================================================================
一、数据库概述
================================================================================

  本系统使用 Oracle XEPDB1 数据库，采用多 Schema 设计：
  - auth_db: 用户认证数据
  - fta_db / fta_vector_db: 业务数据（故障树、向量存储）

  连接信息：
  - URL: jdbc:oracle:thin:@localhost:1521:XEPDB1
  - 驱动: oracle.jdbc.OracleDriver

================================================================================
二、数据库表结构
================================================================================

┌─────────────────────────────────────────────────────────────────────────────────┐
│  第一部分: 向量存储服务 (vector-store-service)                                    │
├─────────────────────────────────────────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────────────────────────────────────────┐
  │  表1: DOCUMENT_METADATA (文档元数据表)                                           │
  ├─────────────────────────────────────────────────────────────────────────────────┤
  │                                                                                 │
  │  字段                    类型              说明                                  │
  │  ───────────────────────────────────────────────────────────────────────────   │
  │  id                     NUMBER(19,0)      主键                                 │
  │  doc_id                 VARCHAR2(100)     文档唯一标识 (UNIQUE)                  │
  │  file_name              VARCHAR2(255)     文件名                               │
  │  file_type              VARCHAR2(50)      文件类型                             │
  │  page_count             NUMBER(10,0)      页数                                 │
  │  upload_time            TIMESTAMP         上传时间                             │
  │  equipment_type         VARCHAR2(100)     设备类型                             │
  │  status                 VARCHAR2(20)      状态 (PENDING/COMPLETED/FAILED)     │
  │  source_type            VARCHAR2(50)      来源类型                             │
  │  credibility_weight     NUMBER(3,2)      可信度权重 (默认0.50)                │
  │  persist_to_knowledge_base NUMBER(1,0)   是否持久化到知识图谱                 │
  │  is_temporary           NUMBER(1,0)       是否临时 (默认1)                    │
  │  expires_at             TIMESTAMP         过期时间                             │
  │  created_at             TIMESTAMP         创建时间                             │
  │  updated_at             TIMESTAMP         更新时间                             │
  │                                                                                 │
  │  索引:                                                                         │
  │  - idx_doc_metadata_doc_id (doc_id)                                           │
  │  - idx_doc_metadata_equipment (equipment_type)                               │
  │  - idx_doc_metadata_status (status)                                           │
  │  - idx_doc_metadata_source (source_type)                                      │
  │                                                                                 │
  └─────────────────────────────────────────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────────────────────────────────────────┐
  │  表2: PARAGRAPH_METADATA (段落元数据表)                                         │
  ├─────────────────────────────────────────────────────────────────────────────────┤
  │                                                                                 │
  │  字段                    类型              说明                                  │
  │  ───────────────────────────────────────────────────────────────────────────   │
  │  id                     NUMBER(19,0)      主键                                 │
  │  paragraph_id           VARCHAR2(100)     段落唯一标识 (UNIQUE)                 │
  │  doc_id                 VARCHAR2(100)     关联文档ID (外键)                     │
  │  section_title          VARCHAR2(255)     章节标题                             │
  │  page_number            NUMBER(10,0)      页码                                 │
  │  paragraph_number       NUMBER(10,0)      段落编号                             │
  │  text_length            NUMBER(10,0)      文本长度                             │
  │  keywords               VARCHAR2(500)     关键词                               │
  │  confidence_score       NUMBER(3,2)      置信度分数                           │
  │  content                CLOB              段落内容                             │
  │  source_type            VARCHAR2(50)      来源类型                             │
  │  credibility_weight     NUMBER(3,2)      可信度权重                           │
  │  created_at             TIMESTAMP         创建时间                             │
  │  updated_at             TIMESTAMP         更新时间                             │
  │                                                                                 │
  │  外键: fk_pm_doc → document_metadata(doc_id)                                  │
  │  索引:                                                                         │
  │  - idx_para_meta_paragraph_id (paragraph_id)                                 │
  │  - idx_para_meta_doc_id (doc_id)                                              │
  │  - idx_para_meta_source (source_type)                                         │
  │                                                                                 │
  └─────────────────────────────────────────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────────────────────────────────────────┐
  │  表3: VECTOR_STORE (向量存储表)                                                 │
  ├─────────────────────────────────────────────────────────────────────────────────┤
  │                                                                                 │
  │  字段                    类型              说明                                  │
  │  ───────────────────────────────────────────────────────────────────────────   │
  │  id                     NUMBER(19,0)      主键                                 │
  │  vector_id              VARCHAR2(100)     向量唯一标识 (UNIQUE)                 │
  │  paragraph_id           VARCHAR2(100)     关联段落ID (外键)                     │
  │  doc_id                 VARCHAR2(100)     关联文档ID (外键)                     │
  │  vector_data            CLOB              向量数据 (JSON格式)                    │
  │  vector_dimension       NUMBER(10,0)      向量维度 (默认768)                   │
  │  embedding_model        VARCHAR2(50)      嵌入模型 (默认BGE-M3)               │
  │  similarity_score       NUMBER(5,4)      相似度分数                           │
  │  created_by             VARCHAR2(50)      创建人                               │
  │  created_at             TIMESTAMP         创建时间                             │
  │  updated_at             TIMESTAMP         更新时间                             │
  │                                                                                 │
  │  外键:                                                                         │
  │  - fk_vs_paragraph → paragraph_metadata(paragraph_id)                         │
  │  - fk_vs_doc → document_metadata(doc_id)                                      │
  │                                                                                 │
  └─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────┐
│  第二部分: 故障树编辑服务 (fault-tree-editor-service)                             │
├─────────────────────────────────────────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────────────────────────────────────────┐
  │  表4: FAULT_TREES (故障树主表)                                                  │
  ├─────────────────────────────────────────────────────────────────────────────────┤
  │                                                                                 │
  │  字段                    类型              说明                                  │
  │  ───────────────────────────────────────────────────────────────────────────   │
  │  id                     NUMBER(19,0)      主键                                 │
  │  tree_id                VARCHAR2(100)     故障树唯一标识 (UNIQUE)                │
  │  name                   VARCHAR2(255)     故障树名称                           │
  │  description            CLOB              描述                                 │
  │  equipment_type         VARCHAR2(100)     设备类型                             │
  │  top_event              VARCHAR2(255)     顶事件名称                           │
  │  tree_data              CLOB              树结构数据 (JSON)                    │
  │  version                NUMBER(10,0)      版本号 (默认1)                       │
  │  validation_status      VARCHAR2(20)      校验状态                             │
  │  validation_message     CLOB              校验消息                             │
  │  source_doc_ids         CLOB              源文档ID列表                         │
  │  source_detail          CLOB              源详情                               │
  │  publish_status         VARCHAR2(20)      发布状态 (DRAFT/PUBLISHED)           │
  │  fusion_statistics      CLOB              融合统计                             │
  │  generated_by           VARCHAR2(50)      生成方式 (AI/MANUAL/HYBRID)         │
  │  template_id            VARCHAR2(100)     模板ID                              │
  │  created_by             VARCHAR2(50)      创建人                              │
  │  updated_by             VARCHAR2(50)      更新人                              │
  │  created_at             TIMESTAMP         创建时间                             │
  │  updated_at             TIMESTAMP         更新时间                             │
  │                                                                                 │
  │  索引:                                                                         │
  │  - idx_fault_trees_tree_id (tree_id)                                           │
  │  - idx_fault_trees_equipment (equipment_type)                                 │
  │  - idx_fault_trees_validation (validation_status)                             │
  │  - idx_fault_trees_publish (publish_status)                                   │
  │  - idx_fault_trees_created_by (created_by)                                    │
  │                                                                                 │
  └─────────────────────────────────────────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────────────────────────────────────────┐
  │  表5: FAULT_TREE_NODES (故障树节点表)                                           │
  ├─────────────────────────────────────────────────────────────────────────────────┤
  │                                                                                 │
  │  字段                    类型              说明                                  │
  │  ───────────────────────────────────────────────────────────────────────────   │
  │  id                     NUMBER(19,0)      主键                                 │
  │  node_id                VARCHAR2(100)     节点唯一标识                           │
  │  tree_id                VARCHAR2(100)     所属故障树ID (外键)                    │
  │  node_name              VARCHAR2(255)     节点名称                             │
  │  node_type              VARCHAR2(20)      节点类型 (TOP/EVENT/GATE)             │
  │  gate_type              VARCHAR2(20)      逻辑门类型 (AND/OR/XOR/etc.)          │
  │  description            CLOB              描述                                 │
  │  confidence             NUMBER(3,2)       置信度 (默认0.85)                    │
  │  source_type            VARCHAR2(50)      来源类型                             │
  │  source_detail          CLOB              来源详情                             │
  │  paragraph_id           VARCHAR2(100)     关联段落ID                           │
  │  verification_status    VARCHAR2(20)      验证状态 (PENDING/VERIFIED/REJECTED) │
  │  verified_by            VARCHAR2(50)      验证人                               │
  │  verified_at           TIMESTAMP         验证时间                             │
  │  position_x            NUMBER(10,0)      X坐标                               │
  │  position_y            NUMBER(10,0)      Y坐标                               │
  │  ai_generated          NUMBER(1,0)       是否AI生成                           │
  │  generation_mode       VARCHAR2(20)      生成模式 (hybrid/knowledge/data)     │
  │  section_title          VARCHAR2(255)     章节标题                             │
  │  similarity_score       NUMBER(5,4)      相似度分数                           │
  │  source_evidence        CLOB              来源证据                             │
  │  event_id               VARCHAR2(100)     事件ID                              │
  │  created_at            TIMESTAMP         创建时间                             │
  │  updated_at            TIMESTAMP         更新时间                             │
  │                                                                                 │
  │  约束: UNIQUE (node_id, tree_id)                                              │
  │  外键: fk_ftn_tree → fault_trees(tree_id)                                     │
  │  索引:                                                                         │
  │  - idx_ftn_tree (tree_id)                                                      │
  │  - idx_ftn_type (node_type)                                                    │
  │                                                                                 │
  └─────────────────────────────────────────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────────────────────────────────────────┐
  │  表6: FAULT_TREE_EDGES (故障树节点关系表)                                        │
  ├─────────────────────────────────────────────────────────────────────────────────┤
  │                                                                                 │
  │  字段                    类型              说明                                  │
  │  ───────────────────────────────────────────────────────────────────────────   │
  │  id                     NUMBER(19,0)      主键                                 │
  │  edge_id                VARCHAR2(100)     边唯一标识 (UNIQUE)                    │
  │  tree_id                VARCHAR2(100)     所属故障树ID (外键)                   │
  │  source_node_id          VARCHAR2(100)     源节点ID                            │
  │  target_node_id          VARCHAR2(100)     目标节点ID                          │
  │  gate_type              VARCHAR2(20)      逻辑门类型 (默认OR)                   │
  │  probability            NUMBER(5,4)       概率                                 │
  │  description            VARCHAR2(500)     描述                                 │
  │  confidence             NUMBER(3,2)       置信度 (默认0.90)                    │
  │  created_at             TIMESTAMP         创建时间                             │
  │                                                                                 │
  │  约束: UNIQUE (edge_id, tree_id)                                              │
  │  外键: fk_fte_tree → fault_trees(tree_id)                                     │
  │  索引:                                                                         │
  │  - idx_fte_tree (tree_id)                                                      │
  │  - idx_fte_source (source_node_id)                                            │
  │  - idx_fte_target (target_node_id)                                            │
  │                                                                                 │
  └─────────────────────────────────────────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────────────────────────────────────────┐
  │  表7: FAULT_TREE_VERSIONS (故障树版本历史表)                                    │
  ├─────────────────────────────────────────────────────────────────────────────────┤
  │                                                                                 │
  │  字段                    类型              说明                                  │
  │  ───────────────────────────────────────────────────────────────────────────   │
  │  id                     NUMBER(19,0)      主键                                 │
  │  version_id             VARCHAR2(100)     版本唯一标识 (UNIQUE)                  │
  │  tree_id                VARCHAR2(100)     所属故障树ID (外键)                   │
  │  version_number         NUMBER(10,0)      版本号                               │
  │  tree_data_snapshot     CLOB              树数据快照                           │
  │  change_summary         CLOB              变更摘要                             │
  │  changed_by             VARCHAR2(50)      变更人                               │
  │  created_at             TIMESTAMP         创建时间                             │
  │                                                                                 │
  │  约束: UNIQUE (tree_id, version_number)                                       │
  │  外键: fk_ftv_tree → fault_trees(tree_id)                                     │
  │                                                                                 │
  └─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────┐
│  第三部分: 认证服务 (auth-service)                                               │
├─────────────────────────────────────────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────────────────────────────────────────┐
  │  表8: USERS (用户表)                                                             │
  ├─────────────────────────────────────────────────────────────────────────────────┤
  │                                                                                 │
  │  字段                    类型              说明                                  │
  │  ───────────────────────────────────────────────────────────────────────────   │
  │  id                     NUMBER(19,0)      主键                                 │
  │  user_id                VARCHAR2(50)      用户唯一标识 (UNIQUE)                  │
  │  username               VARCHAR2(100)     用户名 (UNIQUE)                       │
  │  password               VARCHAR2(255)     密码 (加密存储)                       │
  │  email                  VARCHAR2(255)     邮箱 (UNIQUE)                        │
  │  role                   VARCHAR2(50)      角色 (默认USER)                      │
  │  last_login_time        TIMESTAMP         最后登录时间                         │
  │  password_change_time   TIMESTAMP         密码修改时间                         │
  │  failed_login_attempts   NUMBER(3,0)       失败登录次数                         │
  │  lock_time              TIMESTAMP         锁定时间                             │
  │  password_history       CLOB              密码历史                             │
  │  created_at             TIMESTAMP         创建时间                             │
  │                                                                                 │
  │  索引:                                                                         │
  │  - idx_users_user_id (user_id)                                                │
  │  - idx_users_username (username)                                              │
  │  - idx_users_email (email)                                                    │
  │  - idx_users_role (role)                                                      │
  │                                                                                 │
  └─────────────────────────────────────────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────────────────────────────────────────┐
  │  表9: USER_SESSIONS (用户会话表)                                                │
  ├─────────────────────────────────────────────────────────────────────────────────┤
  │                                                                                 │
  │  字段                    类型              说明                                  │
  │  ───────────────────────────────────────────────────────────────────────────   │
  │  id                     NUMBER(19,0)      主键                                 │
  │  session_id             VARCHAR2(100)     会话ID (UNIQUE)                       │
  │  user_id                VARCHAR2(50)      用户ID (外键)                         │
  │  token                  VARCHAR2(500)     JWT Token                            │
  │  refresh_token          VARCHAR2(500)     刷新Token                           │
  │  device_info            VARCHAR2(200)     设备信息                             │
  │  ip_address             VARCHAR2(50)      IP地址                              │
  │  expires_at             TIMESTAMP         过期时间                             │
  │  created_at             TIMESTAMP         创建时间                             │
  │                                                                                 │
  │  外键: fk_us_user → users(user_id)                                            │
  │                                                                                 │
  └─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────┐
│  第四部分: 反馈学习服务 (feedback-learning-service)                                │
├─────────────────────────────────────────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────────────────────────────────────────┐
  │  表10: FEEDBACK (反馈表)                                                         │
  ├─────────────────────────────────────────────────────────────────────────────────┤
  │                                                                                 │
  │  字段                    类型              说明                                  │
  │  ───────────────────────────────────────────────────────────────────────────   │
  │  id                     NUMBER(19,0)      主键                                 │
  │  feedback_id            VARCHAR2(100)     反馈唯一标识 (UNIQUE)                  │
  │  tree_id                VARCHAR2(100)     关联故障树ID                          │
  │  user_id                VARCHAR2(50)      用户ID                               │
  │  feedback_type          VARCHAR2(50)      反馈类型                             │
  │  content                CLOB              反馈内容                             │
  │  status                 VARCHAR2(20)      处理状态                             │
  │  processed_at           TIMESTAMP         处理时间                             │
  │  created_at             TIMESTAMP         创建时间                             │
  │  updated_at             TIMESTAMP         更新时间                             │
  │                                                                                 │
  │  索引:                                                                         │
  │  - idx_feedback_tree (tree_id)                                                │
  │  - idx_feedback_user (user_id)                                                │
  │  - idx_feedback_type (feedback_type)                                           │
  │                                                                                 │
  └─────────────────────────────────────────────────────────────────────────────────┘

================================================================================
三、序列和触发器
================================================================================

  每个表都有对应的序列和触发器，格式如下：
  - 序列: {table_name}_seq (START WITH 1 INCREMENT BY 1 NOMAXVALUE)
  - 触发器: {table_name}_before_insert (自动填充ID和时间戳)

================================================================================
四、修改建议
================================================================================

┌─────────────────────────────────────────────────────────────────────────────────┐
│  1. 索引优化建议                                                                  │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  对于高频查询场景，建议添加以下复合索引：                                         │
│                                                                                 │
│  -- 故障树多条件查询                                                             │
│  CREATE INDEX idx_ft_composite ON fault_trees(                                  │
│      equipment_type, validation_status, publish_status                          │
│  );                                                                             │
│                                                                                 │
│  -- 段落全文检索优化                                                             │
│  CREATE INDEX idx_pm_keywords ON paragraph_metadata(keywords);                  │
│                                                                                 │
│  -- 时间范围查询                                                                 │
│  CREATE INDEX idx_dm_upload_time ON document_metadata(upload_time);            │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────┐
│  2. 分区表建议 (适用于大数据量场景)                                               │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  当数据量超过100万条时，考虑按时间分区：                                           │
│                                                                                 │
│  -- 按月分区示例                                                                 │
│  CREATE TABLE fault_trees (...)                                                 │
│  PARTITION BY RANGE (created_at) (                                             │
│      PARTITION p2026_01 VALUES LESS THAN (TIMESTAMP '2026-02-01'),             │
│      PARTITION p2026_02 VALUES LESS THAN (TIMESTAMP '2026-03-01'),             │
│      PARTITION p2026_03 VALUES LESS THAN (TIMESTAMP '2026-04-01'),             │
│      PARTITION p_future VALUES LESS THAN (MAXVALUE)                            │
│  );                                                                             │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────┐
│  3. 向量存储优化                                                                  │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  当前 vector_data 使用 CLOB 存储向量序列化后的JSON，                             │
│  建议在生产环境使用 Oracle Vector 或专用向量数据库：                             │
│                                                                                 │
│  -- 添加向量维度索引 (如果使用 Oracle 23c+)                                      │
│  CREATE VECTOR INDEX vector_idx ON vector_store(vector_data)                   │
│  ORGANIZATION NEIGHBOR PARTITIONS                                             │
│  DISTANCES (COSINE);                                                           │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────┐
│  4. 数据清理策略                                                                  │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  建议定期执行以下清理任务：                                                       │
│                                                                                 │
│  -- 清理30天前的临时文档                                                         │
│  DELETE FROM document_metadata                                                 │
│  WHERE is_temporary = 1 AND created_at < SYSDATE - 30;                         │
│                                                                                 │
│  -- 清理过期会话                                                                 │
│  DELETE FROM user_sessions                                                     │
│  WHERE expires_at < SYSDATE;                                                   │
│                                                                                 │
│  -- 清理7天前的版本快照 (保留最近N个版本)                                         │
│  -- 需要配合应用程序逻辑实现                                                     │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘

================================================================================
五、数据库初始化脚本位置
================================================================================

  完整数据库脚本位于: d:\bianchenglianxi\project\Fault_Tree_Assistant\sql\

  脚本文件说明：
  - complete_oracle_schema.sql    - 完整表结构（含序列、触发器、索引）
  - auth_db.sql                   - 认证服务专用表
  - fta_db.sql                    - 故障树服务表
  - feedback_db.sql               - 反馈服务表
  - user_data_isolation.sql       - 用户数据隔离策略

================================================================================

*文档版本：2.0*
*最后更新：2026-04-08*
