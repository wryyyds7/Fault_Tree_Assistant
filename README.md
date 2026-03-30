# 基于知识的工业设备故障树智能生成与辅助构建系统

## 项目概述

本项目为“基于知识的工业设备故障树智能生成与辅助构建系统”，面向高端制造运维场景，旨在通过融合大模型、知识图谱与规则引擎，实现从非结构化工业文档中自动抽取故障逻辑、生成符合FTA（Fault Tree Analysis）标准的故障树，并支持专家可视化交互修正。

## 系统架构

系统采用 Spring Cloud + Vue3 + Python（AI后端） 三层架构，模块间通信以 RabbitMQ 异步解耦为主、Feign 同步调用为辅，确保高内聚低耦合与可扩展性。

### 核心模块

1. **document-ingest-service**：工业文档上传、解析、预处理
2. **knowledge-graph-service**：轻量故障知识图谱构建与管理（基于ISO 13379本体）
3. **rag-generation-service**（Python）：RAG增强的大模型初稿生成
4. **rule-validation-service**：故障树逻辑校验与合规检查
5. **fault-tree-editor-service**：故障树版本管理、CRUD、协作编辑状态同步
6. **feedback-learning-service**：专家修正反馈收集与模型优化触发
7. **frontend**：Vue3前端界面，提供可视化交互

### 技术栈

- **后端**：Java Spring Boot 3.x, Spring Cloud, Neo4j, RabbitMQ
- **前端**：Vue3, Element Plus, WebSocket
- **AI**：Python, Pydantic, FAISS, Transformers, Qwen-Max API
- **容器化**：Docker, Docker Compose

## 快速开始

### 环境要求

- Docker
- Docker Compose

### 启动服务

1. 克隆项目

2. 进入项目根目录

3. 启动所有服务：

```bash
./start.sh
```

### 访问地址

- **前端**：http://localhost:3000
- **RabbitMQ Management**：http://localhost:15672
- **Neo4j Browser**：http://localhost:7474
- **MinIO Console**：http://localhost:9001

### 后端服务API

- **Document Ingest Service**：http://localhost:8081/api/v1/documents
- **Knowledge Graph Service**：http://localhost:8082/api/v1/kg
- **Rule Validation Service**：http://localhost:8083/api/v1/validation
- **Fault Tree Editor Service**：http://localhost:8084/api/v1/fault-trees
- **Feedback Learning Service**：http://localhost:8085/api/v1/feedback

## 系统功能

### 1. 故障树AI智能生成
- 工业知识提取：基于设备手册、公开资料等数据，通过AI技术提取故障事件、事件关联关系、逻辑门规则等核心信息
- 自动生成故障树：基于提取的知识及用户要求，按照FTA规范自动构建完整故障树，清晰呈现“顶事件-中间事件-底事件”的逻辑传导路径

### 2. 专家辅助构建与优化
- 可视化编辑：提供直观的故障树图形化编辑界面，支持事件节点增删、关联关系调整、逻辑门修改等操作
- 逻辑校验与提示：AI自动校验故障树逻辑合理性（如循环关联、逻辑冲突等），并给出优化建议

### 3. 知识图谱管理
- 基于Neo4j构建轻量故障本体图谱，预加载ISO 13379标准事件模板
- 支持动态扩展和知识沉淀

### 4. 文档处理
- 支持PDF、DOCX、TXT等格式设备手册或维修日志的上传和解析
- 执行OCR（PDF图像页）、段落切分、元数据提取

## 项目结构

```
├── AI_message_getter/          # 项目要求和功能文档
├── backend/                    # 后端服务
│   ├── common-core/            # 共享核心库
│   ├── document-ingest-service/ # 文档摄入服务
│   ├── fault-tree-editor-service/ # 故障树编辑器服务
│   ├── feedback-learning-service/ # 反馈学习服务
│   ├── knowledge-graph-service/ # 知识图谱服务
│   ├── rule-validation-service/ # 规则校验服务
├── data/                       # 数据文件
├── frontend/                   # 前端项目
├── python-service/             # Python服务
│   ├── industrial_fta_common/  # Python共享库
│   ├── rag-generation-service/ # RAG生成服务
├── docker-compose.yml          # Docker Compose配置
├── start.sh                    # 启动脚本
└── README.md                   # 项目说明
```

## 配置说明

### 环境变量

- **API_KEY**：Qwen-Max API密钥
- **API_URL**：Qwen-Max API地址

### Docker Compose配置

通过`docker-compose.yml`文件配置所有服务的参数，包括：
- 消息队列（RabbitMQ）
- 知识图谱数据库（Neo4j）
- 文档存储（MinIO）
- 后端服务
- Python服务
- 前端服务

## 开发指南

### 后端开发

1. 进入`backend`目录
2. 运行`./mvnw clean package`构建所有服务
3. 运行`java -jar <service>/target/<service>-1.0.0.jar`启动单个服务

### 前端开发

1. 进入`frontend`目录
2. 运行`npm install`安装依赖
3. 运行`npm run dev`启动开发服务器

### Python服务开发

1. 进入`python-service/rag-generation-service`目录
2. 运行`pip install -r requirements.txt`安装依赖
3. 运行`pip install -e ../industrial_fta_common`安装共享库
4. 运行`python rag_service.py`启动服务

## 故障排除

### 服务启动失败

- 检查Docker是否运行
- 检查端口是否被占用
- 检查环境变量是否正确设置

### 前端无法访问后端服务

- 检查后端服务是否运行
- 检查网络连接是否正常
- 检查API地址是否正确

### 故障树生成失败

- 检查Python服务是否运行
- 检查Qwen-Max API配置是否正确
- 检查文档内容是否完整

## 贡献指南

1. Fork项目
2. 创建功能分支
3. 提交更改
4. 推送到分支
5. 创建Pull Request

## 许可证

本项目采用MIT许可证。