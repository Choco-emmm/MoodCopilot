package com.moodcopilot.ai;

import com.moodcopilot.common.RateLimitException;
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
    private final NotificationService notificationService;
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
        this.notificationService = notificationService;
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
     * 每小时触发一次，处理到期的重要事件回访。
     * 普通的连续记录/每日陪伴通知已停用。
     */
    @Scheduled(cron = "0 0 * * * *")
    public void sendDailyFollowUp() {
        LocalDateTime now = LocalDateTime.now(eventTimeZone);
        log.info("事件回访定时任务触发，currentHour={}", now.getHour());

        LinkedHashSet<Long> userIdSet = new LinkedHashSet<>(userMapper.findUsersWithDueLifeEvents(now));
        List<Long> userIds = new ArrayList<>(userIdSet);
        if (userIds.isEmpty()) {
            log.info("没有需要发送事件回访通知的用户");
            return;
        }

        String today = now.toLocalDate().toString();
        int sent = 0;
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
                // 普通的“连续记录/每日陪伴”通知已停用；重要事件回访仍由上面的分支发送。
                // 释放本轮闸门，避免停用通知占用当天的发送资格。
                releaseDailyClaim(userId, today, dailyClaim);
                dailyClaim = null;
                continue;
            } catch (Exception e) {
                log.warn("用户 {} 每日通知生成失败: {}", userId, e.getMessage());
            } finally {
                if (!notificationPersisted && dailyClaim != null) {
                    releaseDailyClaim(userId, today, dailyClaim);
                }
            }
        }
        log.info("事件回访通知完成: 发送 {} 条", sent);
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

    private ZoneId parseTimeZone(String timeZoneId) {
        try {
            return timeZoneId == null || timeZoneId.isBlank() ? ZoneId.of("Asia/Shanghai") : ZoneId.of(timeZoneId.trim());
        } catch (Exception e) {
            log.warn("事件回访时区配置无效，回退到 Asia/Shanghai: {}", timeZoneId);
            return ZoneId.of("Asia/Shanghai");
        }
    }
}
