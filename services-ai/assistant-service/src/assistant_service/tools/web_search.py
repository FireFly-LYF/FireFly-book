"""网络搜索工具（DuckDuckGo，带超时；国内常失败/较慢）。"""

from __future__ import annotations

import logging
from concurrent.futures import ThreadPoolExecutor
from concurrent.futures import wait

from assistant_service.schemas.search import WebSource
from assistant_service.settings import AssistantSettings, load_assistant_settings
from assistant_service.tools.web_safety import filter_web_sources

log = logging.getLogger(__name__)

_executor = ThreadPoolExecutor(max_workers=2, thread_name_prefix="web-search")


def _search_web_inner(query: str, max_results: int) -> list[WebSource]:
    from duckduckgo_search import DDGS

    with DDGS() as ddgs:
        rows = list(ddgs.text(query, max_results=max_results))
    out: list[WebSource] = []
    for row in rows:
        if not isinstance(row, dict):
            continue
        title = str(row.get("title") or "").strip()
        url = str(row.get("href") or row.get("url") or "").strip()
        if not title or not url:
            continue
        snippet = row.get("body") or row.get("snippet")
        out.append(
            WebSource(
                title=title,
                url=url,
                snippet=str(snippet).strip() if snippet else None,
            )
        )
    return out


def search_web(
    query: str,
    *,
    settings: AssistantSettings | None = None,
) -> list[WebSource]:
    cfg = settings or load_assistant_settings()
    if not cfg.web_search_enabled:
        return []

    max_results = max(1, min(cfg.web_search_max_results, 5))
    # 多拉一些，过滤后仍尽量凑满 max_results
    fetch_n = min(max_results * 3, 12)
    timeout = max(1.0, min(cfg.web_search_timeout_sec, 15.0))
    fut = _executor.submit(_search_web_inner, query, fetch_n)
    done, _ = wait({fut}, timeout=timeout)
    if fut not in done:
        fut.cancel()
        log.warning("网络搜索超时 %.1fs query=%s", timeout, query[:40])
        return []
    try:
        raw = fut.result()
    except Exception as exc:
        log.warning("网络搜索失败: %s", exc)
        return []

    safe = filter_web_sources(
        raw,
        extra_keywords=cfg.web_blocked_keyword_set,
        extra_domains=cfg.web_blocked_domain_set,
    )
    return safe[:max_results]


def web_for_llm(sources: list[WebSource]) -> str:
    if not sources:
        return "（无网络搜索结果）"
    lines: list[str] = []
    for i, w in enumerate(sources, 1):
        snippet = w.snippet or ""
        lines.append(f"[网络{i}] {w.title}\n链接：{w.url}\n{snippet}")
    return "\n\n".join(lines)
