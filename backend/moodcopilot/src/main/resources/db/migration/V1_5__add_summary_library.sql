-- V1_5__add_summary_library.sql
-- AI 总结库：保存用户自定义时期的 AI 情绪总结

CREATE TABLE IF NOT EXISTS diary_summaries
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    title      VARCHAR(100)    NOT NULL,
    start_date DATE            NOT NULL,
    end_date   DATE            NOT NULL,
    ai_summary TEXT            NOT NULL,
    created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_summaries_user (user_id),
    CONSTRAINT fk_summaries_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;
