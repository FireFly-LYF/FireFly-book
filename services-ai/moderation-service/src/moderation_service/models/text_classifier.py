"""P1 文本分类模型（预留）。"""

from moderation_service.pipeline import ModerationResult
from firefly_ai_common.events.note_created import NoteCreatedEvent


def classify(_event: NoteCreatedEvent) -> ModerationResult | None:
    """返回 None 表示未启用 ML，由 rules 决定。"""
    return None
