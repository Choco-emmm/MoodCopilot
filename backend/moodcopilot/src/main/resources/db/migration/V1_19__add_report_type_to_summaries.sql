-- V1_19: 添加报告类型和 AI 引导字段，使周报/月报可持久化
ALTER TABLE diary_summaries
    ADD COLUMN report_type VARCHAR(20) NOT NULL DEFAULT 'CUSTOM' AFTER user_id,
    ADD COLUMN insights_json JSON NULL AFTER ai_summary,
    ADD COLUMN suggestions_json JSON NULL AFTER insights_json,
    ADD COLUMN follow_up_prompt TEXT NULL AFTER suggestions_json;

-- 唯一索引：同一用户同一时间段同一类型只保留一条
ALTER TABLE diary_summaries
    ADD UNIQUE KEY uq_summaries_user_period (user_id, start_date, end_date, report_type);
