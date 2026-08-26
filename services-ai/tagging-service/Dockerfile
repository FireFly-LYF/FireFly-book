# 骨架阶段：仅 health，无 MQ 消费

FROM python:3.11-slim

WORKDIR /app

COPY firefly-ai-common/ /app/firefly-ai-common/
COPY tagging-service/ /app/tagging-service/

RUN pip install --no-cache-dir -e /app/firefly-ai-common -e /app/tagging-service

ENV TAGGING_SERVICE_PORT=9102
EXPOSE 9102

CMD ["python", "-m", "tagging_service.main"]
