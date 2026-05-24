ALTER TABLE users
    ADD COLUMN profile_notify_enabled TINYINT(1) NOT NULL DEFAULT 1 AFTER daily_notify_enabled;
