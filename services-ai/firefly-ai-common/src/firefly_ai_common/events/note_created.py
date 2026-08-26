"""note.created 事件模型（字段对齐 Java NoteIndexEvent）。"""

from pydantic import BaseModel, ConfigDict, Field


class NoteCreatedEvent(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: int | None = None
    user_id: int | None = Field(default=None, alias="userId")
    title: str | None = None
    content: str | None = None
    cover_url: str | None = Field(default=None, alias="coverUrl")

    def is_valid(self) -> bool:
        return self.id is not None
