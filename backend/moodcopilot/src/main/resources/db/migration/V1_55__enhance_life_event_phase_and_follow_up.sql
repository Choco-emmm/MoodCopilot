ALTER TABLE user_life_events
  ADD COLUMN temporal_phase VARCHAR(16) NOT NULL DEFAULT 'PAST' AFTER end_time,
  ADD COLUMN next_follow_up_at DATETIME(3) NULL AFTER temporal_phase,
  ADD COLUMN last_follow_up_at DATETIME(3) NULL AFTER next_follow_up_at,
  ADD COLUMN follow_up_count INT UNSIGNED NOT NULL DEFAULT 0 AFTER last_follow_up_at,
  ADD COLUMN follow_up_reason VARCHAR(512) NULL AFTER follow_up_count,
  ADD COLUMN follow_up_completed TINYINT(1) NOT NULL DEFAULT 0 AFTER follow_up_reason,
  ADD COLUMN importance DECIMAL(5,4) NULL AFTER follow_up_completed,
  ADD KEY idx_life_events_user_follow_up (user_id, status, next_follow_up_at);

UPDATE user_life_events
SET temporal_phase = CASE
      WHEN target_date > CURRENT_DATE() THEN 'UPCOMING'
      WHEN COALESCE(end_date, target_date) >= CURRENT_DATE() THEN 'ONGOING'
      ELSE 'PAST'
    END,
    follow_up_completed = CASE WHEN status = 'FOLLOWED_UP' THEN 1 ELSE 0 END,
    next_follow_up_at = CASE
      WHEN status = 'FOLLOWED_UP' THEN NULL
      WHEN target_date > CURRENT_DATE() THEN TIMESTAMP(DATE_SUB(target_date, INTERVAL 1 DAY), '10:00:00')
      WHEN end_date IS NOT NULL AND end_date >= CURRENT_DATE() THEN TIMESTAMP(DATE_ADD(end_date, INTERVAL 1 DAY), '10:00:00')
      WHEN COALESCE(end_date, target_date) < CURRENT_DATE() THEN CURRENT_TIMESTAMP(3)
      ELSE CURRENT_TIMESTAMP(3)
    END
WHERE temporal_phase IS NULL OR next_follow_up_at IS NULL;
