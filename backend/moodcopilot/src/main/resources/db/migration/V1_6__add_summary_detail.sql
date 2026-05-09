-- V1_6: 总结详情存储 + VIP 标记
ALTER TABLE diary_summaries
    ADD COLUMN moods_json  JSON NULL AFTER ai_summary,
    ADD COLUMN topics_json JSON NULL AFTER moods_json,
    ADD COLUMN diary_count INT  NOT NULL DEFAULT 0 AFTER topics_json;

ALTER TABLE users
    ADD COLUMN is_vip TINYINT(1) NOT NULL DEFAULT 0 AFTER status;
