#!/bin/bash

# 启动所有服务
echo "Starting all services..."
docker-compose up -d

# 检查服务状态
echo "Checking service status..."
docker-compose ps

# 输出访问地址
echo "\nServices are now running:"
echo "Frontend: http://localhost:3000"
echo "RabbitMQ Management: http://localhost:15672"
echo "Neo4j Browser: http://localhost:7474"
echo "MinIO Console: http://localhost:9001"
echo "\nDocument Ingest Service: http://localhost:8081"
echo "Knowledge Graph Service: http://localhost:8082"
echo "Rule Validation Service: http://localhost:8083"
echo "Fault Tree Editor Service: http://localhost:8084"
echo "Feedback Learning Service: http://localhost:8085"
