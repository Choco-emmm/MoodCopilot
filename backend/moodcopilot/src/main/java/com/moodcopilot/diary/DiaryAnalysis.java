package com.moodcopilot.diary;

import java.util.List;

public record DiaryAnalysis(
        String moodLabel,
        int moodIntensity,
        Integer valence,
        Integer arousal,
        List<String> topicLabels,
        List<String> secondaryMoods,
        String summary,
        String feedback
) {
    public DiaryAnalysis(String moodLabel, int moodIntensity, List<String> topicLabels,
                         String summary, String feedback) {
        this(moodLabel, moodIntensity, null, null, topicLabels, List.of(), summary, feedback);
    }
    public DiaryAnalysis(String moodLabel, int moodIntensity, List<String> topicLabels,
                         List<String> secondaryMoods, String summary, String feedback) {
        this(moodLabel, moodIntensity, null, null, topicLabels, secondaryMoods, summary, feedback);
    }

    public boolean hasSecondaryMoods() {
        return secondaryMoods != null && !secondaryMoods.isEmpty();
    }
}
