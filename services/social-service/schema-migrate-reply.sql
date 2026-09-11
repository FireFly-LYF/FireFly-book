-- 已有库升级：二级评论 reply_to_user_id（执行一次即可）
USE social;

ALTER TABLE `comment`
  ADD COLUMN `reply_to_user_id` BIGINT DEFAULT NULL COMMENT '回复谁（楼中楼 @）' AFTER `parent_id`;

ALTER TABLE `comment` ADD KEY `idx_parent` (`parent_id`);
