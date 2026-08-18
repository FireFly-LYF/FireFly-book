-- 已有 content 库补发帖幂等键（列/索引已存在会报错，可忽略）
-- 执行：mysql -uroot -p content < note_idem_key.sql

ALTER TABLE `note`
  ADD COLUMN `idem_key` VARCHAR(64) DEFAULT NULL COMMENT '客户端 Idempotency-Key' AFTER `like_count`,
  ADD UNIQUE KEY `uk_note_idem` (`user_id`, `idem_key`);
