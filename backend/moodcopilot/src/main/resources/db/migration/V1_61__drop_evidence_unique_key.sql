CREATE INDEX idx_memory_evidence_user_source ON user_memory_evidence(user_id, source_type);
ALTER TABLE user_memory_evidence DROP INDEX uk_memory_evidence_source;
