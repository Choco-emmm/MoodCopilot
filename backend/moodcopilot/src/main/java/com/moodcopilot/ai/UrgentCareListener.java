package com.moodcopilot.ai;

import com.moodcopilot.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 监听日记分析完成事件，当检测到极端负面情绪时立刻生成安抚话语并推送。
 */
@Component
public class UrgentCareListener {

    private static final Logger log = LoggerFactory.getLogger(UrgentCareListener.class);

    private static final List<String> URGENT_NEGATIVE_MOODS = List.of(
            "崩溃", "极度抑郁", "绝望", "自残", "自杀", "极度焦虑", "极度愤怒");

    private static final int URGENT_INTENSITY_THRESHOLD = 8;

    private final AiAnalysisService aiAnalysisService;
    private final NotificationService notificationService;

    public UrgentCareListener(AiAnalysisService aiAnalysisService, NotificationService notificationService) {
        this.aiAnalysisService = aiAnalysisService;
        this.notificationService = notificationService;
    }

    @Async
    @EventListener
    public void onDiaryAnalysisCompleted(DiaryAnalysisCompletedEvent event) {
        if (!isUrgentNegative(event.getMoodLabel(), event.getMoodIntensity())) {
            return;
        }

        log.info("检测到极端负面情绪，触发紧急关怀，userId={}，diaryId={}，mood={}，intensity={}",
                event.getUserId(), event.getDiaryId(), event.getMoodLabel(), event.getMoodIntensity());

        try {
            String comfort = aiAnalysisService.generateCoaching(
                    List.of(), List.of());
            String message = "我注意到你现在可能不太好。\n\n" + comfort;
            notificationService.notifyDailyFollowUp(event.getUserId(), message);
            log.info("紧急关怀已推送，userId={}", event.getUserId());
        } catch (Exception e) {
            log.warn("紧急关怀生成失败，userId={}，error={}", event.getUserId(), e.getMessage());
        }
    }

    private boolean isUrgentNegative(String moodLabel, int moodIntensity) {
        if (moodLabel == null || moodIntensity < URGENT_INTENSITY_THRESHOLD) {
            return false;
        }
        return URGENT_NEGATIVE_MOODS.stream().anyMatch(moodLabel::contains);
    }
}
