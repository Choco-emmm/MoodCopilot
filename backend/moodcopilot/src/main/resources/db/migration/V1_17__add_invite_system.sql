-- V1_17: invite system
ALTER TABLE users
    ADD COLUMN invite_code VARCHAR(32) NULL UNIQUE COMMENT '专属邀请码',
    ADD COLUMN invite_quota INT NOT NULL DEFAULT 3 COMMENT '剩余邀请名额',
    ADD COLUMN invited_by BIGINT UNSIGNED NULL COMMENT '邀请人ID',
    ADD INDEX idx_users_invite_code (invite_code);
