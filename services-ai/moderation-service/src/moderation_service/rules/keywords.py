"""P0 敏感词规则。"""

from __future__ import annotations

# 演示词表；生产环境可改为文件加载或远程词库
DEFAULT_KEYWORDS: frozenset[str] = frozenset(
    {
        "违禁",
        "赌博",
        "色情",
        "诈骗",
        "毒品",
        "枪支",
        "代开发票",
        "刷单",
    }
)


def find_keyword(text: str, keywords: frozenset[str] | None = None) -> str | None:
    """命中则返回敏感词，否则 None。"""
    if not text:
        return None
    normalized = text.casefold()
    for word in keywords or DEFAULT_KEYWORDS:
        if word.casefold() in normalized:
            return word
    return None
