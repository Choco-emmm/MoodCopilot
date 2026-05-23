CREATE TABLE IF NOT EXISTS `user_suggestions` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '提交建议的用户ID',
    `content` TEXT NOT NULL COMMENT '建议内容',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/READ/RESOLVED',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_user_suggestions_user_id` (`user_id`),
    INDEX `idx_user_suggestions_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户建议与反馈表';
