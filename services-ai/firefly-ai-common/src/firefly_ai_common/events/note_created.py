"""note.created 事件模型（字段对齐 Java NoteIndexEvent）。"""

from pydantic import BaseModel


class NoteCreatedEvent(BaseModel):
    id: int | None = None
    user_id: int | None = None
    title: str | None = None
    content: str | None = None
    cover_url: str | None = None
