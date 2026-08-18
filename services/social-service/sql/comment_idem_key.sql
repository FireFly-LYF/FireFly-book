-- 已有 social 库补评论幂等键（列/索引已存在会报错，可忽略）
-- 执行：mysql -uroot -p social < comment_idem_key.sql

ALTER TABLE `comment`
  ADD COLUMN `idem_key` VARCHAR(64) DEFAULT NULL COMMENT '客户端 Idempotency-Key' AFTER `content`,
  ADD UNIQUE KEY `uk_comment_idem` (`user_id`, `idem_key`);
