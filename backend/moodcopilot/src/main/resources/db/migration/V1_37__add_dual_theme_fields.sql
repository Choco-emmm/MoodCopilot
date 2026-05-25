ALTER TABLE users ADD COLUMN light_theme VARCHAR(32) DEFAULT 'green';
ALTER TABLE users ADD COLUMN dark_theme VARCHAR(32) DEFAULT 'minimal-dark';
ALTER TABLE users ADD COLUMN theme_mode VARCHAR(16) DEFAULT 'auto';

-- 迁移现有 theme 值：如果用户之前选了暗色主题，把它写入 dark_theme
UPDATE users SET dark_theme = theme WHERE theme IN ('black-rice', 'minimal-dark');
-- 如果用户之前选了亮色主题，把它写入 light_theme（含留空或用旧默认值的情况）
UPDATE users SET light_theme = theme WHERE theme NOT IN ('black-rice', 'minimal-dark');

-- theme 列保留不动，兼容旧代码读取
