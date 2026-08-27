"""搜索问答 HTTP 路由。"""

from __future__ import annotations

import json
import logging

from fastapi import APIRouter, Depends
from fastapi.responses import JSONResponse, StreamingResponse

from assistant_service.agent.registry import invoke, invoke_stream
from assistant_service.app import gateway_user_id
from assistant_service.schemas.search import ApiResponse, SearchRequest

SEARCH_WORKFLOW = "search"

log = logging.getLogger(__name__)

router = APIRouter(tags=["search"])


@router.post("/api/ai/search")
async def ai_search(
    body: SearchRequest,
    _user_id: str = Depends(gateway_user_id),
) -> JSONResponse:
    try:
        data = await invoke(SEARCH_WORKFLOW, body)
        return JSONResponse(content=ApiResponse.ok(data).model_dump(by_alias=True))
    except ValueError as exc:
        return JSONResponse(
            status_code=400,
            content=ApiResponse.fail(40001, str(exc)).model_dump(by_alias=True),
        )
    except Exception as exc:
        return JSONResponse(
            status_code=500,
            content=ApiResponse.fail(50001, f"AI 搜索失败: {exc}").model_dump(by_alias=True),
        )


@router.post("/api/ai/search/stream")
async def ai_search_stream(
    body: SearchRequest,
    _user_id: str = Depends(gateway_user_id),
) -> StreamingResponse:
    async def event_gen():
        try:
            async for evt in invoke_stream(SEARCH_WORKFLOW, body):
                yield (
                    f"event: {evt['event']}\n"
                    f"data: {json.dumps(evt['data'], ensure_ascii=False)}\n\n"
                )
        except ValueError as exc:
            payload = json.dumps({"message": str(exc)}, ensure_ascii=False)
            yield f"event: error\ndata: {payload}\n\n"
        except Exception as exc:
            log.exception("AI 流式搜索失败")
            payload = json.dumps({"message": f"AI 搜索失败: {exc}"}, ensure_ascii=False)
            yield f"event: error\ndata: {payload}\n\n"

    return StreamingResponse(
        event_gen(),
        media_type="text/event-stream; charset=utf-8",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )
