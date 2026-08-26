"""审核流水线：文本 + 图片 → 回写 content → 发 note.moderated。"""

from __future__ import annotations

import asyncio
import logging
from dataclasses import dataclass

from firefly_ai_common.clients.content_client import ContentClient, ContentClientError
from firefly_ai_common.clients.media_client import MediaClient, MediaClientError
from firefly_ai_common.events.note_created import NoteCreatedEvent
from firefly_ai_common.events.note_moderated import NoteModeratedEvent
from firefly_ai_common.mq.consumer_base import RetryableMessageError
from firefly_ai_common.mq.publisher import EventPublisher

from moderation_service.models import image_classifier
from moderation_service.rules import check_heuristics, find_keyword
from moderation_service.rules import image_heuristics
from moderation_service.settings import IMAGE_MODERATION_ENABLED, MAX_IMAGES_PER_NOTE

log = logging.getLogger(__name__)

STATUS_APPROVED = 1
STATUS_REJECTED = 2


@dataclass(frozen=True)
class ModerationResult:
    passed: bool
    reason: str | None = None


def evaluate_text(event: NoteCreatedEvent) -> ModerationResult:
    title = event.title or ""
    content = event.content or ""

    heuristic_reason = check_heuristics(title, content)
    if heuristic_reason:
        return ModerationResult(passed=False, reason=f"text:{heuristic_reason}")

    keyword = find_keyword(f"{title}\n{content}")
    if keyword:
        return ModerationResult(passed=False, reason=f"text:keyword:{keyword}")

    return ModerationResult(passed=True)


def evaluate_images(event: NoteCreatedEvent, media: MediaClient) -> ModerationResult:
    paths = event.image_paths()[:MAX_IMAGES_PER_NOTE]
    if not paths:
        return ModerationResult(passed=True)

    for path in paths:
        path_reason = image_heuristics.check_path(path)
        if path_reason:
            return ModerationResult(passed=False, reason=path_reason)

        try:
            data, content_type = media.fetch_bytes(path)
        except MediaClientError as exc:
            msg = str(exc)
            if "HTTP 5" in msg or "HTTP 502" in msg or "HTTP 503" in msg:
                raise RetryableMessageError(f"拉图失败可重试: {path}") from exc
            if "HTTP 404" in msg:
                return ModerationResult(passed=False, reason="image:not_found")
            log.warning("拉图失败 note_id=%s path=%s: %s", event.id, path, exc)
            return ModerationResult(passed=False, reason="image:fetch_failed")

        bytes_reason = image_heuristics.check_bytes(data)
        if bytes_reason:
            return ModerationResult(passed=False, reason=bytes_reason)

        try:
            passed, score, reason = image_classifier.classify_bytes(data, content_type)
        except RuntimeError as exc:
            raise RetryableMessageError(f"NSFW 检测可重试: {path}") from exc
        log.debug("NSFW-Detector path=%s score=%.4f passed=%s", path, score, passed)
        if not passed:
            return ModerationResult(passed=False, reason=reason)

    return ModerationResult(passed=True)


def _merge(*results: ModerationResult) -> ModerationResult:
    for result in results:
        if not result.passed:
            return result
    return ModerationResult(passed=True)


async def evaluate(event: NoteCreatedEvent, *, media: MediaClient | None = None) -> ModerationResult:
    text_result = evaluate_text(event)
    if not text_result.passed:
        return text_result

    if not IMAGE_MODERATION_ENABLED or media is None:
        return ModerationResult(passed=True)

    if not event.image_paths():
        return ModerationResult(passed=True)

    image_result = await asyncio.to_thread(evaluate_images, event, media)
    return _merge(text_result, image_result)


async def process_note_created(
    event: NoteCreatedEvent,
    *,
    publisher: EventPublisher,
    content: ContentClient,
    media: MediaClient | None = None,
) -> ModerationResult:
    result = await evaluate(event, media=media)
    status = STATUS_APPROVED if result.passed else STATUS_REJECTED

    if not result.passed:
        try:
            content.update_moderation_status(
                event.id,
                status=status,
                reason=result.reason,
            )
            log.info(
                "审核拒绝 note_id=%s reason=%s",
                event.id,
                result.reason,
            )
        except ContentClientError as exc:
            log.warning(
                "回写 content 失败 note_id=%s: %s",
                event.id,
                exc,
            )
    else:
        log.info("审核通过 note_id=%s", event.id)

    await publisher.publish_note_moderated(
        NoteModeratedEvent(
            id=event.id,
            user_id=event.user_id,
            passed=result.passed,
            reason=result.reason,
        )
    )
    return result
