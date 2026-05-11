ALTER TABLE users
    ADD COLUMN avatar VARCHAR(512) NULL AFTER display_name,
    ADD COLUMN daily_notify_enabled TINYINT(1) NOT NULL DEFAULT 1 AFTER is_vip;

ALTER TABLE notifications
    MODIFY COLUMN message TEXT NOT NULL;
