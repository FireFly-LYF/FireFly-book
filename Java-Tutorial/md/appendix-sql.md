# 附录 · 建库 SQL 一键参考

把下面整段放到 MySQL 客户端执行即可（本地开发）。  
密码、库名与教程正文一致，可按需改。

```sql
-- ========== user ==========
CREATE DATABASE IF NOT EXISTS user DEFAULT CHARACTER SET utf8mb4;
USE user;

CREATE TABLE IF NOT EXISTS `user` (
  `id`         BIGINT PRIMARY KEY AUTO_INCREMENT,
  `username`   VARCHAR(64)  NOT NULL UNIQUE,
  `password`   VARCHAR(128) NOT NULL,
  `nickname`   VARCHAR(64)  NOT NULL,
  `avatar_url` VARCHAR(512) DEFAULT NULL,
  `bio`        VARCHAR(256) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `follow` (
  `id`           BIGINT PRIMARY KEY AUTO_INCREMENT,
  `follower_id`  BIGINT NOT NULL,
  `followee_id`  BIGINT NOT NULL,
  `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_follow (`follower_id`, `followee_id`),
  KEY idx_followee (`followee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Access/Refresh 双令牌：仅存 refresh 的 SHA-256，明文只发给客户端一次
CREATE TABLE IF NOT EXISTS `refresh_token` (
  `id`                  BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id`             BIGINT NOT NULL,
  `token_hash`          CHAR(64) NOT NULL,
  `device_fingerprint`  CHAR(64) NOT NULL COMMENT 'SHA-256 of client device id',
  `expires_at`          DATETIME NOT NULL,
  `revoked`             TINYINT NOT NULL DEFAULT 0,
  `created_at`          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_token_hash (`token_hash`),
  KEY idx_user (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== content ==========
CREATE DATABASE IF NOT EXISTS content DEFAULT CHARACTER SET utf8mb4;
USE content;

CREATE TABLE IF NOT EXISTS `note` (
  `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id`     BIGINT       NOT NULL,
  `title`       VARCHAR(128) NOT NULL,
  `content`     TEXT         NOT NULL,
  `cover_url`   VARCHAR(512) DEFAULT NULL,
  `status`      TINYINT      NOT NULL DEFAULT 1,
  `like_count`  INT          NOT NULL DEFAULT 0,
  `idem_key`    VARCHAR(64)  DEFAULT NULL COMMENT '客户端 Idempotency-Key',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_user (`user_id`),
  KEY idx_created (`created_at`),
  UNIQUE KEY uk_note_idem (`user_id`, `idem_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `note_media` (
  `id`         BIGINT PRIMARY KEY AUTO_INCREMENT,
  `note_id`    BIGINT       NOT NULL,
  `media_url`  VARCHAR(512) NOT NULL,
  `sort_no`    INT          NOT NULL DEFAULT 0,
  KEY idx_note (`note_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 笔记领域事件发件箱：与 note 同事务写入，后台投递到 Rabbit（M3 Outbox）
CREATE TABLE IF NOT EXISTS `outbox_event` (
  `id`             BIGINT PRIMARY KEY AUTO_INCREMENT,
  `aggregate_type` VARCHAR(32)  NOT NULL COMMENT 'note',
  `aggregate_id`   BIGINT       NOT NULL,
  `event_type`     VARCHAR(64)  NOT NULL COMMENT 'routing key: note.created/...',
  `payload`        JSON         NOT NULL,
  `status`         VARCHAR(16)  NOT NULL DEFAULT 'NEW' COMMENT 'NEW|SENT|DEAD',
  `attempts`       INT          NOT NULL DEFAULT 0,
  `next_retry_at`  DATETIME     DEFAULT NULL,
  `last_error`     VARCHAR(512) DEFAULT NULL,
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_outbox_poll (`status`, `next_retry_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== media ==========
CREATE DATABASE IF NOT EXISTS media DEFAULT CHARACTER SET utf8mb4;
USE media;

CREATE TABLE IF NOT EXISTS `media_asset` (
  `id`            BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id`       BIGINT       NOT NULL,
  `original_name` VARCHAR(256) NOT NULL,
  `content_type`  VARCHAR(128) DEFAULT NULL,
  `size_bytes`    BIGINT       NOT NULL,
  `url`           VARCHAR(512) NOT NULL,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== social ==========
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
  `parent_id`  BIGINT DEFAULT NULL,
  `content`    VARCHAR(512) NOT NULL,
  `idem_key`   VARCHAR(64) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_note (`note_id`),
  UNIQUE KEY uk_comment_idem (`user_id`, `idem_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== notify ==========
CREATE DATABASE IF NOT EXISTS notify DEFAULT CHARACTER SET utf8mb4;
USE notify;

CREATE TABLE IF NOT EXISTS `notification` (
  `id`           BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id`      BIGINT       NOT NULL,
  `from_user_id` BIGINT       NOT NULL,
  `type`         VARCHAR(32)  NOT NULL,
  `ref_id`       BIGINT       NOT NULL DEFAULT 0,
  `content`      VARCHAR(256) DEFAULT NULL,
  `is_read`      TINYINT      NOT NULL DEFAULT 0,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_notify_event (`user_id`, `type`, `from_user_id`, `ref_id`),
  KEY idx_user_time (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

对应章节：`05`～`10`。Feed / Search 可不建 MySQL 库。
