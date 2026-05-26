package com.moodcopilot.diary;

import java.util.Map;

public record UserStatsResult(
        int days,
        int diaryCount,
        Map<String, Long> moodCounts,
        Map<String, Long> topTopics,
        String note) {
}