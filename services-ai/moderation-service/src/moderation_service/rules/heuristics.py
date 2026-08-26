"""启发式规则：长度、空标题、灌水模式。"""

from __future__ import annotations

import re

MAX_TITLE_LEN = 128
MAX_CONTENT_LEN = 10_000
MAX_REPEAT_CHAR = 20

_REPEAT_PATTERN = re.compile(r"(.)\1{" + str(MAX_REPEAT_CHAR - 1) + r",}")


def check_heuristics(title: str | None, content: str | None) -> str | None:
    """命中则返回 reason，否则 None。"""
    title = (title or "").strip()
    content = content or ""

    if not title:
        return "empty_title"
    if len(title) > MAX_TITLE_LEN:
        return "title_too_long"
    if len(content) > MAX_CONTENT_LEN:
        return "content_too_long"

    combined = f"{title}\n{content}"
    if _REPEAT_PATTERN.search(combined):
        return "spam_pattern"

    return None
