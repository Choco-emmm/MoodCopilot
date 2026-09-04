ALTER TABLE diary_knowledge_graph
  ADD COLUMN source_triple_ids JSON NULL,
  ADD COLUMN source_diary_ids JSON NULL,
  ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'active',
  ADD COLUMN superseded_by_id BIGINT NULL,
  ADD KEY idx_graph_user_status (user_id, status),
  ADD KEY idx_graph_superseded_by (superseded_by_id);
