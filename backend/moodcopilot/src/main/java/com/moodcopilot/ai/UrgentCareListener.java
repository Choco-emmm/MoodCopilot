package com.moodcopilot.ai;

import com.moodcopilot.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * 监听日记分析完成事件，当检测到危机意图（自杀/自残/绝望）或严重情绪波动时，结合真实日记内容与画像生成深度共情安抚并推送。
 */
@Component
public class UrgentCareListener {

    private static final Logger log = LoggerFactory.getLogger(UrgentCareListener.class);
    private static final String URGENT_CARE_COOLDOWN_PREFIX = "care:urgent:cooldown:";
    private static final Duration URGENT_CARE_COOLDOWN = Duration.ofMinutes(30);

    private static final Set<String> CRISIS_KEYWORDS = Set.of(
            "自杀", "想死", "不想活", "结束生命", "离开这个世界", "活着没意思", "活着好累", "解脱", "割腕", "跳楼",
            "去死", "轻生", "吞药", "自残", "绝望想死", "不想撑了", "活着真痛苦", "没有活下去的意义");

    private static final Set<String> EXTREME_NEGATIVE_MOODS = Set.of(
            "绝望", "崩溃", "痛苦", "害怕", "难过", "孤独", "迷茫", "内疚", "愤怒", "窒息", "抑郁", "无助", "心碎", "窒息感");

    private final AiAnalysisService aiAnalysisService;
    private final NotificationService notificationService;
    private final StringRedisTemplate redisTemplate;

    public UrgentCareListener(AiAnalysisService aiAnalysisService, NotificationService notificationService,
            StringRedisTemplate redisTemplate) {
        this.aiAnalysisService = aiAnalysisService;
        this.notificationService = notificationService;
        this.redisTemplate = redisTemplate;
    }

    @Async
    @EventListener
    public void onDiaryAnalysisCompleted(DiaryAnalysisCompletedEvent event) {
        boolean isCrisis = checkIsCrisis(event.getContent(), event.getMoodLabel(), event.getValence());
        boolean isUrgentDistress = checkIsUrgentDistress(event.getMoodLabel(), event.getMoodIntensity(), event.getValence());

        if (!isCrisis && !isUrgentDistress) {
            return;
        }

        // 30分钟防刷冷却，避免连续保存日记产生重复弹窗；高危危机状态不受冷却限制
        String cooldownKey = URGENT_CARE_COOLDOWN_PREFIX + event.getUserId();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(cooldownKey, String.valueOf(System.currentTimeMillis()), URGENT_CARE_COOLDOWN);
        if (Boolean.FALSE.equals(acquired) && !isCrisis) {
            log.info("紧急关怀处于冷却期中，跳过本次推送，userId={}，diaryId={}", event.getUserId(), event.getDiaryId());
            return;
        }

        log.info("检测到{}情绪，触发危机/紧急关怀，userId={}，diaryId={}，mood={}，intensity={}，valence={}",
                isCrisis ? "危机倾向" : "极端负面", event.getUserId(), event.getDiaryId(),
                event.getMoodLabel(), event.getMoodIntensity(), event.getValence());

        try {
            String comfortMessage = aiAnalysisService.generateUrgentComfort(
                    event.getUserId(),
                    event.getContent(),
                    event.getMoodLabel(),
                    event.getMoodIntensity(),
                    event.getSummary(),
                    event.getFeedback(),
                    isCrisis);

            String titlePrefix = isCrisis ? "💙 **来自 MoodCopilot 的特别关怀**\n\n" : "🌱 **轻轻抱抱你**\n\n";
            String fullMessage = titlePrefix + comfortMessage;

            notificationService.notifyDailyFollowUp(event.getUserId(), fullMessage);
            log.info("紧急关怀通知已成功推送，userId={}，isCrisis={}", event.getUserId(), isCrisis);
        } catch (Exception e) {
            log.warn("紧急关怀生成或推送失败，userId={}，error={}", event.getUserId(), e.getMessage());
        }
    }

    private boolean checkIsCrisis(String content, String moodLabel, int valence) {
        if (content != null && !content.isBlank()) {
            for (String kw : CRISIS_KEYWORDS) {
                if (content.contains(kw)) {
                    return true;
                }
            }
        }
        if (moodLabel != null) {
            for (String kw : CRISIS_KEYWORDS) {
                if (moodLabel.contains(kw)) {
                    return true;
                }
            }
        }
        return valence <= -85;
    }

    private boolean checkIsUrgentDistress(String moodLabel, int moodIntensity, int valence) {
        if (moodIntensity >= 4 && moodLabel != null) {
            boolean matchesNegativeMood = EXTREME_NEGATIVE_MOODS.stream().anyMatch(moodLabel::contains);
            if (matchesNegativeMood) {
                return true;
            }
        }
        return valence <= -70 && moodIntensity >= 3;
    }
}

