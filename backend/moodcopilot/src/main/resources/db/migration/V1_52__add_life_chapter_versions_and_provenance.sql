ALTER TABLE user_life_chapters
  ADD COLUMN lifecycle_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  ADD COLUMN generation_status VARCHAR(32) NOT NULL DEFAULT 'SUCCEEDED',
  ADD COLUMN current_version INT UNSIGNED NOT NULL DEFAULT 1,
  ADD COLUMN source_snapshot_hash VARCHAR(64) NULL,
  ADD COLUMN dirty_since DATETIME(3) NULL,
  ADD COLUMN last_generated_at DATETIME(3) NULL,
  ADD COLUMN last_generation_error VARCHAR(2000) NULL,
  ADD COLUMN lock_version BIGINT UNSIGNED NOT NULL DEFAULT 0;

CREATE TABLE user_life_chapter_versions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  chapter_id BIGINT UNSIGNED NOT NULL,
  version INT UNSIGNED NOT NULL,
  title VARCHAR(128) NOT NULL,
  theme_summary VARCHAR(512) NOT NULL,
  dominant_moods_json VARCHAR(256) NOT NULL DEFAULT '[]',
  growth_reflection TEXT NULL,
  source_snapshot_hash VARCHAR(64) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_life_chapter_version (chapter_id, version),
  KEY idx_life_chapter_versions_chapter (chapter_id, version),
  CONSTRAINT fk_life_chapter_versions_chapter FOREIGN KEY (chapter_id)
    REFERENCES user_life_chapters(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE life_chapter_diaries (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  chapter_id BIGINT UNSIGNED NOT NULL,
  diary_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_life_chapter_diary (chapter_id, diary_id),
  KEY idx_life_chapter_diaries_diary (diary_id),
  CONSTRAINT fk_life_chapter_diaries_chapter FOREIGN KEY (chapter_id)
    REFERENCES user_life_chapters(id) ON DELETE CASCADE,
  CONSTRAINT fk_life_chapter_diaries_diary FOREIGN KEY (diary_id)
    REFERENCES diaries(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE life_chapter_events (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  chapter_id BIGINT UNSIGNED NOT NULL,
  event_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_life_chapter_event (chapter_id, event_id),
  KEY idx_life_chapter_events_event (event_id),
  CONSTRAINT fk_life_chapter_events_chapter FOREIGN KEY (chapter_id)
    REFERENCES user_life_chapters(id) ON DELETE CASCADE,
  CONSTRAINT fk_life_chapter_events_event FOREIGN KEY (event_id)
    REFERENCES user_life_events(id) ON DELETE CASCADE
) ENGINE=InnoDB;

INSERT INTO user_life_chapter_versions
  (chapter_id, version, title, theme_summary, dominant_moods_json, growth_reflection,
   source_snapshot_hash, created_at)
SELECT id, 1, title, theme_summary, dominant_moods_json, growth_reflection, NULL, created_at
FROM user_life_chapters;

INSERT IGNORE INTO life_chapter_diaries (chapter_id, diary_id)
SELECT c.id, d.id
FROM user_life_chapters c
JOIN diaries d ON d.author_user_id = c.user_id
  AND d.is_deleted = 0
  AND d.created_at >= c.start_date
  AND d.created_at < DATE_ADD(c.end_date, INTERVAL 1 DAY);

INSERT IGNORE INTO life_chapter_events (chapter_id, event_id)
SELECT c.id, e.id
FROM user_life_chapters c
JOIN user_life_events e ON e.user_id = c.user_id
  AND e.target_date <= c.end_date
  AND COALESCE(e.end_date, e.target_date) >= c.start_date;

UPDATE user_life_chapters c
SET c.diary_count = (SELECT COUNT(*) FROM life_chapter_diaries d WHERE d.chapter_id = c.id),
    c.last_generated_at = c.updated_at,
    c.source_snapshot_hash = NULL,
    c.generation_status = 'SUCCEEDED';
