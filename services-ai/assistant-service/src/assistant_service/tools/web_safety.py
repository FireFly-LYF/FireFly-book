"""Web 搜索结果安全过滤：敏感词/规则 + 域名黑名单。"""

from __future__ import annotations

import logging
from urllib.parse import urlparse

from assistant_service.schemas.search import WebSource

log = logging.getLogger(__name__)

# 演示/基线词表；可用 WEB_BLOCKED_KEYWORDS 追加。偏色情、暴力与高风险违法内容。
DEFAULT_BLOCKED_KEYWORDS: frozenset[str] = frozenset(
    {
        "色情",
        "淫秽",
        "黄片",
        "裸体",
        "裸聊",
        "约炮",
        "嫖娼",
        "强奸",
        "迷奸",
        "恋童",
        "儿童色情",
        "血腥屠杀",
        "斩首视频",
        "恐怖袭击教程",
        "制爆",
        "炸弹制作",
        "porn",
        "pornhub",
        "xvideos",
        "xnxx",
        "onlyfans",
        "hentai",
        "nsfw",
        "gore",
        "beheading",
        "child porn",
        "childporn",
    }
)

# 高风险来源域名（含子域）；可用 WEB_BLOCKED_DOMAINS 追加
DEFAULT_BLOCKED_DOMAINS: frozenset[str] = frozenset(
    {
        "pornhub.com",
        "xvideos.com",
        "xnxx.com",
        "xhamster.com",
        "redtube.com",
        "youporn.com",
        "spankbang.com",
        "onlyfans.com",
        "chaturbate.com",
        "stripchat.com",
        "bongacams.com",
        "livejasmin.com",
        "adultfriendfinder.com",
        "sex.com",
        "porn.com",
        "hqporner.com",
        "missav.com",
        "javdb.com",
        "javlibrary.com",
        "avgle.com",
        "nhentai.net",
        "hanime.tv",
        "rule34.xxx",
        "gelbooru.com",
        "liveleak.com",
        "bestgore.com",
        "theync.com",
        "documentingreality.com",
    }
)


def _host_of(url: str) -> str | None:
    try:
        parsed = urlparse(url.strip())
    except Exception:
        return None
    if parsed.scheme not in ("http", "https"):
        return None
    host = (parsed.hostname or "").strip().lower().rstrip(".")
    return host or None


def _domain_blocked(host: str, blocked_domains: frozenset[str]) -> str | None:
    for domain in blocked_domains:
        d = domain.strip().lower().rstrip(".")
        if not d:
            continue
        if host == d or host.endswith("." + d):
            return d
    return None


def _find_keyword(text: str, keywords: frozenset[str]) -> str | None:
    if not text:
        return None
    normalized = text.casefold()
    for word in keywords:
        w = word.strip()
        if w and w.casefold() in normalized:
            return w
    return None


def _rule_reject(source: WebSource) -> str | None:
    """轻量规则：缺标题/URL、非 http(s)、异常超长灌水。"""
    title = (source.title or "").strip()
    url = (source.url or "").strip()
    snippet = (source.snippet or "").strip()

    if not title or not url:
        return "empty_title_or_url"
    if _host_of(url) is None:
        return "invalid_url"
    if len(title) > 300 or len(snippet) > 4000:
        return "content_too_long"
    # 极端重复字符（灌水/垃圾摘要）
    for ch in ("!", "?", "。", "～", "~", "*"):
        if ch * 12 in title or ch * 12 in snippet:
            return "spam_pattern"
    return None


def filter_web_sources(
    sources: list[WebSource],
    *,
    extra_keywords: frozenset[str] | None = None,
    extra_domains: frozenset[str] | None = None,
) -> list[WebSource]:
    """过滤不合规 Web 结果；保留顺序，仅丢弃命中项。"""
    keywords = DEFAULT_BLOCKED_KEYWORDS | (extra_keywords or frozenset())
    domains = DEFAULT_BLOCKED_DOMAINS | (extra_domains or frozenset())
    kept: list[WebSource] = []

    for src in sources:
        reason = _rule_reject(src)
        if reason:
            log.info("web 过滤丢弃 rule=%s url=%s", reason, (src.url or "")[:120])
            continue

        host = _host_of(src.url) or ""
        blocked = _domain_blocked(host, domains)
        if blocked:
            log.info("web 过滤丢弃 domain=%s url=%s", blocked, src.url[:120])
            continue

        text = f"{src.title or ''}\n{src.snippet or ''}\n{src.url or ''}"
        hit = _find_keyword(text, keywords)
        if hit:
            log.info("web 过滤丢弃 keyword=%s url=%s", hit, src.url[:120])
            continue

        kept.append(src)

    if len(kept) != len(sources):
        log.info("web 过滤完成 kept=%d dropped=%d", len(kept), len(sources) - len(kept))
    return kept
