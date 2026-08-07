-- media-service 建库建表（在 MySQL 中执行）
CREATE DATABASE IF NOT EXISTS media DEFAULT CHARACTER SET utf8mb4;
USE media;

CREATE TABLE IF NOT EXISTS `media_asset` (
  `id`           BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id`      BIGINT       NOT NULL,
  `original_name` VARCHAR(256) NOT NULL,
  `content_type` VARCHAR(128) DEFAULT NULL,
  `size_bytes`   BIGINT       NOT NULL,
  `url`          VARCHAR(512) NOT NULL,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
