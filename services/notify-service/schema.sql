-- notify-service 建库建表（在 MySQL 中执行）
CREATE DATABASE IF NOT EXISTS notify DEFAULT CHARACTER SET utf8mb4;
USE notify;

CREATE TABLE IF NOT EXISTS `notification` (
  `id`           BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id`      BIGINT       NOT NULL COMMENT 'receiver',
  `from_user_id` BIGINT       NOT NULL COMMENT 'actor',
  `type`         VARCHAR(32)  NOT NULL COMMENT 'LIKE/COMMENT/FOLLOW',
  `ref_id`       BIGINT       NOT NULL DEFAULT 0 COMMENT 'note/comment id；FOLLOW 用 0',
  `content`      VARCHAR(256) DEFAULT NULL,
  `is_read`      TINYINT      NOT NULL DEFAULT 0,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_notify_event (`user_id`, `type`, `from_user_id`, `ref_id`),
  KEY idx_user_time (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 已有库补幂等键（无重复行时执行；键已存在会报错可忽略）
-- UPDATE notification SET ref_id = 0 WHERE ref_id IS NULL;
-- ALTER TABLE notification MODIFY `ref_id` BIGINT NOT NULL DEFAULT 0 COMMENT 'note/comment id；FOLLOW 用 0';
-- ALTER TABLE notification ADD UNIQUE KEY uk_notify_event (`user_id`, `type`, `from_user_id`, `ref_id`);
