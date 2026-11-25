CREATE TABLE IF NOT EXISTS `user_suggestion_replies` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `suggestion_id` BIGINT NOT NULL COMMENT '所属反馈ID',
    `parent_reply_id` BIGINT NULL COMMENT '被回复的回复ID',
    `root_reply_id` BIGINT NULL COMMENT '所属根回复ID（两层扁平结构）',
    `author_user_id` BIGINT NOT NULL COMMENT '回复者用户ID',
    `author_name` VARCHAR(64) NOT NULL COMMENT '回复者展示名快照',
    `author_role` VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT '回复时角色: USER/ADMIN',
    `content` TEXT NOT NULL COMMENT '回复内容',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_suggestion_replies_suggestion_id` (`suggestion_id`, `created_at`),
    INDEX `idx_suggestion_replies_parent_id` (`parent_reply_id`),
    INDEX `idx_suggestion_replies_root_id` (`root_reply_id`),
    INDEX `idx_suggestion_replies_author_id` (`author_user_id`),
    CONSTRAINT `fk_suggestion_replies_suggestion`
        FOREIGN KEY (`suggestion_id`) REFERENCES `user_suggestions`(`id`)
        ON DELETE CASCADE,
    CONSTRAINT `fk_suggestion_replies_parent`
        FOREIGN KEY (`parent_reply_id`) REFERENCES `user_suggestion_replies`(`id`)
        ON DELETE SET NULL,
    CONSTRAINT `fk_suggestion_replies_root`
        FOREIGN KEY (`root_reply_id`) REFERENCES `user_suggestion_replies`(`id`)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户反馈回复表';

ALTER TABLE `notifications`
    ADD COLUMN `suggestion_id` BIGINT NULL AFTER `life_event_id`,
    ADD INDEX `idx_notifications_suggestion` (`suggestion_id`),
    ADD CONSTRAINT `fk_notifications_suggestion`
        FOREIGN KEY (`suggestion_id`) REFERENCES `user_suggestions`(`id`)
        ON DELETE SET NULL;
