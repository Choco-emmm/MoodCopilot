ALTER TABLE notifications
    ADD COLUMN is_markdown TINYINT(1) NOT NULL DEFAULT 0 AFTER message;
