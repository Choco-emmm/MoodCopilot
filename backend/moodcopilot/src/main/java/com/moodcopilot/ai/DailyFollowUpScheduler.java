package com.moodcopilot.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodcopilot.common.RateLimitException;
import com.moodcopilot.diary.DiaryAnalysis;
import com.moodcopilot.entity.DiaryAnalysisEntity;
import com.moodcopilot.entity.DiaryEntity;
import com.moodcopilot.entity.UserLifeEventEntity;
import com.moodcopilot.event.LifeEventService;
import com.moodcopilot.mapper.DiaryAnalysisMapper;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.mapper.UserMapper;
import com.moodcopilot.notification.NotificationService;
import com.moodcopilot.security.RateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class DailyFollowUpScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyFollowUpScheduler.class);

    private static final String SENT_KEY_PREFIX = "dailyfu:sent:";
    private static final Duration SENT_TTL = Duration.ofHours(26);
    private static final DefaultRedisScript<Long> RELEASE_DAILY_CLAIM = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);

    private final UserMapper userMapper;
    private final DiaryMapper diaryMapper;
    private final DiaryAnalysisMapper diaryAnalysisMapper;
    private final AiAnalysisService aiAnalysisService;
    private final NotificationService notificationService;
    private final RateLimitService rateLimitService;
    private final StringRedisTemplate redisTemplate;
    private final LifeEventService lifeEventService;
    private final ZoneId eventTimeZone;

    @org.springframework.beans.factory.annotation.Autowired
    public DailyFollowUpScheduler(UserMapper userMapper,
            DiaryMapper diaryMapper,
            DiaryAnalysisMapper diaryAnalysisMapper,
            AiAnalysisService aiAnalysisService,
            NotificationService notificationService,
            RateLimitService rateLimitService,
            StringRedisTemplate redisTemplate,
            LifeEventService lifeEventService,
            @org.springframework.beans.factory.annotation.Value("${moodcopilot.time-zone:Asia/Shanghai}") String timeZoneId) {
        this.userMapper = userMapper;
        this.diaryMapper = diaryMapper;
        this.diaryAnalysisMapper = diaryAnalysisMapper;
        this.aiAnalysisService = aiAnalysisService;
        this.notificationService = notificationService;
        this.rateLimitService = rateLimitService;
        this.redisTemplate = redisTemplate;
        this.lifeEventService = lifeEventService;
        this.eventTimeZone = parseTimeZone(timeZoneId);
    }

    /** 兼容不需要事件回访的旧单元测试构造方式。 */
    public DailyFollowUpScheduler(UserMapper userMapper,
            DiaryMapper diaryMapper,
            DiaryAnalysisMapper diaryAnalysisMapper,
            AiAnalysisService aiAnalysisService,
            NotificationService notificationService,
            RateLimitService rateLimitService,
            StringRedisTemplate redisTemplate) {
        this(userMapper, diaryMapper, diaryAnalysisMapper, aiAnalysisService, notificationService,
                rateLimitService, redisTemplate, null, "Asia/Shanghai");
    }

    /**
     * 每小时触发一次，打散用户通知时间。
     * 每个用户每天只在偏好时间收到一次通知，避免一刀切的早上 6 点推送。
     */
    @Scheduled(cron = "0 0 * * * *")
    public void sendDailyFollowUp() {
        LocalDateTime now = LocalDateTime.now(eventTimeZone);
        int currentHour = now.getHour();
        log.info("每日跟进定时任务触发，currentHour={}", currentHour);

        LinkedHashSet<Long> userIdSet = new LinkedHashSet<>(userMapper.findActiveUsersWithDiariesYesterday());
        userIdSet.addAll(userMapper.findUsersWithDueLifeEvents(now));
        List<Long> userIds = new ArrayList<>(userIdSet);
        if (userIds.isEmpty()) {
            log.info("没有需要发送每日通知的用户");
            return;
        }

        String today = now.toLocalDate().toString();
        int sent = 0;
        int skipped = 0;
        for (Long userId : userIds) {
            String dailyClaim = null;
            boolean notificationPersisted = false;
            try {
                // 事件回访和普通陪伴共用每日闸门，保证同一天最多一条系统主动消息。
                dailyClaim = claimDailySend(userId, today);
                if (dailyClaim == null) {
                    continue;
                }
                Optional<UserLifeEventEntity> event = lifeEventService == null
                        ? Optional.empty() : lifeEventService.getPendingEventForFollowUp(userId);
                if (event.isPresent()) {
                    UserLifeEventEntity pending = event.get();
                    LocalDateTime scheduledAt = pending.getNextFollowUpAt();
                    if (scheduledAt == null) scheduledAt = now;
                    if (lifeEventService.claimFollowUp(userId, pending.getId(), scheduledAt)) {
                        String reason = pending.getFollowUpReason();
                        String message = "我想起你提到的「" + pending.getTitle() + "" +
                                "」，现在方便和我聊聊近况吗？" +
                                (reason == null || reason.isBlank() ? "" : "\n" + reason);
                        notificationPersisted = notificationService.notifyDailyFollowUp(userId, pending.getId(), message);
                        if (!notificationPersisted) {
                            lifeEventService.releaseFollowUpClaim(pending.getId(), scheduledAt);
                            log.warn("事件回访通知未落库，保留回访计划，userId={}，eventId={}", userId, pending.getId());
                            continue;
                        }
                        boolean recorded = lifeEventService.recordFollowUpSent(userId, pending.getId(), scheduledAt);
                        if (!recorded) {
                            log.info("事件回访通知已落库但计划已变化，未推进旧回访状态，userId={}，eventId={}", userId, pending.getId());
                        }
                        sent++;
                        log.info("事件回访通知已发送，userId={}，eventId={}，scheduledAt={}", userId, pending.getId(), scheduledAt);
                    }
                    continue;
                }
                if (!isPreferredHour(userId, currentHour)) {
                    continue;
                }
                try {
                    rateLimitService.tryAcquire(userId, RateLimitService.AiApiType.DIARY_FLASH);
                } catch (RateLimitException e) {
                    skipped++;
                    continue;
                }

                List<DiaryEntity> recent = diaryMapper.selectList(
                        new LambdaQueryWrapper<DiaryEntity>()
                                .eq(DiaryEntity::getAuthorUserId, userId)
                                .orderByDesc(DiaryEntity::getCreatedAt)
                                .last("LIMIT 7"));

                List<Long> ids = recent.stream().map(DiaryEntity::getId).toList();
                List<DiaryAnalysisEntity> analysisEntities = ids.isEmpty()
                        ? List.of()
                        : diaryAnalysisMapper.selectBatchIds(ids);
                Map<Long, DiaryAnalysisEntity> analysisMap = analysisEntities.stream()
                        .collect(java.util.stream.Collectors.toMap(
                                DiaryAnalysisEntity::getDiaryId,
                                analysis -> analysis,
                                (left, right) -> left,
                                LinkedHashMap::new));

                List<AiAnalysisService.DiaryEntryContext> contents = new ArrayList<>();
                List<DiaryAnalysis> analyses = new ArrayList<>();
                for (DiaryEntity d : recent) {
                    contents.add(new AiAnalysisService.DiaryEntryContext(d.getCreatedAt().toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("MM-dd")), d.getContent()));
                    DiaryAnalysisEntity a = analysisMap.get(d.getId());
                    if (a != null) {
                        analyses.add(new DiaryAnalysis(a.getMoodLabel(), a.getMoodIntensity(),
                                a.getTopicLabelsJson(),
                                a.getSecondaryMoodsJson() != null ? a.getSecondaryMoodsJson() : List.of(),
                                a.getSummary(), a.getFeedback()));
                    } else {
                        analyses.add(null);
                    }
                }

                int streak = calcStreak(userId);

                String yesterdayMood = analyses.isEmpty() || analyses.get(0) == null
                        ? "复杂"
                        : analyses.get(0).moodLabel();

                String coaching = aiAnalysisService.generateCoaching(userId, contents, analyses);

                String greeting = greetingByHour(currentHour);
                String message = String.format(
                        "%s！已连续记录 %d 天，昨天是「%s」。\n\n%s", greeting, streak, yesterdayMood, coaching);

                notificationPersisted = notificationService.notifyDailyFollowUp(userId, message);
                if (!notificationPersisted) {
                    log.warn("每日陪伴通知未落库，userId={}，允许后续重试", userId);
                    continue;
                }
                sent++;
            } catch (Exception e) {
                log.warn("用户 {} 每日通知生成失败: {}", userId, e.getMessage());
            } finally {
                if (!notificationPersisted && dailyClaim != null) {
                    releaseDailyClaim(userId, today, dailyClaim);
                }
            }
        }
        log.info("每日跟进通知完成: 发送 {} 条, 额度不足跳过 {} 人", sent, skipped);
    }

    /**
     * 判断当前小时是否为该用户的偏好通知时间。
     * 使用 userId 哈希映射到 6-22 之间的某个小时，打散用户通知负载。
     */
    private boolean isPreferredHour(long userId, int currentHour) {
        int assignedHour = (int) (userId % 17) + 6;
        return currentHour == assignedHour;
    }

    private String greetingByHour(int hour) {
        if (hour >= 5 && hour < 12) {
            return "早上好";
        }
        if (hour >= 12 && hour < 18) {
            return "下午好";
        }
        return "晚上好";
    }

    private String claimDailySend(long userId, String today) {
        if (redisTemplate == null) return "local-test-claim";
        String key = SENT_KEY_PREFIX + userId + ":" + today;
        String token = UUID.randomUUID().toString();
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, token, SENT_TTL)) ? token : null;
        } catch (Exception e) {
            log.warn("每日通知闸门抢占失败，userId={}", userId, e);
            return null;
        }
    }

    private void releaseDailyClaim(long userId, String today, String token) {
        if (redisTemplate == null || token == null) return;
        try {
            redisTemplate.execute(RELEASE_DAILY_CLAIM,
                    java.util.List.of(SENT_KEY_PREFIX + userId + ":" + today), token);
        } catch (Exception e) {
            log.debug("释放每日通知闸门失败, userId={}", userId, e);
        }
    }

    private int calcStreak(Long userId) {
        String cacheKey = "streak:%d:%s".formatted(userId, LocalDate.now());
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return Integer.parseInt(cached);
            }
        } catch (Exception e) {
            log.debug("连续天数缓存读取失败, userId={}", userId, e);
        }

        int streak = 0;
        LocalDate date = LocalDate.now();
        while (true) {
            long count = diaryMapper.selectCount(
                    new LambdaQueryWrapper<DiaryEntity>()
                            .eq(DiaryEntity::getAuthorUserId, userId)
                            .ge(DiaryEntity::getCreatedAt, date.atStartOfDay())
                            .lt(DiaryEntity::getCreatedAt, date.plusDays(1).atStartOfDay()));
            if (count == 0)
                break;
            streak++;
            date = date.minusDays(1);
        }

        try {
            redisTemplate.opsForValue().set(cacheKey, String.valueOf(streak), Duration.ofHours(6));
        } catch (Exception e) {
            log.debug("连续天数缓存写入失败, userId={}", userId, e);
        }
        return streak;
    }

    private ZoneId parseTimeZone(String timeZoneId) {
        try {
            return timeZoneId == null || timeZoneId.isBlank() ? ZoneId.of("Asia/Shanghai") : ZoneId.of(timeZoneId.trim());
        } catch (Exception e) {
            log.warn("事件回访时区配置无效，回退到 Asia/Shanghai: {}", timeZoneId);
            return ZoneId.of("Asia/Shanghai");
        }
    }
}
