package com.moodcopilot.onboarding;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.moodcopilot.common.GlobalExceptionHandler;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.mapper.UserOnboardingMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminOnboardingMetricsTest {

    @Nested
    class ControllerAuthAndContractTests {

        private AdminOnboardingMetricsService metricsService;
        private MockMvc mockMvc;

        @BeforeEach
        void setup() {
            metricsService = mock(AdminOnboardingMetricsService.class);
            AdminOnboardingMetricsController controller = new AdminOnboardingMetricsController(metricsService);
            mockMvc = MockMvcBuilders.standaloneSetup(controller)
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                    .build();
        }

        @AfterEach
        void cleanup() {
            SecurityContextHolder.clearContext();
        }

        @Test
        void anonymousRequestReturns401() throws Exception {
            mockMvc.perform(get("/api/admin/onboarding/metrics"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(401));

            verifyNoInteractions(metricsService);
        }

        @Test
        void principalWithoutUserIdReturns401() throws Exception {
            UserEntity user = new UserEntity();
            user.setRole("ADMIN");
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(user, null, List.of()));

            mockMvc.perform(get("/api/admin/onboarding/metrics"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(401));

            verifyNoInteractions(metricsService);
        }

        @Test
        void nonAdminUserReturns403() throws Exception {
            authenticate(10L, "USER");
            mockMvc.perform(get("/api/admin/onboarding/metrics"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403));

            authenticate(11L, null);
            mockMvc.perform(get("/api/admin/onboarding/metrics"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403));

            verifyNoInteractions(metricsService);
        }

        @Test
        void invalidGuideVersionReturns400() throws Exception {
            authenticate(1L, "ADMIN");
            when(metricsService.getMetrics(0))
                    .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的引导版本"));
            when(metricsService.getMetrics(-1))
                    .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的引导版本"));
            when(metricsService.getMetrics(2))
                    .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的引导版本"));

            mockMvc.perform(get("/api/admin/onboarding/metrics").param("guideVersion", "0"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));

            mockMvc.perform(get("/api/admin/onboarding/metrics").param("guideVersion", "-1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));

            mockMvc.perform(get("/api/admin/onboarding/metrics").param("guideVersion", "2"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));

            mockMvc.perform(get("/api/admin/onboarding/metrics").param("guideVersion", "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        void adminRequestReturns200WithApiResponseCodeZero() throws Exception {
            authenticate(1L, "ADMIN");
            Map<String, Long> scopeBreakdown = new LinkedHashMap<>();
            scopeBreakdown.put("GLOBAL", 3L);
            scopeBreakdown.put("WEB", 2L);
            scopeBreakdown.put("MINI_PROGRAM", 1L);

            AdminOnboardingMetricsView sample = new AdminOnboardingMetricsView(
                    1,
                    4L,
                    1L,
                    1L,
                    0.25,
                    3L,
                    0.75,
                    2L,
                    2L,
                    0.5,
                    2L,
                    2L,
                    1L,
                    0.25,
                    1L,
                    scopeBreakdown
            );
            when(metricsService.getMetrics(null)).thenReturn(sample);
            when(metricsService.getMetrics(1)).thenReturn(sample);

            mockMvc.perform(get("/api/admin/onboarding/metrics"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.message").value("ok"))
                    .andExpect(jsonPath("$.data.guideVersion").value(1))
                    .andExpect(jsonPath("$.data.eligibleUserCount").value(4))
                    .andExpect(jsonPath("$.data.welcomeCompletedCount").value(1))
                    .andExpect(jsonPath("$.data.welcomeSkippedCount").value(1))
                    .andExpect(jsonPath("$.data.welcomeSkipRate").value(0.25))
                    .andExpect(jsonPath("$.data.firstDiaryCompletedCount").value(3))
                    .andExpect(jsonPath("$.data.firstDiaryCompletionRate").value(0.75))
                    .andExpect(jsonPath("$.data.firstAnalysisViewedCount").value(2))
                    .andExpect(jsonPath("$.data.firstChatReplyCompletedCount").value(2))
                    .andExpect(jsonPath("$.data.firstChatSuccessRate").value(0.5))
                    .andExpect(jsonPath("$.data.memoryCenterVisitedCount").value(2))
                    .andExpect(jsonPath("$.data.themeExploredCount").value(2))
                    .andExpect(jsonPath("$.data.allCoreCompletedCount").value(1))
                    .andExpect(jsonPath("$.data.coreCompletionRate").value(0.25))
                    .andExpect(jsonPath("$.data.crossPlatformHintUserCount").value(1))
                    .andExpect(jsonPath("$.data.scopeBreakdown.GLOBAL").value(3))
                    .andExpect(jsonPath("$.data.scopeBreakdown.WEB").value(2))
                    .andExpect(jsonPath("$.data.scopeBreakdown.MINI_PROGRAM").value(1));

            authenticate(2L, "ROLE_ADMIN");
            mockMvc.perform(get("/api/admin/onboarding/metrics").param("guideVersion", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.eligibleUserCount").value(4));

            verify(metricsService).getMetrics(null);
            verify(metricsService).getMetrics(1);
        }

        private void authenticate(Long userId, String role) {
            UserEntity user = new UserEntity();
            user.setId(userId);
            user.setRole(role);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(user, null, List.of()));
        }
    }

    @Nested
    class H2MySqlIntegrationTests {

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
                        role VARCHAR(32) NOT NULL DEFAULT 'USER',
                        status TINYINT UNSIGNED NOT NULL DEFAULT 1,
                        PRIMARY KEY (id)
                    ) ENGINE=InnoDB
                    """);

            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1_66__add_user_onboarding.sql"))
                    .execute(database);

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

        @Test
        void zeroEligibleUsersReturnsZeroCountsAndRatesEvenWhenLegacyUsersHaveRows() {
            // Pre-existing users with onboarding_enrolled_version = 0
            insertUser(1L, "legacy_a", 0);
            insertUser(2L, "legacy_b", 0);

            LocalDateTime now = LocalDateTime.of(2026, 11, 26, 12, 0, 0);
            try (SqlSession session = sqlSessionFactory.openSession(true)) {
                UserOnboardingMapper mapper = session.getMapper(UserOnboardingMapper.class);
                mapper.upsertMonotonic(1L, "WELCOME_V1", 1, "GLOBAL", "COMPLETED", now, now);
                mapper.upsertMonotonic(1L, "FIRST_DIARY", 1, "GLOBAL", "COMPLETED", now, now);
                mapper.upsertMonotonic(1L, "FIRST_ANALYSIS_VIEWED", 1, "GLOBAL", "COMPLETED", now, now);
                mapper.upsertMonotonic(1L, "FIRST_CHAT_REPLY", 1, "GLOBAL", "COMPLETED", now, now);
                mapper.upsertMonotonic(1L, "MEMORY_CENTER_VISITED", 1, "GLOBAL", "COMPLETED", now, now);
                mapper.upsertMonotonic(1L, "THEME_EXPLORED", 1, "GLOBAL", "COMPLETED", now, now);
                mapper.upsertMonotonic(1L, "PUBLIC_DIARY_WEB", 1, "WEB", "COMPLETED", now, now);
                mapper.upsertMonotonic(1L, "MINI_HINT_DEMO", 1, "MINI_PROGRAM", "COMPLETED", now, now);
                mapper.upsertMonotonic(2L, "WELCOME_V1", 1, "GLOBAL", "SKIPPED", null, now);

                AdminOnboardingMetricsService service = new AdminOnboardingMetricsService(mapper);
                AdminOnboardingMetricsView metrics = service.getMetrics(null);

                assertEquals(1, metrics.guideVersion());
                assertEquals(0L, metrics.eligibleUserCount());
                assertEquals(0L, metrics.welcomeCompletedCount());
                assertEquals(0L, metrics.welcomeSkippedCount());
                assertEquals(0.0, metrics.welcomeSkipRate());
                assertEquals(0L, metrics.firstDiaryCompletedCount());
                assertEquals(0.0, metrics.firstDiaryCompletionRate());
                assertEquals(0L, metrics.firstAnalysisViewedCount());
                assertEquals(0L, metrics.firstChatReplyCompletedCount());
                assertEquals(0.0, metrics.firstChatSuccessRate());
                assertEquals(0L, metrics.memoryCenterVisitedCount());
                assertEquals(0L, metrics.themeExploredCount());
                assertEquals(0L, metrics.allCoreCompletedCount());
                assertEquals(0.0, metrics.coreCompletionRate());
                assertEquals(0L, metrics.crossPlatformHintUserCount());
                assertEquals(Map.of("GLOBAL", 0L, "WEB", 0L, "MINI_PROGRAM", 0L), metrics.scopeBreakdown());
            }
        }

        @Test
        void rejectsInvalidGuideVersionInService() {
            try (SqlSession session = sqlSessionFactory.openSession(true)) {
                AdminOnboardingMetricsService service = new AdminOnboardingMetricsService(
                        session.getMapper(UserOnboardingMapper.class));

                ResponseStatusException zeroEx = assertThrows(ResponseStatusException.class, () -> service.getMetrics(0));
                assertEquals(HttpStatus.BAD_REQUEST, zeroEx.getStatusCode());

                ResponseStatusException negEx = assertThrows(ResponseStatusException.class, () -> service.getMetrics(-3));
                assertEquals(HttpStatus.BAD_REQUEST, negEx.getStatusCode());

                ResponseStatusException futureEx = assertThrows(ResponseStatusException.class, () -> service.getMetrics(2));
                assertEquals(HttpStatus.BAD_REQUEST, futureEx.getStatusCode());
            }
        }

        @Test
        void aggregatesMixedEligibleUsersAndExcludesLegacyUsersAndOtherVersions() {
            // Legacy user (enrolled = 0) — must be completely excluded
            insertUser(1L, "legacy_complete", 0);
            // Eligible users (enrolled >= 1)
            insertUser(2L, "eligible_all_core", 1);
            insertUser(3L, "eligible_skipped_partial", 1);
            insertUser(4L, "eligible_three_core_plus_theme", 1);
            insertUser(5L, "eligible_no_rows_v2", 2);

            LocalDateTime now = LocalDateTime.of(2026, 11, 26, 14, 0, 0);
            try (SqlSession session = sqlSessionFactory.openSession(true)) {
                UserOnboardingMapper mapper = session.getMapper(UserOnboardingMapper.class);

                // Legacy user 1: completed everything across all scopes — MUST NOT count
                mapper.upsertMonotonic(1L, "WELCOME_V1", 1, "GLOBAL", "COMPLETED", now, now);
                mapper.upsertMonotonic(1L, "FIRST_DIARY", 1, "GLOBAL", "COMPLETED", now, now);
                mapper.upsertMonotonic(1L, "FIRST_ANALYSIS_VIEWED", 1, "GLOBAL", "COMPLETED", now, now);
                mapper.upsertMonotonic(1L, "FIRST_CHAT_REPLY", 1, "GLOBAL", "COMPLETED", now, now);
                mapper.upsertMonotonic(1L, "MEMORY_CENTER_VISITED", 1, "GLOBAL", "COMPLETED", now, now);
                mapper.upsertMonotonic(1L, "THEME_EXPLORED", 1, "GLOBAL", "COMPLETED", now, now);
                mapper.upsertMonotonic(1L, "PUBLIC_DIARY_WEB", 1, "WEB", "COMPLETED", now, now);
                mapper.upsertMonotonic(1L, "MINI_HINT_DEMO", 1, "MINI_PROGRAM", "COMPLETED", now, now);

                // Eligible user 2: welcome COMPLETED, all 4 core COMPLETED, theme COMPLETED, WEB + MINI_PROGRAM hints
                mapper.upsertMonotonic(2L, "WELCOME_V1", 1, "GLOBAL", "COMPLETED", now, now);
                mapper.upsertMonotonic(2L, "FIRST_DIARY", 1, "GLOBAL", "COMPLETED", now, now);
                mapper.upsertMonotonic(2L, "FIRST_ANALYSIS_VIEWED", 1, "GLOBAL", "COMPLETED", now, now);
                mapper.upsertMonotonic(2L, "FIRST_CHAT_REPLY", 1, "GLOBAL", "COMPLETED", now, now);
                mapper.upsertMonotonic(2L, "MEMORY_CENTER_VISITED", 1, "GLOBAL", "COMPLETED", now, now);
                mapper.upsertMonotonic(2L, "THEME_EXPLORED", 1, "GLOBAL", "COMPLETED", now, now);
                mapper.upsertMonotonic(2L, "PUBLIC_DIARY_WEB", 1, "WEB", "COMPLETED", now, now);
                mapper.upsertMonotonic(2L, "TOOL_APPROVAL_WEB", 1, "WEB", "SKIPPED", null, now);
                mapper.upsertMonotonic(2L, "MINI_HINT_DEMO", 1, "MINI_PROGRAM", "COMPLETED", now, now);

                // Eligible user 3: welcome SKIPPED, FIRST_DIARY + FIRST_CHAT_REPLY COMPLETED, others non-completed, WEB only
                mapper.upsertMonotonic(3L, "WELCOME_V1", 1, "GLOBAL", "SKIPPED", null, now);
                mapper.upsertMonotonic(3L, "FIRST_DIARY", 1, "GLOBAL", "COMPLETED", now, now);
                mapper.upsertMonotonic(3L, "FIRST_ANALYSIS_VIEWED", 1, "GLOBAL", "SKIPPED", null, now);
                mapper.upsertMonotonic(3L, "FIRST_CHAT_REPLY", 1, "GLOBAL", "COMPLETED", now, now);
                mapper.upsertMonotonic(3L, "MEMORY_CENTER_VISITED", 1, "GLOBAL", "IN_PROGRESS", null, now);
                mapper.upsertMonotonic(3L, "PUBLIC_DIARY_WEB", 1, "WEB", "SKIPPED", null, now);

                // Eligible user 4: welcome IN_PROGRESS, 3 of 4 core COMPLETED + THEME_EXPLORED COMPLETED (missing chat)
                mapper.upsertMonotonic(4L, "WELCOME_V1", 1, "GLOBAL", "IN_PROGRESS", null, now);
                mapper.upsertMonotonic(4L, "FIRST_DIARY", 1, "GLOBAL", "COMPLETED", now, now);
                mapper.upsertMonotonic(4L, "FIRST_ANALYSIS_VIEWED", 1, "GLOBAL", "COMPLETED", now, now);
                mapper.upsertMonotonic(4L, "MEMORY_CENTER_VISITED", 1, "GLOBAL", "COMPLETED", now, now);
                mapper.upsertMonotonic(4L, "THEME_EXPLORED", 1, "GLOBAL", "COMPLETED", now, now);

                // Eligible user 5: no v1 rows, only a hypothetical v2 row — must count in eligibleUserCount, not v1 numerators
                mapper.upsertMonotonic(5L, "WELCOME_V1", 2, "GLOBAL", "COMPLETED", now, now);
                mapper.upsertMonotonic(5L, "FIRST_DIARY", 2, "GLOBAL", "COMPLETED", now, now);

                AdminOnboardingMetricsService service = new AdminOnboardingMetricsService(mapper);
                AdminOnboardingMetricsView metrics = service.getMetrics(1);

                assertEquals(1, metrics.guideVersion());
                assertEquals(4L, metrics.eligibleUserCount());
                assertEquals(1L, metrics.welcomeCompletedCount());
                assertEquals(1L, metrics.welcomeSkippedCount());
                assertEquals(0.25, metrics.welcomeSkipRate());
                assertEquals(3L, metrics.firstDiaryCompletedCount());
                assertEquals(0.75, metrics.firstDiaryCompletionRate());
                assertEquals(2L, metrics.firstAnalysisViewedCount());
                assertEquals(2L, metrics.firstChatReplyCompletedCount());
                assertEquals(0.5, metrics.firstChatSuccessRate());
                assertEquals(2L, metrics.memoryCenterVisitedCount());
                assertEquals(2L, metrics.themeExploredCount());
                assertEquals(1L, metrics.allCoreCompletedCount());
                assertEquals(0.25, metrics.coreCompletionRate());
                assertEquals(1L, metrics.crossPlatformHintUserCount());
                assertEquals(Map.of("GLOBAL", 3L, "WEB", 2L, "MINI_PROGRAM", 1L), metrics.scopeBreakdown());
            }
        }

        @Test
        void roundsRepeatingDecimalRatesCleanly() {
            insertUser(10L, "u1", 1);
            insertUser(11L, "u2", 1);
            insertUser(12L, "u3", 1);

            LocalDateTime now = LocalDateTime.of(2026, 11, 26, 15, 0, 0);
            try (SqlSession session = sqlSessionFactory.openSession(true)) {
                UserOnboardingMapper mapper = session.getMapper(UserOnboardingMapper.class);
                mapper.upsertMonotonic(10L, "WELCOME_V1", 1, "GLOBAL", "SKIPPED", null, now);
                mapper.upsertMonotonic(10L, "FIRST_DIARY", 1, "GLOBAL", "COMPLETED", now, now);
                mapper.upsertMonotonic(11L, "FIRST_DIARY", 1, "GLOBAL", "COMPLETED", now, now);

                AdminOnboardingMetricsService service = new AdminOnboardingMetricsService(mapper);
                AdminOnboardingMetricsView metrics = service.getMetrics(1);

                assertEquals(3L, metrics.eligibleUserCount());
                assertEquals(0.3333, metrics.welcomeSkipRate());
                assertEquals(0.6667, metrics.firstDiaryCompletionRate());
                assertEquals(0.0, metrics.firstChatSuccessRate());
                assertEquals(0.0, metrics.coreCompletionRate());
            }
        }

        private void insertUser(long id, String displayName, int enrolledVersion) {
            jdbc.update(
                    "INSERT INTO users (id, display_name, email, role, status, onboarding_enrolled_version) "
                            + "VALUES (?, ?, ?, 'USER', 1, ?)",
                    id, displayName, displayName + "@test.com", enrolledVersion);
        }
    }
}
