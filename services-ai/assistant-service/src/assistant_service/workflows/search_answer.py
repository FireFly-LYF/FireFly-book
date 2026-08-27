"""搜索问答 workflow。

步骤：准备材料（笔记 + 可选网络）→ 调 LLM → 一次性返回或 SSE 推流。
"""

from __future__ import annotations

import asyncio
import logging
import time
from collections.abc import AsyncIterator
from dataclasses import dataclass

from assistant_service.schemas.search import NoteSource, SearchData, SearchRequest, WebSource
from assistant_service.settings import AssistantSettings, load_assistant_settings
from assistant_service.agent.llm import clean_answer, generate_answer, iter_answer_chunks
from assistant_service.tools.notes import (
    fetch_note_sources,
    note_sources_from_candidates,
    notes_for_llm,
)
from assistant_service.agent.sync_stream import stream_sync_in_thread
from assistant_service.tools.web_search import search_web, web_for_llm

log = logging.getLogger(__name__)

WEB_DISABLED = "（用户关闭了网络搜索）"
WEB_EMPTY = "（无网络搜索结果）"


@dataclass
class SearchContext:
    query: str
    notes_block: str
    web_block: str
    note_sources: list[NoteSource]
    web_sources: list[WebSource]
    cfg: AssistantSettings


def _web_block_for(include_web: bool, web_sources: list[WebSource]) -> str:
    if not include_web:
        return WEB_DISABLED
    if not web_sources:
        return WEB_EMPTY
    return web_for_llm(web_sources)


async def _prepare_search_context(req: SearchRequest) -> SearchContext:
    cfg = load_assistant_settings()
    query = req.query.strip()
    note_limit = min(req.note_limit, cfg.note_search_size, 10)

    if req.note_candidates:
        note_sources = note_sources_from_candidates(
            req.note_candidates,
            limit=note_limit,
            settings=cfg,
        )
        web_sources: list[WebSource] = []
        if req.include_web and cfg.web_search_enabled:
            web_sources = await asyncio.to_thread(search_web, query, settings=cfg)
    else:

        async def _notes() -> list[NoteSource]:
            return await asyncio.to_thread(
                fetch_note_sources,
                query,
                limit=note_limit,
                settings=cfg,
            )

        async def _web() -> list[WebSource]:
            if not req.include_web or not cfg.web_search_enabled:
                return []
            return await asyncio.to_thread(search_web, query, settings=cfg)

        note_sources, web_sources = await asyncio.gather(_notes(), _web())

    return SearchContext(
        query=query,
        notes_block=notes_for_llm(note_sources),
        web_block=_web_block_for(req.include_web, web_sources),
        note_sources=note_sources,
        web_sources=web_sources if req.include_web else [],
        cfg=cfg,
    )


async def run_search(req: SearchRequest) -> SearchData:
    """同步 workflow：准备材料 → LLM → 完整答案。"""
    t0 = time.perf_counter()
    ctx = await _prepare_search_context(req)

    answer = await asyncio.to_thread(
        generate_answer,
        ctx.query,
        ctx.notes_block,
        ctx.web_block,
        settings=ctx.cfg,
    )
    log.info(
        "search_answer 完成 query=%s notes=%d web=%d total=%.2fs",
        ctx.query[:30],
        len(ctx.note_sources),
        len(ctx.web_sources),
        time.perf_counter() - t0,
    )
    return SearchData(
        answer=answer,
        note_sources=ctx.note_sources,
        web_sources=ctx.web_sources,
    )


async def run_search_stream(req: SearchRequest) -> AsyncIterator[dict]:
    """流式 workflow：meta → delta* → done。"""
    t0 = time.perf_counter()
    ctx = await _prepare_search_context(req)

    yield {
        "event": "meta",
        "data": {
            "noteSources": [n.model_dump(by_alias=True) for n in ctx.note_sources],
            "webSources": [w.model_dump(by_alias=True) for w in ctx.web_sources],
        },
    }

    parts: list[str] = []
    async for chunk in stream_sync_in_thread(
        iter_answer_chunks(
            ctx.query,
            ctx.notes_block,
            ctx.web_block,
            settings=ctx.cfg,
        )
    ):
        parts.append(chunk)
        yield {"event": "delta", "data": {"text": chunk}}

    answer = clean_answer("".join(parts))
    log.info(
        "search_answer(stream) 完成 query=%s notes=%d chunks=%d total=%.2fs",
        ctx.query[:30],
        len(ctx.note_sources),
        len(parts),
        time.perf_counter() - t0,
    )
    yield {"event": "done", "data": {"answer": answer}}
