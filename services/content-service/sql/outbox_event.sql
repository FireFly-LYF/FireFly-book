-- content 库：笔记事件 Outbox（与业务同事务写入，定时投递 MQ）
-- 执行：mysql -uroot -p content < outbox_event.sql

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
