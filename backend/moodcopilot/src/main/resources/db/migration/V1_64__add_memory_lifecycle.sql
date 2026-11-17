ALTER TABLE user_profile_memory ADD COLUMN lifecycle_state VARCHAR(16) NOT NULL DEFAULT 'active';
ALTER TABLE user_profile_memory ADD COLUMN freshness DECIMAL(5,4) NOT NULL DEFAULT 1.0000;
ALTER TABLE user_profile_memory ADD COLUMN lifecycle_reason VARCHAR(64) NULL;
ALTER TABLE user_profile_memory ADD COLUMN lifecycle_updated_at DATETIME(3) NULL;

CREATE INDEX idx_memory_lifecycle_scan ON user_profile_memory (lifecycle_state, last_evidence_at);
CREATE INDEX idx_memory_user_lifecycle ON user_profile_memory (user_id, lifecycle_state);

UPDATE user_profile_memory
   SET lifecycle_updated_at = COALESCE(last_evidence_at, updated_at)
 WHERE lifecycle_updated_at IS NULL;

-- diaries 已存在 idx_diaries_author_created (author_user_id, created_at)，由 V1__init_schema.sql 建立，
-- 曝光统计所需的索引无需在此重建。重复创建会让 MySQL 报 Duplicate key name 并使迁移停在半完成状态
-- （MySQL 的 DDL 不能回滚），因此这里刻意不写 CREATE INDEX。
