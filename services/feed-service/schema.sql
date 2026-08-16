-- Feed 写扩散底表（MySQL）；Redis 仅作热缓存
-- 执行：mysql -uroot -p feed < schema.sql

CREATE DATABASE IF NOT EXISTS `feed` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `feed`;

CREATE TABLE IF NOT EXISTS `feed_inbox` (
  `user_id`    BIGINT   NOT NULL COMMENT '粉丝（时间线主人）',
  `note_id`    BIGINT   NOT NULL,
  `author_id`  BIGINT   NOT NULL COMMENT '笔记作者',
  `created_at` DATETIME NOT NULL COMMENT '笔记创建时间，用于排序',
  PRIMARY KEY (`user_id`, `note_id`),
  KEY `idx_user_time` (`user_id`, `created_at` DESC),
  KEY `idx_note` (`note_id`),
  KEY `idx_user_author` (`user_id`, `author_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
