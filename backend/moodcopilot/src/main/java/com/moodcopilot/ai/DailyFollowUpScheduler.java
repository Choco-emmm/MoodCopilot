package com.moodcopilot.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodcopilot.common.RateLimitException;
import com.moodcopilot.diary.DiaryAnalysis;
import com.moodcopilot.entity.DiaryAnalysisEntity;
import com.moodcopilot.entity.DiaryEntity;
import com.moodcopilot.mapper.DiaryAnalysisMapper;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.mapper.UserMapper;
import com.moodcopilot.notification.NotificationService;
import com.moodcopilot.security.RateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DailyFollowUpScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyFollowUpScheduler.class);

    private final UserMapper userMapper;
    private final DiaryMapper diaryMapper;
    private final DiaryAnalysisMapper diaryAnalysisMapper;
    private final AiAnalysisService aiAnalysisService;
    private final NotificationService notificationService;
    private final RateLimitService rateLimitService;
    private final StringRedisTemplate redisTemplate;

    public DailyFollowUpScheduler(UserMapper userMapper,
            DiaryMapper diaryMapper,
            DiaryAnalysisMapper diaryAnalysisMapper,
            AiAnalysisService aiAnalysisService,
            NotificationService notificationService,
            RateLimitService rateLimitService,
            StringRedisTemplate redisTemplate) {
        this.userMapper = userMapper;
        this.diaryMapper = diaryMapper;
        this.diaryAnalysisMapper = diaryAnalysisMapper;
        this.aiAnalysisService = aiAnalysisService;
        this.notificationService = notificationService;
        this.rateLimitService = rateLimitService;
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(cron = "0 0 6 * * *")
    public void sendDailyFollowUp() {
        log.info("每日跟进通知定时任务开始");
        List<Long> userIds = userMapper.findActiveUsersWithDiariesYesterday();
        if (userIds.isEmpty()) {
            log.info("没有需要发送每日通知的用户");
            return;
        }
        log.info("找到 {} 位昨天有日记且开启通知的用户", userIds.size());

        int sent = 0;
        int skipped = 0;
        for (Long userId : userIds) {
            try {
                // 检查 AI 额度
                try {
                    rateLimitService.tryAcquire(userId, RateLimitService.AiApiType.ANALYSIS);
                } catch (RateLimitException e) {
                    skipped++;
                    continue;
                }

                // 获取最近 7 篇日记
                List<DiaryEntity> recent = diaryMapper.selectList(
                        new LambdaQueryWrapper<DiaryEntity>()
                                .eq(DiaryEntity::getAuthorUserId, userId)
                                .orderByDesc(DiaryEntity::getCreatedAt)
                                .last("LIMIT 7"));

                // 获取分析结果
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

                List<String> contents = new ArrayList<>();
                List<DiaryAnalysis> analyses = new ArrayList<>();
                for (DiaryEntity d : recent) {
                    contents.add(d.getContent());
                    DiaryAnalysisEntity a = analysisMap.get(d.getId());
                    if (a != null) {
                        analyses.add(new DiaryAnalysis(a.getMoodLabel(), a.getMoodIntensity(),
                                a.getTopicLabelsJson(), a.getSummary(), a.getFeedback()));
                    } else {
                        analyses.add(null);
                    }
                }

                // 计算连续天数
                int streak = calcStreak(userId);

                // 昨日情绪
                String yesterdayMood = analyses.isEmpty() || analyses.get(0) == null
                        ? "复杂"
                        : analyses.get(0).moodLabel();

                // 调用 AI 陪跑
                String coaching = aiAnalysisService.generateCoaching(contents, analyses);

                // 构建通知内容
                String message = String.format(
                        "早安！已连续记录 %d 天，昨天是「%s」。\n\n%s", streak, yesterdayMood, coaching);

                notificationService.notifyDailyFollowUp(userId, message);
                sent++;
            } catch (Exception e) {
                log.warn("用户 {} 每日通知生成失败: {}", userId, e.getMessage());
            }
        }
        log.info("每日跟进通知完成: 发送 {} 条, 额度不足跳过 {} 人", sent, skipped);
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
}
