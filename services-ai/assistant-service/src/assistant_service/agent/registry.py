"""Workflow 注册表（确定性编排，非自主 agent）。"""

from __future__ import annotations

from collections.abc import AsyncIterator, Awaitable, Callable

from assistant_service.schemas.search import SearchData, SearchRequest
from assistant_service.workflows.search_answer import run_search, run_search_stream

WorkflowSync = Callable[[SearchRequest], Awaitable[SearchData]]
WorkflowStream = Callable[[SearchRequest], AsyncIterator[dict]]

WORKFLOWS: dict[str, WorkflowSync] = {
    "search": run_search,
}

STREAM_WORKFLOWS: dict[str, WorkflowStream] = {
    "search": run_search_stream,
}


def invoke(name: str, req: SearchRequest) -> Awaitable[SearchData]:
    """按名称执行同步 workflow。"""
    wf = WORKFLOWS.get(name)
    if wf is None:
        raise ValueError(f"未知 workflow: {name}")
    return wf(req)


async def invoke_stream(name: str, req: SearchRequest) -> AsyncIterator[dict]:
    """按名称执行流式 workflow。"""
    wf = STREAM_WORKFLOWS.get(name)
    if wf is None:
        raise ValueError(f"未知 workflow: {name}")
    async for evt in wf(req):
        yield evt
