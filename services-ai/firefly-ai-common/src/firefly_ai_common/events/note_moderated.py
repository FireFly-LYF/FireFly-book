"""note.moderated 事件模型（骨架）。"""

from pydantic import BaseModel


class NoteModeratedEvent(BaseModel):
    id: int
    user_id: int | None = None
    passed: bool
    reason: str | None = None
