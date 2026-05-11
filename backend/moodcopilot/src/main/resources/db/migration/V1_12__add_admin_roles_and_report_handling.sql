ALTER TABLE users
    ADD COLUMN role VARCHAR(32) NOT NULL DEFAULT 'USER' AFTER status;

ALTER TABLE user_reports
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'PENDING' AFTER reason,
    ADD COLUMN handled_by_user_id BIGINT UNSIGNED NULL AFTER status,
    ADD COLUMN handled_at DATETIME(3) NULL AFTER handled_by_user_id,
    ADD COLUMN handle_note VARCHAR(500) NULL AFTER handled_at,
    ADD KEY idx_user_reports_status_created (status, created_at),
    ADD CONSTRAINT fk_user_reports_handler FOREIGN KEY (handled_by_user_id) REFERENCES users(id) ON DELETE SET NULL;
