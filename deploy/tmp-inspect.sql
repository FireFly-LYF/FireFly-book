SHOW DATABASES;
SELECT TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA IN ('content','social','notify')
  AND COLUMN_NAME IN ('idem_key','ref_id');
SELECT TABLE_SCHEMA, TABLE_NAME, INDEX_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA IN ('content','social','notify')
  AND INDEX_NAME IN ('uk_note_idem','uk_comment_idem','uk_notify_event');
