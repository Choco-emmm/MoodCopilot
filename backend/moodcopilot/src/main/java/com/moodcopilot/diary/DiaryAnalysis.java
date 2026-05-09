package com.moodcopilot.diary;

import java.util.List;

public record DiaryAnalysis(
        String moodLabel,
        int moodIntensity,
        List<String> topicLabels,
        String summary,
        String feedback
) {
}
