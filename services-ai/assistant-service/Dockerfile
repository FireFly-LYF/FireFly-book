# assistant-service：笔记 + 网络搜索 + LLM 问答

FROM python:3.11-slim

WORKDIR /app

COPY firefly-ai-common/ /app/firefly-ai-common/
COPY assistant-service/ /app/assistant-service/

RUN pip install --no-cache-dir -e /app/firefly-ai-common -e /app/assistant-service

ENV ASSISTANT_SERVICE_PORT=9102
EXPOSE 9102

CMD ["python", "-m", "assistant_service.main"]
