-- notify-service 建库建表（在 MySQL 中执行）
CREATE DATABASE IF NOT EXISTS notify DEFAULT CHARACTER SET utf8mb4;
USE notify;

CREATE TABLE IF NOT EXISTS `notification` (
  `id`           BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id`      BIGINT       NOT NULL COMMENT 'receiver',
  `from_user_id` BIGINT       NOT NULL COMMENT 'actor',
  `type`         VARCHAR(32)  NOT NULL COMMENT 'LIKE/COMMENT/FOLLOW',
  `ref_id`       BIGINT       DEFAULT NULL COMMENT 'note or comment id',
  `content`      VARCHAR(256) DEFAULT NULL,
  `is_read`      TINYINT      NOT NULL DEFAULT 0,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user_time (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
