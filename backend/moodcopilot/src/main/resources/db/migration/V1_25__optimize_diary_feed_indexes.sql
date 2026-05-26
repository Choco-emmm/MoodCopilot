ALTER TABLE diaries
  ADD KEY idx_diaries_visibility_pinned_created (visibility, is_pinned, created_at),
  ADD KEY idx_diaries_author_visibility_created (author_user_id, visibility, created_at);