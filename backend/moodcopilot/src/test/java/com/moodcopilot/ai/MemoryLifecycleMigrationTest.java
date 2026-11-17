package com.moodcopilot.ai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 在 H2（MySQL 模式）上执行真实的 V1_64 迁移，验证默认值与回填。 */
class MemoryLifecycleMigrationTest {

    private DriverManagerDataSource database;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        database = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(database);
        // 只建迁移会触碰到的列，模拟迁移前的历史表。
        jdbc.execute("CREATE TABLE user_profile_memory (id BIGINT PRIMARY KEY, user_id BIGINT, "
                + "attribute_key VARCHAR(64), status VARCHAR(32), last_evidence_at TIMESTAMP(3), updated_at TIMESTAMP(3))");
        jdbc.execute("CREATE TABLE diaries (id BIGINT PRIMARY KEY, author_user_id BIGINT, created_at TIMESTAMP)");
        jdbc.update("INSERT INTO user_profile_memory(id,user_id,attribute_key,status,last_evidence_at,updated_at) "
                + "VALUES (1,7,'喜欢咖啡','active','2026-01-02 03:04:05','2026-02-02 00:00:00')");
        jdbc.update("INSERT INTO user_profile_memory(id,user_id,attribute_key,status,last_evidence_at,updated_at) "
                + "VALUES (2,7,'爱跑步','active',NULL,'2026-03-02 00:00:00')");
    }

    @AfterEach
    void cleanup() {
        jdbc.execute("DROP ALL OBJECTS");
    }

    private void migrate() {
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1_64__add_memory_lifecycle.sql"))
                .execute(database);
    }

    @Test
    void addsLifecycleColumnsWithSafeDefaults() {
        migrate();
        assertEquals("active", jdbc.queryForObject(
                "SELECT lifecycle_state FROM user_profile_memory WHERE id = 1", String.class));
        BigDecimal freshness = jdbc.queryForObject(
                "SELECT freshness FROM user_profile_memory WHERE id = 1", BigDecimal.class);
        assertNotNull(freshness);
        assertEquals(0, new BigDecimal("1.0000").compareTo(freshness), "已有记忆的新鲜度应为 1");
    }

    @Test
    void backfillsLifecycleUpdatedAtFromEvidenceThenUpdatedAt() {
        migrate();
        Timestamp fromEvidence = jdbc.queryForObject(
                "SELECT lifecycle_updated_at FROM user_profile_memory WHERE id = 1", Timestamp.class);
        Timestamp fromUpdatedAt = jdbc.queryForObject(
                "SELECT lifecycle_updated_at FROM user_profile_memory WHERE id = 2", Timestamp.class);
        assertEquals(Timestamp.valueOf("2026-01-02 03:04:05"), fromEvidence, "优先使用证据时间");
        assertEquals(Timestamp.valueOf("2026-03-02 00:00:00"), fromUpdatedAt, "没有证据时间时退回写入时间");
    }

    @Test
    void createsLifecycleIndexes() {
        migrate();
        Integer indexes = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.indexes WHERE LOWER(index_name) = 'idx_memory_lifecycle_scan'",
                Integer.class);
        assertNotNull(indexes);
        assertEquals(1, indexes, "应建立 lifecycle_state 扫描索引");
    }
}
