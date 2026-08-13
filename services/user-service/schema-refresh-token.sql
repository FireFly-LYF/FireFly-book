-- user-service 补充表（在 MySQL user 库执行）
USE user;

CREATE TABLE IF NOT EXISTS `refresh_token` (
  `id`                  BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id`             BIGINT NOT NULL,
  `token_hash`          CHAR(64) NOT NULL COMMENT 'SHA-256 hex of refresh token',
  `device_fingerprint`  CHAR(64) NOT NULL COMMENT 'SHA-256 hex of client device id',
  `expires_at`          DATETIME NOT NULL,
  `revoked`             TINYINT NOT NULL DEFAULT 0,
  `created_at`          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_token_hash (`token_hash`),
  KEY idx_user (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 若表已存在且无 device_fingerprint 列，执行：
-- ALTER TABLE refresh_token
--   ADD COLUMN device_fingerprint CHAR(64) NOT NULL DEFAULT '' AFTER token_hash;
-- UPDATE refresh_token SET revoked=1 WHERE device_fingerprint='';
-- ALTER TABLE refresh_token ALTER COLUMN device_fingerprint DROP DEFAULT;
