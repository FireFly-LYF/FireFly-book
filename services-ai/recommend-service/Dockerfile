# 骨架阶段：仅 health，无排序 API

FROM python:3.11-slim

WORKDIR /app

COPY firefly-ai-common/ /app/firefly-ai-common/
COPY recommend-service/ /app/recommend-service/

RUN pip install --no-cache-dir -e /app/firefly-ai-common -e /app/recommend-service

ENV RECOMMEND_SERVICE_PORT=9103
EXPOSE 9103

CMD ["python", "-m", "recommend_service.main"]
