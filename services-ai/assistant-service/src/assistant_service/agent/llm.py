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
    "硬性禁止：套话开场（如「关于…为您整理如下」「结合站内笔记与通用建议」）；"
    "报告式分节（如【站内笔记要点】【补充通用建议】）；复述用户原问题；"
    "用「某篇笔记」「某个网站」「网上说」等模糊来源说法。\n"
    "溯源规则：\n"
    "1. 材料已用固定标号 [笔记1]、[网络2] 等给出；依据某条材料时，必须在相应处标注同一标号。\n"
    "2. 正文口语化即可；不要编造材料中没有的细节。\n"
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
    """去掉模型偶发的套话开场与报告式分节标题。"""
    t = text.strip()
    t = re.sub(
        r'^关于[「『""].*?[」』""].*?(整理如下|总结如下|如下)[：:]?\s*',
        "",
        t,
        count=1,
    )
    t = re.sub(r"^\*{0,2}【[^】]+】\*{0,2}\s*\n?", "", t, flags=re.MULTILINE)
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


def fallback_answer(query: str, notes_block: str, web_block: str) -> str:
    has_notes = "无相关站内笔记" not in notes_block
    has_web = (
        "无网络搜索结果" not in web_block
        and "用户关闭了网络搜索" not in web_block
    )
    parts = []
    if has_notes:
        parts.append("站内有相关笔记：\n" + notes_block)
    if has_web:
        parts.append("\n网络参考：\n" + web_block)
    if not has_notes and not has_web:
        parts.append("暂未找到相关内容，建议换个关键词试试。")
    if not load_assistant_settings().llm_api_key:
        parts.append("\n（未配置 LLM_API_KEY，当前为检索摘要模式）")

    # 回退模式：材料即依据，补上可核对的 usedSources
    note_n = len(re.findall(r"\[笔记(\d+)\]", notes_block)) if has_notes else 0
    web_n = len(re.findall(r"\[网络(\d+)\]", web_block)) if has_web else 0
    used = [f"笔记{i}" for i in range(1, note_n + 1)] + [
        f"网络{i}" for i in range(1, web_n + 1)
    ]
    parts.append("\nusedSources: [" + ", ".join(used) + "]")
    return "\n".join(parts)
