-- MoodCopilot initial schema (MySQL 8+)

-- 1) Users
CREATE TABLE IF NOT EXISTS users (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  display_name VARCHAR(64) NOT NULL,
  email VARCHAR(191) NULL,
  password_hash VARCHAR(255) NULL,
  status TINYINT UNSIGNED NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email),
  KEY idx_users_created_at (created_at)
) ENGINE=InnoDB;

-- 2) Diaries
CREATE TABLE IF NOT EXISTS diaries (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  author_user_id BIGINT UNSIGNED NOT NULL,
  author_name VARCHAR(64) NOT NULL,
  content TEXT NOT NULL,
  visibility ENUM('PRIVATE', 'PUBLIC') NOT NULL DEFAULT 'PRIVATE',
  resonance_count INT UNSIGNED NOT NULL DEFAULT 0,
  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_diaries_author_created (author_user_id, created_at),
  KEY idx_diaries_visibility_created (visibility, created_at),
  CONSTRAINT fk_diaries_author_user
    FOREIGN KEY (author_user_id) REFERENCES users(id)
) ENGINE=InnoDB;

-- 3) AI analysis (1:1 with diary)
CREATE TABLE IF NOT EXISTS diary_analysis (
  diary_id BIGINT UNSIGNED NOT NULL,
  mood_label VARCHAR(32) NOT NULL,
  mood_intensity TINYINT UNSIGNED NOT NULL,
  topic_labels_json JSON NOT NULL,
  summary VARCHAR(255) NOT NULL,
  feedback VARCHAR(500) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (diary_id),
  CONSTRAINT chk_diary_analysis_intensity CHECK (mood_intensity BETWEEN 1 AND 5),
  CONSTRAINT fk_diary_analysis_diary
    FOREIGN KEY (diary_id) REFERENCES diaries(id)
    ON DELETE CASCADE
) ENGINE=InnoDB;

-- 4) Comments
CREATE TABLE IF NOT EXISTS diary_comments (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  diary_id BIGINT UNSIGNED NOT NULL,
  author_user_id BIGINT UNSIGNED NULL,
  author_name VARCHAR(64) NOT NULL,
  content VARCHAR(1000) NOT NULL,
  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_diary_comments_diary_created (diary_id, created_at),
  KEY idx_diary_comments_author_created (author_user_id, created_at),
  CONSTRAINT fk_diary_comments_diary
    FOREIGN KEY (diary_id) REFERENCES diaries(id)
    ON DELETE CASCADE,
  CONSTRAINT fk_diary_comments_author_user
    FOREIGN KEY (author_user_id) REFERENCES users(id)
    ON DELETE SET NULL
) ENGINE=InnoDB;

-- 5) Resonance reactions (one user can resonate once per diary)
CREATE TABLE IF NOT EXISTS diary_resonances (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  diary_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_diary_resonances_diary_user (diary_id, user_id),
  KEY idx_diary_resonances_user_created (user_id, created_at),
  CONSTRAINT fk_diary_resonances_diary
    FOREIGN KEY (diary_id) REFERENCES diaries(id)
    ON DELETE CASCADE,
  CONSTRAINT fk_diary_resonances_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
) ENGINE=InnoDB;

-- 6) Basic notifications (for comment/reply/resonance)
CREATE TABLE IF NOT EXISTS notifications (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  recipient_user_id BIGINT UNSIGNED NOT NULL,
  actor_user_id BIGINT UNSIGNED NULL,
  diary_id BIGINT UNSIGNED NULL,
  comment_id BIGINT UNSIGNED NULL,
  type ENUM('COMMENT', 'REPLY', 'RESONANCE', 'SYSTEM') NOT NULL,
  message VARCHAR(255) NOT NULL,
  is_read TINYINT(1) NOT NULL DEFAULT 0,
  read_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_notifications_recipient_read_created (recipient_user_id, is_read, created_at),
  KEY idx_notifications_diary (diary_id),
  CONSTRAINT fk_notifications_recipient_user
    FOREIGN KEY (recipient_user_id) REFERENCES users(id)
    ON DELETE CASCADE,
  CONSTRAINT fk_notifications_actor_user
    FOREIGN KEY (actor_user_id) REFERENCES users(id)
    ON DELETE SET NULL,
  CONSTRAINT fk_notifications_diary
    FOREIGN KEY (diary_id) REFERENCES diaries(id)
    ON DELETE CASCADE,
  CONSTRAINT fk_notifications_comment
    FOREIGN KEY (comment_id) REFERENCES diary_comments(id)
    ON DELETE CASCADE
) ENGINE=InnoDB;

