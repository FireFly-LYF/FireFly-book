"""图片 P0 规则：path 白名单、大小、魔数。"""

from __future__ import annotations

import re

MAX_IMAGE_BYTES = 10 * 1024 * 1024

_SAFE_PATH = re.compile(
    r"^/files/[0-9a-fA-F-]{36}\.(jpg|jpeg|png|gif|webp)$",
    re.IGNORECASE,
)


def check_path(path: str) -> str | None:
    if not path or not _SAFE_PATH.match(path.strip()):
        return "image:invalid_path"
    return None


def check_bytes(data: bytes) -> str | None:
    if not data:
        return "image:empty_file"
    if len(data) > MAX_IMAGE_BYTES:
        return "image:too_large"
    if not _detect_image_magic(data):
        return "image:invalid_format"
    return None


def _detect_image_magic(head: bytes) -> bool:
    if len(head) < 12:
        return False
    if head[0:3] == b"\xff\xd8\xff":
        return True
    if head[0:8] == b"\x89PNG\r\n\x1a\n":
        return True
    if head[0:6] in (b"GIF87a", b"GIF89a"):
        return True
    if head[0:4] == b"RIFF" and head[8:12] == b"WEBP":
        return True
    return False
