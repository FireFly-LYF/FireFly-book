"""Agent 共享上下文（workflow 间复用）。"""

from __future__ import annotations

from dataclasses import dataclass, field

from assistant_service.settings import AssistantSettings, load_assistant_settings


@dataclass
class AgentContext:
    """单次请求级上下文；后续可扩展 session、trace_id 等。"""

    user_id: str | None = None
    settings: AssistantSettings | None = field(default=None)

    @property
    def cfg(self) -> AssistantSettings:
        return self.settings or load_assistant_settings()
