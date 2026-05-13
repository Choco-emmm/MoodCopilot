CREATE TABLE user_profile_memory (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    attribute_key VARCHAR(64) NOT NULL,
    attribute_value VARCHAR(500) NOT NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_user_profile_memory_user_attr (user_id, attribute_key),
    KEY idx_user_profile_memory_user_time (user_id, update_time),
    CONSTRAINT fk_user_profile_memory_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
