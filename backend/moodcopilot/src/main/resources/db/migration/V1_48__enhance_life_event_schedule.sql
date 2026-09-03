ALTER TABLE user_life_events
  MODIFY COLUMN diary_ids_json TEXT NULL,
  MODIFY COLUMN last_diary_id BIGINT UNSIGNED NULL,
  ADD COLUMN end_date DATE NULL AFTER target_date,
  ADD COLUMN start_time TIME NULL AFTER end_date,
  ADD COLUMN end_time TIME NULL AFTER start_time,
  ADD COLUMN title_aliases_json TEXT NULL AFTER diary_ids_json;

CREATE INDEX idx_life_events_user_end_date ON user_life_events (user_id, end_date);
