"""note.tagged 事件模型。"""

from pydantic import BaseModel, ConfigDict, Field


class NoteTaggedEvent(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: int
    user_id: int | None = Field(default=None, alias="userId")
    tags: list[str] = Field(default_factory=list)

    def is_valid(self) -> bool:
        return self.id is not None
