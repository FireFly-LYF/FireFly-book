-- content.note 附近流：纬度 / 经度 / 地点名
-- mysql ... < note_location.sql  或 docker exec 执行

USE content;

ALTER TABLE `note`
  ADD COLUMN `lat` DOUBLE NULL COMMENT '纬度 WGS84' AFTER `cover_url`,
  ADD COLUMN `lng` DOUBLE NULL COMMENT '经度 WGS84' AFTER `lat`,
  ADD COLUMN `place_name` VARCHAR(64) NULL COMMENT '地点展示名' AFTER `lng`;

-- 可选索引（演示数据量小，先不加空间索引）
-- ALTER TABLE `note` ADD KEY idx_note_geo (`status`, `lat`, `lng`);
