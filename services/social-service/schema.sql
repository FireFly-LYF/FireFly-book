-- social-service 建库建表（在 MySQL 中执行）
CREATE DATABASE IF NOT EXISTS social DEFAULT CHARACTER SET utf8mb4;
USE social;

CREATE TABLE IF NOT EXISTS `note_like` (
  `id`         BIGINT PRIMARY KEY AUTO_INCREMENT,
  `note_id`    BIGINT NOT NULL,
  `user_id`    BIGINT NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_like (`note_id`, `user_id`),
  KEY idx_user (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `note_collect` (
  `id`         BIGINT PRIMARY KEY AUTO_INCREMENT,
  `note_id`    BIGINT NOT NULL,
  `user_id`    BIGINT NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_collect (`note_id`, `user_id`),
  KEY idx_user (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `comment` (
  `id`         BIGINT PRIMARY KEY AUTO_INCREMENT,
  `note_id`    BIGINT NOT NULL,
  `user_id`    BIGINT NOT NULL,
  `parent_id`  BIGINT DEFAULT NULL COMMENT '回复哪条评论，可空',
  `content`     VARCHAR(512) NOT NULL,
  `idem_key`    VARCHAR(64)  DEFAULT NULL COMMENT '客户端 Idempotency-Key',
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_note (`note_id`),
  UNIQUE KEY uk_comment_idem (`user_id`, `idem_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 已有库：ALTER TABLE comment ADD COLUMN idem_key VARCHAR(64) DEFAULT NULL, ADD UNIQUE KEY uk_comment_idem (user_id, idem_key);

