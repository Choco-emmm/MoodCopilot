ALTER TABLE user_life_chapters
  MODIFY COLUMN end_date DATE NULL,
  ADD COLUMN segment_type VARCHAR(32) NOT NULL DEFAULT 'LEGACY_MONTH',
  ADD COLUMN is_open TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN boundary_reason VARCHAR(512) NULL,
  ADD COLUMN boundary_confidence DECIMAL(5,4) NULL,
  ADD COLUMN last_source_at DATETIME(3) NULL,
  ADD COLUMN previous_chapter_id BIGINT UNSIGNED NULL,
  ADD COLUMN next_chapter_id BIGINT UNSIGNED NULL,
  ADD KEY idx_life_chapters_user_dynamic (user_id, segment_type, is_open, start_date);

UPDATE user_life_chapters
SET segment_type = 'LEGACY_MONTH', is_open = 0
WHERE segment_type IS NULL OR segment_type <> 'DYNAMIC';

CREATE TABLE user_life_timeline_candidates (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  left_chapter_id BIGINT UNSIGNED NOT NULL,
  right_chapter_id BIGINT UNSIGNED NULL,
  suggested_start_date DATE NOT NULL,
  suggested_end_date DATE NULL,
  reason VARCHAR(512) NOT NULL,
  confidence DECIMAL(5,4) NOT NULL DEFAULT 0,
  source_diary_ids_json TEXT NOT NULL,
  source_event_ids_json TEXT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  resolved_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  KEY idx_timeline_candidates_user_status (user_id, status, created_at),
  CONSTRAINT fk_timeline_candidate_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_timeline_candidate_left FOREIGN KEY (left_chapter_id) REFERENCES user_life_chapters(id) ON DELETE CASCADE,
  CONSTRAINT fk_timeline_candidate_right FOREIGN KEY (right_chapter_id) REFERENCES user_life_chapters(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE user_life_chapter_version_sources (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  version_id BIGINT UNSIGNED NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  source_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_chapter_version_source (version_id, source_type, source_id),
  KEY idx_chapter_version_sources_version (version_id),
  CONSTRAINT fk_chapter_version_sources_version FOREIGN KEY (version_id)
    REFERENCES user_life_chapter_versions(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE user_life_chapter_source_moves (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  source_id BIGINT UNSIGNED NOT NULL,
  from_chapter_id BIGINT UNSIGNED NULL,
  to_chapter_id BIGINT UNSIGNED NULL,
  reason VARCHAR(512) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_chapter_source_moves_user (user_id, source_type, source_id),
  CONSTRAINT fk_chapter_source_moves_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

INSERT IGNORE INTO user_life_chapter_version_sources (version_id, source_type, source_id)
SELECT v.id, 'DIARY', d.diary_id
FROM user_life_chapter_versions v
JOIN life_chapter_diaries d ON d.chapter_id = v.chapter_id;

INSERT IGNORE INTO user_life_chapter_version_sources (version_id, source_type, source_id)
SELECT v.id, 'EVENT', e.event_id
FROM user_life_chapter_versions v
JOIN life_chapter_events e ON e.chapter_id = v.chapter_id;

UPDATE user_life_chapters c
SET c.is_open = 0
WHERE c.segment_type = 'LEGACY_MONTH';
