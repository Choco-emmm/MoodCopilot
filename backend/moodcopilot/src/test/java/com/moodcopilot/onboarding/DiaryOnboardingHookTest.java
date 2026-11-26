package com.moodcopilot.onboarding;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.ai.AiAnalysisService;
import com.moodcopilot.ai.MemoryExtractionService;
import com.moodcopilot.ai.RagMemoryService;
import com.moodcopilot.ai.VisionService;
import com.moodcopilot.ai.mq.AiTaskProducer;
import com.moodcopilot.diary.CreateDiaryRequest;
import com.moodcopilot.diary.DiaryCacheService;
import com.moodcopilot.diary.DiaryCreatedEvent;
import com.moodcopilot.diary.DiaryService;
import com.moodcopilot.entity.DiaryEntity;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.event.LifeChapterService;
import com.moodcopilot.event.LifeEventService;
import com.moodcopilot.follow.FollowService;
import com.moodcopilot.growth.UserGrowthService;
import com.moodcopilot.mapper.*;
import com.moodcopilot.music.MusicParseService;
import com.moodcopilot.notification.NotificationService;
import com.moodcopilot.oss.OssService;
import com.moodcopilot.security.RateLimitService;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DiaryOnboardingHookTest {

    private DriverManagerDataSource database;
    private JdbcTemplate jdbc;

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
                    PRIMARY KEY (id)
                ) ENGINE=InnoDB
                """);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1_66__add_user_onboarding.sql"))
                .execute(database);
        jdbc.update("INSERT INTO users (id, display_name, email, status, onboarding_enrolled_version) "
                + "VALUES (7, 'diary_user', 'diary@example.com', 1, 1)");
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        jdbc.execute("DROP ALL OBJECTS");
    }

    @Test
    void diaryServiceCreatePublishesDiaryCreatedEvent() {
        DiaryMapper diaryMapper = mock(DiaryMapper.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        doAnswer(invocation -> {
            DiaryEntity entity = invocation.getArgument(0);
            entity.setId(555L);
            return 1;
        }).when(diaryMapper).insert(any(DiaryEntity.class));

        DiaryService diaryService = new DiaryService(
                diaryMapper,
                mock(DiaryAnalysisMapper.class),
                mock(DiaryCommentMapper.class),
                mock(DiaryResonanceMapper.class),
                mock(DiaryHideMapper.class),
                mock(DiaryRecommendationExposureMapper.class),
                mock(UserMapper.class),
                mock(DiaryKnowledgeGraphMapper.class),
                mock(DiaryCollectionRelationMapper.class),
                mock(AiAnalysisService.class),
                mock(VisionService.class),
                mock(OssService.class),
                mock(MemoryExtractionService.class),
                mock(NotificationService.class),
                mock(FollowService.class),
                mock(DiarySummaryMapper.class),
                mock(StringRedisTemplate.class),
                new ObjectMapper(),
                eventPublisher,
                mock(RagMemoryService.class),
                mock(MusicParseService.class),
                mock(RateLimitService.class),
                mock(UserGrowthService.class),
                mock(TransactionTemplate.class),
                mock(AiTaskProducer.class),
                mock(DiaryCacheService.class),
                mock(LifeEventService.class),
                mock(LifeChapterService.class)
        );

        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setDisplayName("diary_user");
        user.setNickname("日记作者");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));

        diaryService.create(new CreateDiaryRequest(
                "今天认真记录了一篇日记，心情平静了很多。", "PRIVATE", null, List.of(), List.of(), false, false));

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(new DiaryCreatedEvent(7L, 555L), eventCaptor.getValue());
    }

    @Test
    void afterCommitListenerRecordsFirstDiaryOnCommitAndSkipsOnRollback() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(UserOnboardingMapper.class);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), database));
        SqlSessionFactory sqlSessionFactory = new MybatisSqlSessionFactoryBuilder().build(configuration);

        try (SqlSession session = sqlSessionFactory.openSession(true);
             AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            UserOnboardingMapper onboardingMapper = session.getMapper(UserOnboardingMapper.class);
            OnboardingService realOnboardingService = new OnboardingService(
                    onboardingMapper, mock(UserMapper.class), mock(DiaryMapper.class), mock(DiaryAnalysisMapper.class));

            context.registerBean(DataSource.class, () -> database);
            context.registerBean(OnboardingService.class, () -> realOnboardingService);
            context.register(TxTestConfig.class);
            context.refresh();

            TxDiaryPublisher publisher = context.getBean(TxDiaryPublisher.class);

            // 1) Rollback must NOT record FIRST_DIARY
            assertThrows(IllegalStateException.class, () -> publisher.createAndRollback(7L, "rolled back diary"));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM diaries WHERE author_user_id = 7", Integer.class));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM user_onboarding WHERE user_id = 7 AND guide_key = 'FIRST_DIARY'",
                    Integer.class));

            // 2) Commit triggers AFTER_COMMIT listener in REQUIRES_NEW and records FIRST_DIARY = COMPLETED
            publisher.createAndCommit(7L, "committed diary");
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM diaries WHERE author_user_id = 7", Integer.class));
            assertEquals("COMPLETED", jdbc.queryForObject(
                    "SELECT status FROM user_onboarding WHERE user_id = 7 AND guide_key = 'FIRST_DIARY' AND guide_version = 1 AND scope = 'GLOBAL'",
                    String.class));
        }
    }

    @Test
    void onboardingExceptionInAfterCommitListenerDoesNotFailOrRollbackDiaryCreation() {
        OnboardingService failingOnboarding = mock(OnboardingService.class);
        doThrow(new RuntimeException("onboarding db failure"))
                .when(failingOnboarding).recordBusinessTaskCompleted(7L, GuideKey.FIRST_DIARY);

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(DataSource.class, () -> database);
            context.registerBean(OnboardingService.class, () -> failingOnboarding);
            context.register(TxTestConfig.class);
            context.refresh();

            TxDiaryPublisher publisher = context.getBean(TxDiaryPublisher.class);
            publisher.createAndCommit(7L, "diary survives onboarding failure");

            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM diaries WHERE author_user_id = 7", Integer.class));
            verify(failingOnboarding).recordBusinessTaskCompleted(7L, GuideKey.FIRST_DIARY);
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class TxTestConfig {

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        OnboardingBusinessEventListener onboardingBusinessEventListener(OnboardingService onboardingService) {
            return new OnboardingBusinessEventListener(onboardingService);
        }

        @Bean
        TxDiaryPublisher txDiaryPublisher(JdbcTemplate jdbcTemplate, ApplicationEventPublisher eventPublisher) {
            return new TxDiaryPublisher(jdbcTemplate, eventPublisher);
        }
    }

    static class TxDiaryPublisher {
        private final JdbcTemplate jdbcTemplate;
        private final ApplicationEventPublisher eventPublisher;

        TxDiaryPublisher(JdbcTemplate jdbcTemplate, ApplicationEventPublisher eventPublisher) {
            this.jdbcTemplate = jdbcTemplate;
            this.eventPublisher = eventPublisher;
        }

        @Transactional
        public void createAndCommit(Long userId, String content) {
            jdbcTemplate.update("INSERT INTO diaries (author_user_id, content, is_deleted) VALUES (?, ?, 0)",
                    userId, content);
            eventPublisher.publishEvent(new DiaryCreatedEvent(userId, 1L));
        }

        @Transactional
        public void createAndRollback(Long userId, String content) {
            jdbcTemplate.update("INSERT INTO diaries (author_user_id, content, is_deleted) VALUES (?, ?, 0)",
                    userId, content);
            eventPublisher.publishEvent(new DiaryCreatedEvent(userId, 2L));
            throw new IllegalStateException("simulated failure");
        }
    }
}
