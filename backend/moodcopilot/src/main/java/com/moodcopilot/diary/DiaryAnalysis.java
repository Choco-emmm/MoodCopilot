package com.moodcopilot.diary;

import java.util.List;

public record DiaryAnalysis(
        String moodLabel,
        int moodIntensity,
        List<String> topicLabels,
        List<String> secondaryMoods,
        String summary,
        String feedback
) {
    public DiaryAnalysis(String moodLabel, int moodIntensity, List<String> topicLabels,
                         String summary, String feedback) {
        this(moodLabel, moodIntensity, topicLabels, List.of(), summary, feedback);
    }

    public boolean hasSecondaryMoods() {
        return secondaryMoods != null && !secondaryMoods.isEmpty();
    }
}
