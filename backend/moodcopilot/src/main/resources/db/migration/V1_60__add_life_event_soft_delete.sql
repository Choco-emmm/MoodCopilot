ALTER TABLE user_life_events
  ADD COLUMN deleted_at DATETIME(3) NULL AFTER updated_at,
  ADD KEY idx_life_events_user_deleted_target (user_id, deleted_at, target_date);
