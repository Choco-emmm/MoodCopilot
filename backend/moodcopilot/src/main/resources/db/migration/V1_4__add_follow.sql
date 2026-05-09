-- V1_4__add_follow.sql
-- 关注系统：follows 表，通知类型增加 FOLLOW

CREATE TABLE IF NOT EXISTS follows
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    follower_id BIGINT UNSIGNED NOT NULL,
    followed_id BIGINT UNSIGNED NOT NULL,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_follows_pair (follower_id, followed_id),
    KEY idx_follows_followed (followed_id),
    CONSTRAINT fk_follows_follower FOREIGN KEY (follower_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_follows_followed FOREIGN KEY (followed_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

ALTER TABLE notifications
    MODIFY COLUMN type ENUM ('COMMENT', 'REPLY', 'RESONANCE', 'SYSTEM', 'FOLLOW') NOT NULL;
