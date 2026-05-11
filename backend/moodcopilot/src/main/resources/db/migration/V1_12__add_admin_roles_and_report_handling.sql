DELIMITER //

CREATE PROCEDURE moodcopilot_add_column_if_missing(
    IN table_name_param VARCHAR(64),
    IN column_name_param VARCHAR(64),
    IN ddl_param TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = table_name_param
          AND COLUMN_NAME = column_name_param
    ) THEN
        SET @ddl = ddl_param;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

CREATE PROCEDURE moodcopilot_add_index_if_missing(
    IN table_name_param VARCHAR(64),
    IN index_name_param VARCHAR(64),
    IN ddl_param TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = table_name_param
          AND INDEX_NAME = index_name_param
    ) THEN
        SET @ddl = ddl_param;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

CREATE PROCEDURE moodcopilot_add_fk_if_missing(
    IN table_name_param VARCHAR(64),
    IN constraint_name_param VARCHAR(64),
    IN ddl_param TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
          AND TABLE_NAME = table_name_param
          AND CONSTRAINT_NAME = constraint_name_param
          AND CONSTRAINT_TYPE = 'FOREIGN KEY'
    ) THEN
        SET @ddl = ddl_param;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

DELIMITER ;

CALL moodcopilot_add_column_if_missing(
    'users',
    'role',
    'ALTER TABLE users ADD COLUMN role VARCHAR(32) NOT NULL DEFAULT ''USER'' AFTER status'
);

CALL moodcopilot_add_column_if_missing(
    'user_reports',
    'status',
    'ALTER TABLE user_reports ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT ''PENDING'' AFTER reason'
);

CALL moodcopilot_add_column_if_missing(
    'user_reports',
    'handled_by_user_id',
    'ALTER TABLE user_reports ADD COLUMN handled_by_user_id BIGINT UNSIGNED NULL AFTER status'
);

CALL moodcopilot_add_column_if_missing(
    'user_reports',
    'handled_at',
    'ALTER TABLE user_reports ADD COLUMN handled_at DATETIME(3) NULL AFTER handled_by_user_id'
);

CALL moodcopilot_add_column_if_missing(
    'user_reports',
    'handle_note',
    'ALTER TABLE user_reports ADD COLUMN handle_note VARCHAR(500) NULL AFTER handled_at'
);

CALL moodcopilot_add_index_if_missing(
    'user_reports',
    'idx_user_reports_status_created',
    'ALTER TABLE user_reports ADD KEY idx_user_reports_status_created (status, created_at)'
);

CALL moodcopilot_add_fk_if_missing(
    'user_reports',
    'fk_user_reports_handler',
    'ALTER TABLE user_reports ADD CONSTRAINT fk_user_reports_handler FOREIGN KEY (handled_by_user_id) REFERENCES users(id) ON DELETE SET NULL'
);

DROP PROCEDURE moodcopilot_add_fk_if_missing;
DROP PROCEDURE moodcopilot_add_index_if_missing;
DROP PROCEDURE moodcopilot_add_column_if_missing;
