-- Add reply support to diary_comments
ALTER TABLE diary_comments
    ADD COLUMN parent_comment_id BIGINT UNSIGNED NULL AFTER id,
    ADD INDEX idx_diary_comments_parent (parent_comment_id),
    ADD CONSTRAINT fk_diary_comments_parent
        FOREIGN KEY (parent_comment_id) REFERENCES diary_comments(id)
        ON DELETE CASCADE;
