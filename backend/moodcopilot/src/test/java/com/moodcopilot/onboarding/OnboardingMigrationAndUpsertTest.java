package com.moodcopilot.onboarding;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.moodcopilot.mapper.UserOnboardingMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class OnboardingMigrationAndUpsertTest {

    private DriverManagerDataSource database;
    private JdbcTemplate jdbc;
    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setup() {
        database = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(database);

        jdbc.execute("""
                CREATE TABLE users (
                    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                    display_name VARCHAR(64) NOT NULL,
                    email VARCHAR(191) NULL,
                    status TINYINT UNSIGNED NOT NULL DEFAULT 1,
                    PRIMARY KEY (id)
                ) ENGINE=InnoDB
                """);
        jdbc.execute("""
                CREATE TABLE diaries (
                    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                    author_user_id BIGINT UNSIGNED NOT NULL,
                    content TEXT NOT NULL,
                    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
                    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    PRIMARY KEY (id)
                ) ENGINE=InnoDB
                """);

        // Pre-existing user before V1_66 migration
        jdbc.update("INSERT INTO users (id, display_name, email, status) VALUES (1, 'legacy_user', 'legacy@test.com', 1)");
        jdbc.update("INSERT INTO users (id, display_name, email, status) VALUES (2, 'new_user', 'new@test.com', 1)");

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(UserOnboardingMapper.class);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), database));
        sqlSessionFactory = new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    @AfterEach
    void cleanup() {
        jdbc.execute("DROP ALL OBJECTS");
    }

    private void migrate() {
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1_66__add_user_onboarding.sql"))
                .execute(database);
    }

    @Test
    void migrationPreservesExistingUsersAtZeroAndCreatesTableAndConstraints() {
        migrate();

        Integer legacyEnrolled = jdbc.queryForObject(
                "SELECT onboarding_enrolled_version FROM users WHERE id = 1", Integer.class);
        assertEquals(0, legacyEnrolled, "Existing users must default to onboarding_enrolled_version = 0");

        jdbc.update("INSERT INTO users (id, display_name, email, status, onboarding_enrolled_version) "
                + "VALUES (3, 'enrolled_user', 'enrolled@test.com', 1, 1)");
        assertEquals(1, jdbc.queryForObject(
                "SELECT onboarding_enrolled_version FROM users WHERE id = 3", Integer.class));

        Integer userIdx = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.indexes WHERE LOWER(index_name) = 'idx_user_onboarding_user'",
                Integer.class);
        assertEquals(1, userIdx);

        // Verify FK ON DELETE CASCADE
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            UserOnboardingMapper mapper = session.getMapper(UserOnboardingMapper.class);
            LocalDateTime now = LocalDateTime.of(2026, 11, 26, 10, 0, 0);
            mapper.upsertMonotonic(3L, "WELCOME_V1", 1, "GLOBAL", "COMPLETED", now, now);
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM user_onboarding WHERE user_id = 3", Integer.class));
        jdbc.update("DELETE FROM users WHERE id = 3");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user_onboarding WHERE user_id = 3", Integer.class));
    }

    @Test
    void upsertMonotonicEnforcesStrictNonDowngradeAndPreservesEarliestCompletedAt() {
        migrate();

        LocalDateTime t1 = LocalDateTime.of(2026, 11, 26, 10, 0, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 11, 26, 10, 5, 0);
        LocalDateTime t3 = LocalDateTime.of(2026, 11, 26, 10, 10, 0);
        LocalDateTime t0 = LocalDateTime.of(2026, 11, 26, 9, 30, 0);

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            UserOnboardingMapper mapper = session.getMapper(UserOnboardingMapper.class);

            // IN_PROGRESS -> SKIPPED (updated_at advances)
            mapper.upsertMonotonic(1L, "WELCOME_V1", 1, "GLOBAL", "IN_PROGRESS", null, t1);
            assertEquals("IN_PROGRESS", queryStatus(1L, "WELCOME_V1"));
            assertEquals(Timestamp.valueOf(t1), queryUpdatedAt(1L, "WELCOME_V1"));

            mapper.upsertMonotonic(1L, "WELCOME_V1", 1, "GLOBAL", "SKIPPED", null, t2);
            assertEquals("SKIPPED", queryStatus(1L, "WELCOME_V1"));
            assertEquals(Timestamp.valueOf(t2), queryUpdatedAt(1L, "WELCOME_V1"));

            // SKIPPED must NOT regress to IN_PROGRESS (updated_at stays unchanged)
            mapper.upsertMonotonic(1L, "WELCOME_V1", 1, "GLOBAL", "IN_PROGRESS", null, t3);
            assertEquals("SKIPPED", queryStatus(1L, "WELCOME_V1"));
            assertEquals(Timestamp.valueOf(t2), queryUpdatedAt(1L, "WELCOME_V1"));

            // SKIPPED -> COMPLETED (updated_at advances)
            LocalDateTime t2Plus = LocalDateTime.of(2026, 11, 26, 10, 7, 0);
            mapper.upsertMonotonic(1L, "WELCOME_V1", 1, "GLOBAL", "COMPLETED", t2Plus, t2Plus);
            assertEquals("COMPLETED", queryStatus(1L, "WELCOME_V1"));
            assertEquals(Timestamp.valueOf(t2Plus), queryCompletedAt(1L, "WELCOME_V1"));
            assertEquals(Timestamp.valueOf(t2Plus), queryUpdatedAt(1L, "WELCOME_V1"));

            // COMPLETED must NOT regress to SKIPPED or IN_PROGRESS (updated_at & completed_at unchanged)
            mapper.upsertMonotonic(1L, "WELCOME_V1", 1, "GLOBAL", "SKIPPED", null, t3);
            assertEquals("COMPLETED", queryStatus(1L, "WELCOME_V1"));
            assertEquals(Timestamp.valueOf(t2Plus), queryCompletedAt(1L, "WELCOME_V1"));
            assertEquals(Timestamp.valueOf(t2Plus), queryUpdatedAt(1L, "WELCOME_V1"));

            mapper.upsertMonotonic(1L, "WELCOME_V1", 1, "GLOBAL", "IN_PROGRESS", null, t3);
            assertEquals("COMPLETED", queryStatus(1L, "WELCOME_V1"));
            assertEquals(Timestamp.valueOf(t2Plus), queryUpdatedAt(1L, "WELCOME_V1"));

            // Duplicate COMPLETED with later timestamp keeps original completed_at and updated_at
            mapper.upsertMonotonic(1L, "WELCOME_V1", 1, "GLOBAL", "COMPLETED", t3, t3);
            assertEquals(Timestamp.valueOf(t2Plus), queryCompletedAt(1L, "WELCOME_V1"));
            assertEquals(Timestamp.valueOf(t2Plus), queryUpdatedAt(1L, "WELCOME_V1"));

            // Merge COMPLETED with earlier timestamp preserves earliest completed_at
            mapper.upsertMonotonic(1L, "WELCOME_V1", 1, "GLOBAL", "COMPLETED", t0, t3);
            assertEquals(Timestamp.valueOf(t0), queryCompletedAt(1L, "WELCOME_V1"));
        }
    }

    @Test
    void concurrentUpsertsProduceSingleRowWithHighestMonotonicStatus() throws Exception {
        migrate();

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        LocalDateTime now = LocalDateTime.of(2026, 11, 26, 12, 0, 0);
        String[] statuses = { "IN_PROGRESS", "SKIPPED", "COMPLETED", "IN_PROGRESS", "SKIPPED", "COMPLETED", "IN_PROGRESS", "SKIPPED" };

        try {
            for (int i = 0; i < threads; i++) {
                final String status = statuses[i];
                final LocalDateTime completedAt = "COMPLETED".equals(status) ? now : null;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    try (SqlSession session = sqlSessionFactory.openSession(true)) {
                        UserOnboardingMapper mapper = session.getMapper(UserOnboardingMapper.class);
                        mapper.upsertMonotonic(2L, "FIRST_DIARY", 1, "GLOBAL", status, completedAt, now);
                    }
                }));
            }
            ready.await();
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }

        Integer rowCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_onboarding WHERE user_id = 2 AND guide_key = 'FIRST_DIARY'",
                Integer.class);
        assertEquals(1, rowCount);
        assertEquals("COMPLETED", queryStatus(2L, "FIRST_DIARY"));
        assertNotNull(queryCompletedAt(2L, "FIRST_DIARY"));
    }

    @Test
    void existsActiveDiaryForUserRespectsOwnershipAndSoftDelete() {
        migrate();

        jdbc.update("INSERT INTO diaries (author_user_id, content, is_deleted) VALUES (1, 'deleted diary', 1)");
        jdbc.update("INSERT INTO diaries (author_user_id, content, is_deleted) VALUES (2, 'other user diary', 0)");

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            UserOnboardingMapper mapper = session.getMapper(UserOnboardingMapper.class);
            assertNull(mapper.existsActiveDiaryForUser(1L));
            assertEquals(1, mapper.existsActiveDiaryForUser(2L));

            jdbc.update("INSERT INTO diaries (author_user_id, content, is_deleted) VALUES (1, 'active diary', 0)");
            session.clearCache();
            assertEquals(1, mapper.existsActiveDiaryForUser(1L));
        }
    }

    private String queryStatus(Long userId, String guideKey) {
        return jdbc.queryForObject(
                "SELECT status FROM user_onboarding WHERE user_id = ? AND guide_key = ? AND guide_version = 1 AND scope = 'GLOBAL'",
                String.class, userId, guideKey);
    }

    private Timestamp queryCompletedAt(Long userId, String guideKey) {
        return jdbc.queryForObject(
                "SELECT completed_at FROM user_onboarding WHERE user_id = ? AND guide_key = ? AND guide_version = 1 AND scope = 'GLOBAL'",
                Timestamp.class, userId, guideKey);
    }

    private Timestamp queryUpdatedAt(Long userId, String guideKey) {
        return jdbc.queryForObject(
                "SELECT updated_at FROM user_onboarding WHERE user_id = ? AND guide_key = ? AND guide_version = 1 AND scope = 'GLOBAL'",
                Timestamp.class, userId, guideKey);
    }
}
