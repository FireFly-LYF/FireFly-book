-- content: note idempotency
ALTER TABLE content.note
  ADD COLUMN idem_key VARCHAR(64) DEFAULT NULL COMMENT '客户端 Idempotency-Key' AFTER like_count;
ALTER TABLE content.note
  ADD UNIQUE KEY uk_note_idem (user_id, idem_key);

-- social: comment idempotency
ALTER TABLE social.comment
  ADD COLUMN idem_key VARCHAR(64) DEFAULT NULL COMMENT '客户端 Idempotency-Key' AFTER content;
ALTER TABLE social.comment
  ADD UNIQUE KEY uk_comment_idem (user_id, idem_key);

-- notify: event unique (FOLLOW ref_id NULL -> 0)
UPDATE notify.notification SET ref_id = 0 WHERE ref_id IS NULL;
ALTER TABLE notify.notification
  MODIFY ref_id BIGINT NOT NULL DEFAULT 0 COMMENT 'note/comment id；FOLLOW 用 0';
ALTER TABLE notify.notification
  ADD UNIQUE KEY uk_notify_event (user_id, type, from_user_id, ref_id);
