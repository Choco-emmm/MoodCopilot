ALTER TABLE user_memory_candidates
  ADD COLUMN merged_into_id BIGINT UNSIGNED NULL,
  ADD COLUMN merge_reason VARCHAR(255) NULL,
  ADD KEY idx_memory_candidates_merged_into (merged_into_id),
  ADD CONSTRAINT fk_memory_candidates_merged_into
    FOREIGN KEY (merged_into_id) REFERENCES user_memory_candidates(id) ON DELETE SET NULL;
