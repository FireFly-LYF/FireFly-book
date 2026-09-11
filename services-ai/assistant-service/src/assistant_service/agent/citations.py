"""回答溯源：解析 usedSources / 文内 [笔记N][网络M]，并与候选材料核对。"""

from __future__ import annotations

import re
from dataclasses import dataclass

from assistant_service.schemas.search import NoteSource, WebSource

UNGROUNDED_PREFIX = "未检索到依据，以下是AI补充结果"

_USED_LINE = re.compile(
    r"(?im)^\s*usedSources\s*:\s*\[([^\]]*)\]\s*$"
)
_CITATION = re.compile(r"\[(笔记|网络)(\d+)\]")


@dataclass(frozen=True)
class CitationResult:
    answer: str
    note_sources: list[NoteSource]
    web_sources: list[WebSource]
    ungrounded: bool


def _parse_used_indices(blob: str) -> tuple[set[int], set[int]]:
    notes: set[int] = set()
    webs: set[int] = set()
    for kind, num in _CITATION.findall(blob):
        try:
            idx = int(num)
        except ValueError:
            continue
        if idx < 1:
            continue
        if kind == "笔记":
            notes.add(idx)
        else:
            webs.add(idx)
    return notes, webs


def _strip_used_sources_line(text: str) -> tuple[str, str | None]:
    """去掉文末/文中 usedSources 行，返回 (正文, 该行括号内原文)。"""
    match = None
    for m in _USED_LINE.finditer(text):
        match = m
    if not match:
        return text.strip(), None
    inner = match.group(1)
    cleaned = (text[: match.start()] + text[match.end() :]).strip()
    return cleaned, inner


def resolve_citations(
    raw_answer: str,
    note_candidates: list[NoteSource],
    web_candidates: list[WebSource],
) -> CitationResult:
    """只认 [笔记N]/[网络M]；模糊说法忽略。返回核对后的实引子集。"""
    body, used_inner = _strip_used_sources_line(raw_answer or "")
    note_idxs: set[int] = set()
    web_idxs: set[int] = set()

    if used_inner is not None:
        n, w = _parse_used_indices(used_inner)
        note_idxs |= n
        web_idxs |= w

    # 正文内标记一并纳入，再与 usedSources 取并集后校验
    n2, w2 = _parse_used_indices(body)
    note_idxs |= n2
    web_idxs |= w2

    cited_notes = [
        note_candidates[i - 1]
        for i in sorted(note_idxs)
        if 1 <= i <= len(note_candidates)
    ]
    cited_webs = [
        web_candidates[i - 1]
        for i in sorted(web_idxs)
        if 1 <= i <= len(web_candidates)
    ]

    has_materials = bool(note_candidates or web_candidates)
    ungrounded = not has_materials

    answer = body.strip()
    if ungrounded:
        if answer and not answer.startswith(UNGROUNDED_PREFIX):
            answer = f"{UNGROUNDED_PREFIX}\n\n{answer}"
        elif not answer:
            answer = UNGROUNDED_PREFIX
        cited_notes = []
        cited_webs = []

    return CitationResult(
        answer=answer,
        note_sources=cited_notes,
        web_sources=cited_webs,
        ungrounded=ungrounded,
    )


def auto_cite_all(
    answer: str,
    note_candidates: list[NoteSource],
    web_candidates: list[WebSource],
) -> CitationResult:
    """无 LLM / 回退摘要：材料即依据，全部视为实引。"""
    has_materials = bool(note_candidates or web_candidates)
    if not has_materials:
        text = (answer or "").strip()
        if text and not text.startswith(UNGROUNDED_PREFIX):
            text = f"{UNGROUNDED_PREFIX}\n\n{text}"
        elif not text:
            text = UNGROUNDED_PREFIX
        return CitationResult(text, [], [], True)
    return CitationResult(
        answer=(answer or "").strip(),
        note_sources=list(note_candidates),
        web_sources=list(web_candidates),
        ungrounded=False,
    )
