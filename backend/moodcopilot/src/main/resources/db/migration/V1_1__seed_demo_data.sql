-- MoodCopilot demo seed data (idempotent)
-- Target DB: mood_copilot_db

INSERT INTO users (id, display_name, email, password_hash, status)
VALUES
  (1001, '我', 'me@moodcopilot.local', NULL, 1),
  (1002, '同频的人', 'peer1@moodcopilot.local', NULL, 1),
  (1003, '慢慢来', 'peer2@moodcopilot.local', NULL, 1),
  (1004, '撑过去的人', 'peer3@moodcopilot.local', NULL, 1)
ON DUPLICATE KEY UPDATE
  display_name = VALUES(display_name),
  status = VALUES(status);

INSERT INTO diaries (id, author_user_id, author_name, content, visibility, resonance_count, is_deleted, created_at)
VALUES
  (2001, 1002, '同频的人', '今天和朋友解释了很久还是被误会，有点委屈，也觉得好累。', 'PUBLIC', 2, 0, NOW() - INTERVAL 40 MINUTE),
  (2002, 1003, '慢慢来', '加班到很晚，任务还没结束。虽然焦虑，但我还是把最难的一步做完了。', 'PUBLIC', 3, 0, NOW() - INTERVAL 32 MINUTE),
  (2003, 1004, '撑过去的人', '昨晚没睡好，白天整个人很疲惫。晚上散步后稍微舒服了一点。', 'PUBLIC', 1, 0, NOW() - INTERVAL 24 MINUTE),
  (2004, 1001, '我', '今天状态一般，但我把房间收拾干净了，心里轻了一点。', 'PRIVATE', 0, 0, NOW() - INTERVAL 16 MINUTE),
  (2005, 1001, '我', '我把最近的压力写下来后，好像没有那么闷了。', 'PUBLIC', 0, 0, NOW() - INTERVAL 8 MINUTE)
ON DUPLICATE KEY UPDATE
  content = VALUES(content),
  visibility = VALUES(visibility),
  resonance_count = VALUES(resonance_count),
  is_deleted = VALUES(is_deleted),
  updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO diary_analysis (diary_id, mood_label, mood_intensity, topic_labels_json, summary, feedback)
VALUES
  (2001, '委屈', 4, JSON_ARRAY('人际关系'), '被误会后感到委屈和疲惫。', '这份委屈值得被看见，先不用急着解释一切。'),
  (2002, '焦虑', 4, JSON_ARRAY('工作学习'), '加班压力很大，但完成了关键一步。', '先专注下一小步，焦虑会慢慢下降。'),
  (2003, '疲惫', 3, JSON_ARRAY('睡眠身体'), '睡眠不足导致白天疲惫，散步后稍有缓解。', '你已经在照顾自己了，继续保持小的恢复动作。'),
  (2004, '平静', 2, JSON_ARRAY('日常情绪'), '通过整理房间获得了一些轻松感。', '微小行动也能稳住状态，继续记录。'),
  (2005, '轻松', 2, JSON_ARRAY('自我成长'), '写下压力后情绪变得更松一点。', '你正在建立更稳定的自我支持方式。')
ON DUPLICATE KEY UPDATE
  mood_label = VALUES(mood_label),
  mood_intensity = VALUES(mood_intensity),
  topic_labels_json = VALUES(topic_labels_json),
  summary = VALUES(summary),
  feedback = VALUES(feedback),
  updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO diary_comments (id, diary_id, author_user_id, author_name, content, is_deleted, created_at)
VALUES
  (3001, 2001, 1003, '慢慢来', '你已经很努力了，先抱抱自己。', 0, NOW() - INTERVAL 30 MINUTE),
  (3002, 2002, 1002, '同频的人', '先把最小的一步拆出来，焦虑会降下来一点。', 0, NOW() - INTERVAL 22 MINUTE)
ON DUPLICATE KEY UPDATE
  content = VALUES(content),
  is_deleted = VALUES(is_deleted),
  updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO diary_resonances (id, diary_id, user_id, created_at)
VALUES
  (4001, 2001, 1001, NOW() - INTERVAL 28 MINUTE),
  (4002, 2001, 1003, NOW() - INTERVAL 27 MINUTE),
  (4003, 2002, 1001, NOW() - INTERVAL 20 MINUTE),
  (4004, 2002, 1002, NOW() - INTERVAL 19 MINUTE),
  (4005, 2002, 1004, NOW() - INTERVAL 18 MINUTE),
  (4006, 2003, 1001, NOW() - INTERVAL 12 MINUTE)
ON DUPLICATE KEY UPDATE
  created_at = VALUES(created_at);

INSERT INTO notifications (id, recipient_user_id, actor_user_id, diary_id, comment_id, type, message, is_read, created_at)
VALUES
  (5001, 1002, 1003, 2001, 3001, 'COMMENT', '慢慢来 评论了你的日记', 0, NOW() - INTERVAL 29 MINUTE),
  (5002, 1003, 1002, 2002, 3002, 'COMMENT', '同频的人 评论了你的日记', 0, NOW() - INTERVAL 21 MINUTE)
ON DUPLICATE KEY UPDATE
  message = VALUES(message),
  is_read = VALUES(is_read);

