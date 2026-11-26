ALTER TABLE users
    ADD COLUMN onboarding_enrolled_version INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS user_onboarding (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    guide_key VARCHAR(64) NOT NULL,
    guide_version INT NOT NULL DEFAULT 1,
    scope VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    completed_at DATETIME(3) NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_onboarding_key (user_id, guide_key, guide_version, scope),
    KEY idx_user_onboarding_user (user_id),
    CONSTRAINT fk_user_onboarding_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
