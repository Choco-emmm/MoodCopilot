ALTER TABLE notifications
  ADD COLUMN life_event_id BIGINT UNSIGNED NULL AFTER diary_id,
  ADD KEY idx_notifications_life_event (life_event_id),
  ADD CONSTRAINT fk_notifications_life_event
    FOREIGN KEY (life_event_id) REFERENCES user_life_events(id) ON DELETE SET NULL;

-- Backfill only the unambiguous follow-up message format used by the event scheduler.
-- Generic system notifications are intentionally left untouched.
UPDATE notifications n
SET n.life_event_id = (
  SELECT MIN(e.id)
  FROM user_life_events e
  WHERE e.user_id = n.recipient_user_id
    AND n.message LIKE CONCAT('我想起你提到的「', e.title, '」，%')
)
WHERE n.life_event_id IS NULL
  AND n.type = 'SYSTEM'
  AND EXISTS (
    SELECT 1
    FROM user_life_events e
    WHERE e.user_id = n.recipient_user_id
      AND n.message LIKE CONCAT('我想起你提到的「', e.title, '」，%')
  );
