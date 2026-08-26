# moderation-service：MQ 消费 + 调用独立 NSFW HTTP 服务

FROM python:3.11-slim

WORKDIR /app

COPY firefly-ai-common/ /app/firefly-ai-common/
COPY moderation-service/ /app/moderation-service/

RUN pip install --no-cache-dir -e /app/firefly-ai-common -e /app/moderation-service

ENV MODERATION_SERVICE_PORT=9101
ENV NSFW_DETECTOR_URL=http://nsfw-detector:3333
ENV NSFW_THRESHOLD=0.5
ENV IMAGE_MODERATION_ENABLED=true

EXPOSE 9101

CMD ["python", "-m", "moderation_service.main"]
