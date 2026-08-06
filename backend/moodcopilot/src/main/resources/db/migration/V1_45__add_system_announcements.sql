CREATE TABLE IF NOT EXISTS system_announcements (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  version BIGINT UNSIGNED NOT NULL,
  title VARCHAR(60) NOT NULL,
  content VARCHAR(2000) NOT NULL,
  published_by_user_id BIGINT UNSIGNED NOT NULL,
  published_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_system_announcements_version (version),
  KEY idx_system_announcements_published_at (published_at),
  CONSTRAINT fk_system_announcements_published_by
    FOREIGN KEY (published_by_user_id) REFERENCES users(id)
) ENGINE=InnoDB;
