CREATE TABLE IF NOT EXISTS user_life_events (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  title VARCHAR(128) NOT NULL,
  description TEXT NULL,
  target_date DATE NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  diary_ids_json TEXT NOT NULL,
  last_diary_id BIGINT UNSIGNED NOT NULL,
  follow_up_note TEXT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_life_events_user_status (user_id, status),
  KEY idx_life_events_user_target_date (user_id, target_date),
  CONSTRAINT fk_life_events_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_life_chapters (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  title VARCHAR(128) NOT NULL,
  theme_summary VARCHAR(512) NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  dominant_moods_json VARCHAR(256) NOT NULL DEFAULT '[]',
  growth_reflection TEXT NOT NULL,
  diary_count INT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_life_chapters_user_dates (user_id, start_date, end_date),
  CONSTRAINT fk_life_chapters_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
