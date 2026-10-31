CREATE DATABASE IF NOT EXISTS im DEFAULT CHARACTER SET utf8mb4;
USE im;

CREATE TABLE IF NOT EXISTS `conversation` (
  `id`               BIGINT PRIMARY KEY AUTO_INCREMENT,
  `type`             VARCHAR(16)  NOT NULL DEFAULT 'DM',
  `dm_key`           VARCHAR(64)  DEFAULT NULL COMMENT 'minUid:maxUid for DM',
  `last_message_id`  BIGINT       DEFAULT NULL,
  `last_preview`     VARCHAR(256) DEFAULT NULL,
  `last_sender_id`   BIGINT       DEFAULT NULL,
  `last_message_at`  DATETIME     DEFAULT NULL,
  `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_dm_key (`dm_key`),
  KEY idx_last_msg (`last_message_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `conversation_member` (
  `conversation_id`      BIGINT NOT NULL,
  `user_id`              BIGINT NOT NULL,
  `unread_count`         INT    NOT NULL DEFAULT 0,
  `last_read_message_id` BIGINT NOT NULL DEFAULT 0,
  `joined_at`            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`conversation_id`, `user_id`),
  KEY idx_member_user (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `im_message` (
  `id`              BIGINT PRIMARY KEY AUTO_INCREMENT,
  `conversation_id` BIGINT       NOT NULL,
  `sender_id`       BIGINT       NOT NULL,
  `content`         VARCHAR(1000) NOT NULL,
  `idem_key`        VARCHAR(64)  DEFAULT NULL,
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_conv_idem (`conversation_id`, `idem_key`),
  KEY idx_conv_msg (`conversation_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `outbox_event` (
  `id`             BIGINT PRIMARY KEY AUTO_INCREMENT,
  `aggregate_type` VARCHAR(32)  NOT NULL,
  `aggregate_id`   BIGINT       NOT NULL,
  `event_type`     VARCHAR(64)  NOT NULL,
  `payload`        TEXT         NOT NULL,
  `status`         VARCHAR(16)  NOT NULL DEFAULT 'NEW',
  `attempts`       INT          NOT NULL DEFAULT 0,
  `next_retry_at`  DATETIME     DEFAULT NULL,
  `last_error`     VARCHAR(512) DEFAULT NULL,
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_outbox_poll (`status`, `next_retry_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
