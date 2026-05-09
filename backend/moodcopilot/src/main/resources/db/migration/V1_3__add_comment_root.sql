-- Add root_comment_id for two-level flat reply structure
ALTER TABLE diary_comments
    ADD COLUMN root_comment_id BIGINT UNSIGNED NULL AFTER parent_comment_id,
    ADD INDEX idx_diary_comments_root (root_comment_id),
    ADD CONSTRAINT fk_diary_comments_root
        FOREIGN KEY (root_comment_id) REFERENCES diary_comments(id)
        ON DELETE CASCADE;
