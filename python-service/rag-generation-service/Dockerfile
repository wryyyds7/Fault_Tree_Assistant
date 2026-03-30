FROM python:3.10-slim

WORKDIR /app

COPY . /app

# 安装依赖
RUN pip install --no-cache-dir -r requirements.txt

# 安装industrial_fta_common
RUN pip install --no-cache-dir -e /app/../industrial_fta_common

EXPOSE 8000

CMD ["python", "rag_service.py"]