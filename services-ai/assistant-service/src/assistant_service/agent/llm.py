"""Agent 核心 LLM 调用（OpenAI 兼容）。"""

from __future__ import annotations

import logging
import re
import time
from collections.abc import Iterator

from openai import OpenAI

from assistant_service.settings import AssistantSettings, load_assistant_settings

log = logging.getLogger(__name__)

SYSTEM_PROMPT = (
    "你是 FireFly 社区的搜索助手。用自然口语中文直接回答问题，像资深用户在分享经验。\n"
    "硬性禁止：套话开场（如「关于…为您整理如下」「结合站内笔记与通用建议」「站内有相关笔记」）；"
    "报告式分节（如【站内笔记要点】【补充通用建议】）；复述用户原问题；"
    "原样粘贴参考材料；输出笔记/网络的内部编号、id、URL 原文；"
    "用「某篇笔记」「某个网站」「网上说」等模糊来源说法。\n"
    "溯源规则：\n"
    "1. 材料已用固定标号 [笔记1]、[网络2] 等给出；依据某条材料时，在相应句末标注同一标号。\n"
    "2. 把材料融合成口语建议，只保留用户关心的要点；不要罗列「《标题》+ 全文摘要」目录。\n"
    "3. 正文结束后单独一行，格式必须严格为：\n"
    "usedSources: [笔记1, 网络2]\n"
    "若完全未使用材料（纯常识补充），写：usedSources: []\n"
    "4. usedSources 里只允许出现上文给出的标号，不要写标题或 URL。\n"
    "5. 若参考材料标明「无相关站内笔记」且「无网络搜索结果」，可做常识补充，"
    "并输出 usedSources: []。\n"
    "篇幅：2～4 个短段落或简短列表即可。"
)

_client: OpenAI | None = None
_client_key: tuple[str, str, float] | None = None


def _get_client(cfg: AssistantSettings) -> OpenAI:
    global _client, _client_key
    sig = (cfg.llm_base_url, cfg.llm_api_key, cfg.llm_timeout_sec)
    if _client is None or _client_key != sig:
        _client = OpenAI(
            base_url=cfg.llm_base_url,
            api_key=cfg.llm_api_key,
            timeout=cfg.llm_timeout_sec,
        )
        _client_key = sig
    return _client


def clean_answer(text: str) -> str:
    """去掉模型偶发的套话开场、报告式分节与工程师痕迹。"""
    t = text.strip()
    t = re.sub(
        r'^关于[「『""].*?[」』""].*?(整理如下|总结如下|如下)[：:]?\s*',
        "",
        t,
        count=1,
    )
    t = re.sub(r"^\*{0,2}【[^】]+】\*{0,2}\s*\n?", "", t, flags=re.MULTILINE)
    t = re.sub(r"（站内笔记\s*id\s*=\s*\d+）", "", t)
    t = re.sub(r"^\s*站内有相关笔记\s*[:：]?\s*$", "", t, flags=re.MULTILINE)
    return t.strip()


def generate_answer(
    query: str,
    notes_block: str,
    web_block: str,
    *,
    settings: AssistantSettings | None = None,
) -> str:
    cfg = settings or load_assistant_settings()
    if not cfg.llm_api_key:
        return fallback_answer(query, notes_block, web_block)

    parts = list(
        iter_answer_chunks(query, notes_block, web_block, settings=cfg)
    )
    if parts:
        return clean_answer("".join(parts))
    return fallback_answer(query, notes_block, web_block)


def iter_answer_chunks(
    query: str,
    notes_block: str,
    web_block: str,
    *,
    settings: AssistantSettings | None = None,
) -> Iterator[str]:
    """流式 yield LLM 文本片段；无 API Key 时一次性 yield 回退全文。"""
    cfg = settings or load_assistant_settings()
    if not cfg.llm_api_key:
        yield fallback_answer(query, notes_block, web_block)
        return

    user_content = (
        f"用户问题：{query}\n\n"
        f"参考笔记：\n{notes_block}\n\n"
        f"网络摘要：\n{web_block}\n\n"
        "直接给出回答正文（需按规则标注 [笔记N]/[网络M]），"
        "最后一行必须是 usedSources: [...]；"
        "不要开场白、不要分节标题、不要复述问题。"
    )

    t0 = time.perf_counter()
    chunks = 0
    try:
        client = _get_client(cfg)
        stream = client.chat.completions.create(
            model=cfg.llm_model,
            messages=[
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": user_content},
            ],
            temperature=0.55,
            max_tokens=cfg.llm_max_tokens,
            stream=True,
            extra_body={"enable_thinking": cfg.llm_enable_thinking},
        )
        for chunk in stream:
            if not chunk.choices:
                continue
            delta = chunk.choices[0].delta.content
            if delta:
                chunks += 1
                yield delta
        log.info(
            "LLM stream 完成 model=%s %.2fs chunks=%d",
            cfg.llm_model,
            time.perf_counter() - t0,
            chunks,
        )
    except Exception as exc:
        log.warning("LLM 流式调用失败，回退摘要模式: %s (%.2fs)", exc, time.perf_counter() - t0)
        yield fallback_answer(query, notes_block, web_block)


def _parse_labeled_entries(block: str, kind: str) -> list[tuple[int, str, str]]:
    """从 notes_for_llm / web_for_llm 文本解析 (标号, 标题, 正文)。"""
    marker = re.compile(rf"\[{re.escape(kind)}(\d+)\]\s*")
    matches = list(marker.finditer(block or ""))
    out: list[tuple[int, str, str]] = []
    for i, m in enumerate(matches):
        try:
            idx = int(m.group(1))
        except ValueError:
            continue
        end = matches[i + 1].start() if i + 1 < len(matches) else len(block)
        chunk = (block[m.end() : end] or "").strip()
        first, _, rest = chunk.partition("\n")
        title = first.strip().strip("《》").strip()
        # 兼容旧材料格式：标题前缀「（站内笔记 id=…）」
        title = re.sub(r"^（站内笔记\s*id\s*=\s*\d+）\s*", "", title).strip()
        if kind == "网络":
            # 去掉「链接：…」行，避免工程师味外泄到用户正文
            body_lines = [
                ln
                for ln in (rest or "").splitlines()
                if not re.match(r"^\s*链接\s*[:：]", ln)
            ]
            body = "\n".join(body_lines).strip()
        else:
            body = (rest or "").strip()
        out.append((idx, title or f"{kind}{idx}", body))
    return out


def _fallback_snip(text: str, max_len: int = 72) -> str:
    t = " ".join((text or "").split())
    if len(t) <= max_len:
        return t
    return f"{t[:max_len]}…"


def _dedupe_entries(
    entries: list[tuple[int, str, str]],
) -> list[tuple[int, str, str]]:
    """按标题+摘要去重，避免同款造数笔记刷屏。"""
    seen: set[tuple[str, str]] = set()
    out: list[tuple[int, str, str]] = []
    for idx, title, body in entries:
        key = (title.strip().casefold(), _fallback_snip(body, 28).casefold())
        if key in seen:
            continue
        seen.add(key)
        out.append((idx, title, body))
    return out


def fallback_answer(query: str, notes_block: str, web_block: str) -> str:
    """无 LLM 时的人话短答：要点成段，[笔记N]/[网络M] 可点追溯。"""
    has_notes = "无相关站内笔记" not in notes_block
    has_web = (
        "无网络搜索结果" not in web_block
        and "用户关闭了网络搜索" not in web_block
    )
    parts: list[str] = []
    used: list[str] = []

    if has_notes:
        for idx, title, body in _dedupe_entries(
            _parse_labeled_entries(notes_block, "笔记")
        ):
            tip = _fallback_snip(body)
            if tip:
                parts.append(f"{tip}[笔记{idx}]")
            else:
                parts.append(f"《{title}》里有相关分享，可点开看看。[笔记{idx}]")
            used.append(f"笔记{idx}")

    if has_web:
        for idx, title, body in _dedupe_entries(
            _parse_labeled_entries(web_block, "网络")
        ):
            tip = _fallback_snip(body) or title
            parts.append(f"{tip}[网络{idx}]")
            used.append(f"网络{idx}")

    if not parts:
        parts.append("暂未找到相关内容，建议换个关键词试试。")

    body = "\n\n".join(parts)
    return f"{body}\nusedSources: [{', '.join(used)}]"
