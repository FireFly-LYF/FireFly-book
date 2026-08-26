"""FastAPI 应用（骨架：/health，/internal/rank 占位）。"""

import os

from fastapi import FastAPI

from firefly_ai_common.health import health_payload
from firefly_ai_common.models.rank import RankRequest, RankResponse

SERVICE_NAME = "recommend-service"
DEFAULT_PORT = 9103


def create_app() -> FastAPI:
    app = FastAPI(title=SERVICE_NAME, version="0.1.0")
    port = int(os.getenv("RECOMMEND_SERVICE_PORT", DEFAULT_PORT))

    @app.get("/health")
    def health() -> dict:
        return health_payload(SERVICE_NAME, port)

    @app.post("/internal/rank", response_model=RankResponse)
    def rank_stub(body: RankRequest) -> RankResponse:
        # 骨架：原样返回候选列表
        return RankResponse(note_ids=body.candidate_note_ids)

    return app
