package com.moodcopilot.diary;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record WeeklyReportView(
                String weekLabel,
                int diaryCount,
                List<DailyMood> dailyMoods,
                Map<String, Integer> topicCounts,
                Map<String, Integer> moodDistribution,
                String moodDominantQuadrant,
                Integer positiveRatioPercent,
                Integer highEnergyRatioPercent,
                String aiSummary,
                List<String> insights,
                List<String> suggestions,
                String followUpPrompt,
                LocalDateTime generatedAt,
                boolean needsRegenerate) {
        public record DailyMood(
                        LocalDate date,
                        String moodLabel,
                        int moodIntensity,
                        Integer valence,
                        Integer arousal,
                        List<Long> diaryIds,
                        String contentSnippet) {
        }
}
