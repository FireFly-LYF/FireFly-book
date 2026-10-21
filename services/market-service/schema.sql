-- market-service 建库建表（在 MySQL 中执行）
CREATE DATABASE IF NOT EXISTS market DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE market;

CREATE TABLE IF NOT EXISTS `product` (
  `id`           BIGINT PRIMARY KEY AUTO_INCREMENT,
  `seller_id`    BIGINT       NOT NULL,
  `title`        VARCHAR(128) NOT NULL,
  `description`  VARCHAR(2000) DEFAULT NULL,
  `price_cents`  INT          NOT NULL,
  `stock`        INT          NOT NULL DEFAULT 0,
  `cover_url`    VARCHAR(512) DEFAULT NULL,
  `status`       VARCHAR(16)  NOT NULL DEFAULT 'ON' COMMENT 'ON|OFF',
  `idem_key`     VARCHAR(64)  DEFAULT NULL,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_product_seller_idem (`seller_id`, `idem_key`),
  KEY idx_product_status_time (`status`, `created_at`),
  KEY idx_product_seller (`seller_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `cart_item` (
  `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id`     BIGINT NOT NULL,
  `product_id`  BIGINT NOT NULL,
  `quantity`    INT    NOT NULL DEFAULT 1,
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cart_user_product (`user_id`, `product_id`),
  KEY idx_cart_user (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `address` (
  `id`             BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id`        BIGINT       NOT NULL,
  `receiver_name`  VARCHAR(64)  NOT NULL,
  `phone`          VARCHAR(32)  NOT NULL,
  `province`       VARCHAR(64)  NOT NULL,
  `city`           VARCHAR(64)  NOT NULL,
  `district`       VARCHAR(64)  DEFAULT NULL,
  `detail`         VARCHAR(256) NOT NULL,
  `is_default`     TINYINT      NOT NULL DEFAULT 0,
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_address_user (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `market_order` (
  `id`                BIGINT PRIMARY KEY AUTO_INCREMENT,
  `buyer_id`          BIGINT       NOT NULL,
  `seller_id`         BIGINT       NOT NULL,
  `status`            VARCHAR(32)  NOT NULL COMMENT 'CREATED|PAID|SHIPPED|COMPLETED|CANCELLED|REFUNDING|REFUNDED',
  `amount_cents`      INT          NOT NULL,
  `address_snapshot`  JSON         NOT NULL,
  `idem_key`          VARCHAR(64)  DEFAULT NULL,
  `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_order_buyer_idem (`buyer_id`, `idem_key`),
  KEY idx_order_buyer (`buyer_id`, `created_at`),
  KEY idx_order_seller (`seller_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `order_item` (
  `id`           BIGINT PRIMARY KEY AUTO_INCREMENT,
  `order_id`     BIGINT       NOT NULL,
  `product_id`   BIGINT       NOT NULL,
  `title`        VARCHAR(128) NOT NULL,
  `cover_url`    VARCHAR(512) DEFAULT NULL,
  `price_cents`  INT          NOT NULL,
  `quantity`     INT          NOT NULL,
  KEY idx_order_item_order (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `payment` (
  `id`            BIGINT PRIMARY KEY AUTO_INCREMENT,
  `order_id`      BIGINT       NOT NULL,
  `amount_cents`  INT          NOT NULL,
  `channel`       VARCHAR(32)  NOT NULL DEFAULT 'mock',
  `status`        VARCHAR(16)  NOT NULL COMMENT 'PAID|REFUNDED',
  `paid_at`       DATETIME     NOT NULL,
  `refunded_at`   DATETIME     DEFAULT NULL,
  UNIQUE KEY uk_payment_order (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `after_sale` (
  `id`                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  `order_id`           BIGINT       NOT NULL,
  `buyer_id`           BIGINT       NOT NULL,
  `seller_id`          BIGINT       NOT NULL,
  `type`               VARCHAR(32)  NOT NULL COMMENT 'CANCEL_REFUND|REFUND',
  `reason`             VARCHAR(256) DEFAULT NULL,
  `status`             VARCHAR(16)  NOT NULL COMMENT 'PENDING|AGREED|REJECTED',
  `prev_order_status`  VARCHAR(32)  NOT NULL,
  `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_after_sale_order (`order_id`),
  KEY idx_after_sale_seller (`seller_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `outbox_event` (
  `id`             BIGINT PRIMARY KEY AUTO_INCREMENT,
  `aggregate_type` VARCHAR(32)  NOT NULL,
  `aggregate_id`   BIGINT       NOT NULL,
  `event_type`     VARCHAR(64)  NOT NULL,
  `payload`        JSON         NOT NULL,
  `status`         VARCHAR(16)  NOT NULL DEFAULT 'NEW',
  `attempts`       INT          NOT NULL DEFAULT 0,
  `next_retry_at`  DATETIME     DEFAULT NULL,
  `last_error`     VARCHAR(512) DEFAULT NULL,
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_outbox_poll (`status`, `next_retry_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
