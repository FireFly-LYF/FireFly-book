"""站内笔记检索工具（search-service）。"""

from __future__ import annotations

import logging

from firefly_ai_common.clients.search_client import SearchClient, SearchClientError

from assistant_service.schemas.search import NoteCandidate, NoteSource
from assistant_service.settings import AssistantSettings, load_assistant_settings

log = logging.getLogger(__name__)


def _snippet(text: str | None, max_len: int) -> str | None:
    if not text:
        return None
    t = " ".join(str(text).split())
    if len(t) <= max_len:
        return t
    return f"{t[:max_len]}…"


def _to_note_source(
    *,
    note_id: int,
    title: str | None,
    content: str | None,
    cover_url: str | None,
    snippet_max: int,
) -> NoteSource:
    return NoteSource(
        id=note_id,
        title=title,
        snippet=_snippet(content, snippet_max),
        cover_url=cover_url,
    )


def _dedupe_cap(
    items: list[tuple[int, str | None, str | None, str | None]],
    *,
    limit: int,
    snippet_max: int,
) -> list[NoteSource]:
    cap = max(1, min(limit, 10))
    seen: set[int] = set()
    out: list[NoteSource] = []
    for note_id, title, content, cover_url in items:
        if note_id in seen:
            continue
        seen.add(note_id)
        out.append(
            _to_note_source(
                note_id=note_id,
                title=title,
                content=content,
                cover_url=cover_url,
                snippet_max=snippet_max,
            )
        )
        if len(out) >= cap:
            break
    return out


def note_sources_from_candidates(
    candidates: list[NoteCandidate],
    *,
    limit: int | None = None,
    snippet_max: int | None = None,
    settings: AssistantSettings | None = None,
) -> list[NoteSource]:
    """将前端传入的检索结果转为 NoteSource（不再调 search-service）。"""
    cfg = settings or load_assistant_settings()
    cap = limit if limit is not None else cfg.note_search_size
    snip = snippet_max if snippet_max is not None else cfg.note_snippet_max
    rows = [
        (c.id, c.title, c.content, c.cover_url)
        for c in candidates
    ]
    return _dedupe_cap(rows, limit=cap, snippet_max=snip)


def fetch_note_sources(
    query: str,
    *,
    limit: int | None = None,
    snippet_max: int | None = None,
    client: SearchClient | None = None,
    settings: AssistantSettings | None = None,
) -> list[NoteSource]:
    cfg = settings or load_assistant_settings()
    cap = limit if limit is not None else cfg.note_search_size
    snip = snippet_max if snippet_max is not None else cfg.note_snippet_max
    search = client or SearchClient.from_settings()
    owns = client is None
    try:
        rows_raw = search.search_notes(query, size=cap)
    except SearchClientError as exc:
        log.warning("站内搜索失败: %s", exc)
        return []
    finally:
        if owns:
            search.close()

    items: list[tuple[int, str | None, str | None, str | None]] = []
    for row in rows_raw:
        note_id = row.get("id")
        if note_id is None:
            continue
        try:
            nid = int(note_id)
        except (TypeError, ValueError):
            continue
        items.append(
            (
                nid,
                row.get("title"),
                row.get("content"),
                row.get("coverUrl") or row.get("cover_url"),
            )
        )
    return _dedupe_cap(items, limit=cap, snippet_max=snip)


def notes_for_llm(notes: list[NoteSource]) -> str:
    if not notes:
        return "（无相关站内笔记）"
    lines: list[str] = []
    for n in notes:
        title = n.title or "无标题"
        body = n.snippet or ""
        lines.append(f"[笔记#{n.id}] {title}\n{body}")
    return "\n\n".join(lines)
