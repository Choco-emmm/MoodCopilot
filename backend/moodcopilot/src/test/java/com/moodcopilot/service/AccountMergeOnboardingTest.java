package com.moodcopilot.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.mapper.*;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountMergeOnboardingTest {

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
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1_66__add_user_onboarding.sql"))
                .execute(database);

        jdbc.update("INSERT INTO users (id, display_name, email, status, onboarding_enrolled_version) "
                + "VALUES (10, 'wx_temp', 'temp@wx.com', 1, 1)");
        jdbc.update("INSERT INTO users (id, display_name, email, status, onboarding_enrolled_version) "
                + "VALUES (20, 'legacy_email', 'legacy@example.com', 1, 0)");

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(UserOnboardingMapper.class);
        configuration.addMapper(ChatConversationMapper.class);
        configuration.addMapper(DiaryCollectionMapper.class);
        configuration.addMapper(DiaryCommentMapper.class);
        configuration.addMapper(DiaryHideMapper.class);
        configuration.addMapper(DiaryKnowledgeGraphMapper.class);
        configuration.addMapper(DiaryMapper.class);
        configuration.addMapper(DiaryRecommendationExposureMapper.class);
        configuration.addMapper(DiaryResonanceMapper.class);
        configuration.addMapper(DiarySummaryMapper.class);
        configuration.addMapper(FollowMapper.class);
        configuration.addMapper(NotificationMapper.class);
        configuration.addMapper(SuggestionMapper.class);
        configuration.addMapper(SuggestionReplyMapper.class);
        configuration.addMapper(UserProfileMemoryMapper.class);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), database));
        sqlSessionFactory = new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    @AfterEach
    void cleanup() {
        jdbc.execute("DROP ALL OBJECTS");
    }

    @Test
    void mergeAccountsMonotonicallyMergesOnboardingAndPreservesTargetEnrollment() {
        LocalDateTime earlier = LocalDateTime.of(2026, 11, 20, 8, 0, 0);
        LocalDateTime later = LocalDateTime.of(2026, 11, 25, 18, 0, 0);

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            UserOnboardingMapper realOnboardingMapper = session.getMapper(UserOnboardingMapper.class);

            // Source (10) rows:
            // - FIRST_DIARY COMPLETED earlier than target
            // - WELCOME_V1 IN_PROGRESS (must not downgrade target's SKIPPED)
            // - MEMORY_CENTER_VISITED COMPLETED (only on source -> transferred to target)
            // - FIRST_CHAT_REPLY SKIPPED (target is COMPLETED -> must stay COMPLETED)
            realOnboardingMapper.upsertMonotonic(10L, "FIRST_DIARY", 1, "GLOBAL", "COMPLETED", earlier, earlier);
            realOnboardingMapper.upsertMonotonic(10L, "WELCOME_V1", 1, "GLOBAL", "IN_PROGRESS", null, later);
            realOnboardingMapper.upsertMonotonic(10L, "MEMORY_CENTER_VISITED", 1, "GLOBAL", "COMPLETED", later, later);
            realOnboardingMapper.upsertMonotonic(10L, "FIRST_CHAT_REPLY", 1, "GLOBAL", "SKIPPED", null, later);

            // Target (20) rows:
            realOnboardingMapper.upsertMonotonic(20L, "FIRST_DIARY", 1, "GLOBAL", "COMPLETED", later, later);
            realOnboardingMapper.upsertMonotonic(20L, "WELCOME_V1", 1, "GLOBAL", "SKIPPED", null, earlier);
            realOnboardingMapper.upsertMonotonic(20L, "FIRST_CHAT_REPLY", 1, "GLOBAL", "COMPLETED", earlier, earlier);

            UserMapper userMapper = mock(UserMapper.class);
            UserEntity sourceUser = new UserEntity();
            sourceUser.setId(10L);
            sourceUser.setExp(5);
            sourceUser.setWxOpenId("wx-open-10");
            sourceUser.setOnboardingEnrolledVersion(1);

            UserEntity targetUser = new UserEntity();
            targetUser.setId(20L);
            targetUser.setExp(100);
            targetUser.setOnboardingEnrolledVersion(0);

            when(userMapper.selectById(10L)).thenReturn(sourceUser);
            when(userMapper.selectById(20L)).thenReturn(targetUser);

            AccountMergeService mergeService = new AccountMergeService();
            ReflectionTestUtils.setField(mergeService, "chatConversationMapper", mock(ChatConversationMapper.class));
            ReflectionTestUtils.setField(mergeService, "diaryCollectionMapper", mock(DiaryCollectionMapper.class));
            ReflectionTestUtils.setField(mergeService, "diaryCommentMapper", mock(DiaryCommentMapper.class));
            ReflectionTestUtils.setField(mergeService, "diaryHideMapper", mock(DiaryHideMapper.class));
            ReflectionTestUtils.setField(mergeService, "diaryKnowledgeGraphMapper", mock(DiaryKnowledgeGraphMapper.class));
            ReflectionTestUtils.setField(mergeService, "diaryMapper", mock(DiaryMapper.class));
            ReflectionTestUtils.setField(mergeService, "diaryRecommendationExposureMapper", mock(DiaryRecommendationExposureMapper.class));
            ReflectionTestUtils.setField(mergeService, "diaryResonanceMapper", mock(DiaryResonanceMapper.class));
            ReflectionTestUtils.setField(mergeService, "diarySummaryMapper", mock(DiarySummaryMapper.class));
            ReflectionTestUtils.setField(mergeService, "followMapper", mock(FollowMapper.class));
            ReflectionTestUtils.setField(mergeService, "notificationMapper", mock(NotificationMapper.class));
            ReflectionTestUtils.setField(mergeService, "suggestionMapper", mock(SuggestionMapper.class));
            ReflectionTestUtils.setField(mergeService, "suggestionReplyMapper", mock(SuggestionReplyMapper.class));
            ReflectionTestUtils.setField(mergeService, "userProfileMemoryMapper", mock(UserProfileMemoryMapper.class));
            ReflectionTestUtils.setField(mergeService, "userMapper", userMapper);
            ReflectionTestUtils.setField(mergeService, "userOnboardingMapper", realOnboardingMapper);

            mergeService.mergeAccounts(10L, 20L);

            verify(userMapper).deleteById(10L);
            verify(userMapper).updateById(targetUser);
            assertEquals(0, targetUser.getOnboardingEnrolledVersion(),
                    "Target existing user's onboardingEnrolledVersion must remain 0");
        }

        // Source user's onboarding rows are cleaned up
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_onboarding WHERE user_id = 10", Integer.class));

        // Target user has monotonically merged rows with earliest completed_at preserved
        assertEquals("COMPLETED", queryStatus(20L, "FIRST_DIARY"));
        assertEquals(Timestamp.valueOf(earlier), queryCompletedAt(20L, "FIRST_DIARY"));
        assertEquals("SKIPPED", queryStatus(20L, "WELCOME_V1"));
        assertEquals("COMPLETED", queryStatus(20L, "MEMORY_CENTER_VISITED"));
        assertEquals("COMPLETED", queryStatus(20L, "FIRST_CHAT_REPLY"));
        assertEquals(Timestamp.valueOf(earlier), queryCompletedAt(20L, "FIRST_CHAT_REPLY"));
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
}
