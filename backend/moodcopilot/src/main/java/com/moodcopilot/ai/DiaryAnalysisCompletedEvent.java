package com.moodcopilot.ai;

import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * 日记 AI 分析完成后发布的事件，用于驱动紧急关怀与画像更新等后续流程。
 */
public class DiaryAnalysisCompletedEvent extends ApplicationEvent {

    private final long diaryId;
    private final long userId;
    private final String moodLabel;
    private final int moodIntensity;
    private final List<String> topicLabels;
    private final String content;
    private final String summary;
    private final String feedback;
    private final int valence;
    private final int arousal;

    public DiaryAnalysisCompletedEvent(Object source, long diaryId, long userId,
            String moodLabel, int moodIntensity, List<String> topicLabels,
            String content, String summary, String feedback, int valence, int arousal) {
        super(source);
        this.diaryId = diaryId;
        this.userId = userId;
        this.moodLabel = moodLabel;
        this.moodIntensity = moodIntensity;
        this.topicLabels = topicLabels;
        this.content = content;
        this.summary = summary;
        this.feedback = feedback;
        this.valence = valence;
        this.arousal = arousal;
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

    public String getContent() {
        return content;
    }

    public String getSummary() {
        return summary;
    }

    public String getFeedback() {
        return feedback;
    }

    public int getValence() {
        return valence;
    }

    public int getArousal() {
        return arousal;
    }
}

