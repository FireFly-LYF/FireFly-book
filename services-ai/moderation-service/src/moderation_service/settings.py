"""moderation-service 本地配置（环境变量）。"""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class ModerationSettings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=(".env", "../.env"),
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    nsfw_detector_url: str = "http://127.0.0.1:3333"
    nsfw_threshold: float = 0.5
    max_images_per_note: int = 9
    image_moderation_enabled: bool = True


@lru_cache
def load_moderation_settings() -> ModerationSettings:
    return ModerationSettings()


# 兼容现有 `from moderation_service.settings import NSFW_*` 用法
_s = load_moderation_settings()
NSFW_DETECTOR_URL: str = _s.nsfw_detector_url
NSFW_THRESHOLD: float = _s.nsfw_threshold
MAX_IMAGES_PER_NOTE: int = _s.max_images_per_note
IMAGE_MODERATION_ENABLED: bool = _s.image_moderation_enabled
