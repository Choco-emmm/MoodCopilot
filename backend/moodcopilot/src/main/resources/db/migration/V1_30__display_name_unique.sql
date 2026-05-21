-- 用户名去重 + 唯一约束 + 每周修改次数追踪

-- Step 1: 创建临时表存放去重后的名字
CREATE TEMPORARY TABLE tmp_dedup_names (
    id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    new_display_name VARCHAR(64) NOT NULL
) ENGINE=MEMORY;

INSERT INTO tmp_dedup_names (id, new_display_name)
SELECT
    id,
    CONCAT(
        display_name,
        CASE WHEN rn = 1 THEN '' ELSE CONCAT('_', rn) END
    ) AS new_display_name
FROM (
    SELECT
        id,
        display_name,
        ROW_NUMBER() OVER (PARTITION BY display_name ORDER BY created_at) AS rn
    FROM users
) ranked;

-- Step 2: 应用去重
UPDATE users u
INNER JOIN tmp_dedup_names d ON u.id = d.id
SET u.display_name = d.new_display_name;

DROP TEMPORARY TABLE tmp_dedup_names;

-- Step 3: 添加唯一约束
ALTER TABLE users ADD UNIQUE KEY uk_users_display_name (display_name);

-- Step 4: 每周用户名修改次数追踪
ALTER TABLE users ADD COLUMN name_change_count INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN name_change_week INT NOT NULL DEFAULT 0;
