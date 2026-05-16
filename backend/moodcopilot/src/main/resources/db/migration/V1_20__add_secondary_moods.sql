ALTER TABLE diary_analysis
  ADD COLUMN secondary_moods_json JSON NULL
  COMMENT '次要情绪数组，如 ["感恩","平静"]，单情绪日记可为空';
