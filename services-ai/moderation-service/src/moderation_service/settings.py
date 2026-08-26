"""moderation-service 本地配置（环境变量）。"""

import os

NSFW_DETECTOR_URL: str = os.getenv("NSFW_DETECTOR_URL", "http://127.0.0.1:3333")
NSFW_THRESHOLD: float = float(os.getenv("NSFW_THRESHOLD", "0.5"))
MAX_IMAGES_PER_NOTE: int = int(os.getenv("MAX_IMAGES_PER_NOTE", "9"))
IMAGE_MODERATION_ENABLED: bool = os.getenv("IMAGE_MODERATION_ENABLED", "true").lower() in (
    "1",
    "true",
    "yes",
)
