CREATE TABLE user_personas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    version INT NOT NULL,
    role VARCHAR(64) NOT NULL,
    tone_json JSON NOT NULL,
    behavior_flags_json JSON NOT NULL,
    custom_description VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_persona_version (user_id, version),
    INDEX idx_user_persona_latest (user_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE conversation_persona_overrides (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    version INT NOT NULL,
    role VARCHAR(64) NULL,
    tone_json JSON NULL,
    behavior_flags_json JSON NULL,
    custom_description VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_conversation_persona_version (conversation_id, version),
    INDEX idx_conversation_persona_latest (user_id, conversation_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
