package com.moodcopilot.diary;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record WeeklyReportView(
        String weekLabel,
        int diaryCount,
        List<DailyMood> dailyMoods,
        Map<String, Integer> topicCounts,
        String aiSummary
) {
    public record DailyMood(
            LocalDate date,
            String moodLabel,
            int moodIntensity,
            List<Long> diaryIds
    ) {
    }
}
