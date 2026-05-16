package com.moodcopilot.ai;

import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * 日记 AI 分析完成后发布的事件，用于驱动紧急关怀等后续流程。
 */
public class DiaryAnalysisCompletedEvent extends ApplicationEvent {

    private final long diaryId;
    private final long userId;
    private final String moodLabel;
    private final int moodIntensity;
    private final List<String> topicLabels;

    public DiaryAnalysisCompletedEvent(Object source, long diaryId, long userId,
            String moodLabel, int moodIntensity, List<String> topicLabels) {
        super(source);
        this.diaryId = diaryId;
        this.userId = userId;
        this.moodLabel = moodLabel;
        this.moodIntensity = moodIntensity;
        this.topicLabels = topicLabels;
    }

    public long getDiaryId() {
        return diaryId;
    }

    public long getUserId() {
        return userId;
    }

    public String getMoodLabel() {
        return moodLabel;
    }

    public int getMoodIntensity() {
        return moodIntensity;
    }

    public List<String> getTopicLabels() {
        return topicLabels;
    }
}
