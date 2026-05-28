-- Create diary_collection table
CREATE TABLE IF NOT EXISTS diary_collection (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '合集ID',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    name VARCHAR(100) NOT NULL COMMENT '合集名称',
    description VARCHAR(500) COMMENT '合集描述',
    cover_url VARCHAR(500) COMMENT '封面图片URL',
    visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC' COMMENT '可见性: PUBLIC(公开), PRIVATE(私密)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_user_id (user_id),
    INDEX idx_visibility (visibility),
    CONSTRAINT fk_diary_collection_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='日记合集表';

-- Create diary_collection_relation table (many-to-many relationship)
CREATE TABLE IF NOT EXISTS diary_collection_relation (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '关系ID',
    collection_id BIGINT UNSIGNED NOT NULL COMMENT '合集ID',
    diary_id BIGINT UNSIGNED NOT NULL COMMENT '日记ID',
    sort_order DOUBLE NOT NULL COMMENT '排序值，用于未来拖拽排序功能',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    UNIQUE KEY uk_collection_diary (collection_id, diary_id),
    INDEX idx_collection_id (collection_id),
    INDEX idx_diary_id (diary_id),
    INDEX idx_sort_order (collection_id, sort_order),
    CONSTRAINT fk_collection_relation_collection FOREIGN KEY (collection_id) REFERENCES diary_collection(id) ON DELETE CASCADE,
    CONSTRAINT fk_collection_relation_diary FOREIGN KEY (diary_id) REFERENCES diaries(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='日记合集关系表';