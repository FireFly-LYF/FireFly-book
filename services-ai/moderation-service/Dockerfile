# 骨架阶段：仅 health，无 MQ 消费

FROM python:3.11-slim

WORKDIR /app

COPY firefly-ai-common/ /app/firefly-ai-common/
COPY moderation-service/ /app/moderation-service/

RUN pip install --no-cache-dir -e /app/firefly-ai-common -e /app/moderation-service

ENV MODERATION_SERVICE_PORT=9101
EXPOSE 9101

CMD ["python", "-m", "moderation_service.main"]
