ALTER TABLE notifications
  ADD KEY idx_notifications_recipient_created (recipient_user_id, created_at);