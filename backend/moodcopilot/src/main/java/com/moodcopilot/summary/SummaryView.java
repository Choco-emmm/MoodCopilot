package com.moodcopilot.summary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.diary.WeeklyReportView.DailyMood;
import com.moodcopilot.entity.DiarySummaryEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import java.util.LinkedHashMap;

public record SummaryView(
        long id,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        String aiSummary,
        List<DailyMood> dailyMoods,
        Map<String, Integer> topicCounts,
        int diaryCount,
        List<Long> diaryIds,
        List<String> insights,
        List<String> suggestions,
        String followUpPrompt,
        String reportType,
        LocalDateTime createdAt,
        String moodDominantQuadrant,
        Integer positiveRatioPercent,
        Integer highEnergyRatioPercent,
        Map<String, Integer> moodDistribution
) {
    static SummaryView from(DiarySummaryEntity entity, ObjectMapper mapper) {
        List<DailyMood> moods = List.of();
        Map<String, Integer> topics = Map.of();
        List<Long> diaryIds = List.of();
        List<String> insights = List.of();
        List<String> suggestions = List.of();
        try {
            if (entity.getMoodsJson() != null) {
                moods = mapper.readValue(entity.getMoodsJson(), new TypeReference<List<DailyMood>>() {});
            }
            if (entity.getTopicsJson() != null) {
                topics = mapper.readValue(entity.getTopicsJson(), new TypeReference<Map<String, Integer>>() {});
            }
            if (entity.getDiaryIds() != null) {
                diaryIds = mapper.readValue(entity.getDiaryIds(), new TypeReference<List<Long>>() {});
            }
            if (entity.getInsightsJson() != null) {
                insights = mapper.readValue(entity.getInsightsJson(),
                        mapper.getTypeFactory().constructCollectionType(List.class, String.class));
            }
            if (entity.getSuggestionsJson() != null) {
                suggestions = mapper.readValue(entity.getSuggestionsJson(),
                        mapper.getTypeFactory().constructCollectionType(List.class, String.class));
            }
        } catch (Exception ignored) {
        }

        String dominantQuadrant = "暂无";
        int posRatio = 0;
        int highEnergyRatio = 0;
        Map<String, Integer> distribution = new LinkedHashMap<>();
        distribution.put("正向高能量", 0);
        distribution.put("正向低能量", 0);
        distribution.put("负向高能量", 0);
        distribution.put("负向低能量", 0);

        if (moods != null && !moods.isEmpty()) {
            for (DailyMood m : moods) {
                int v = m.valence() != null ? m.valence() : 0;
                int a = m.arousal() != null ? m.arousal() : 0;
                String q;
                if (v > 0) {
                    q = a > 0 ? "正向高能量" : "正向低能量";
                } else {
                    q = a > 0 ? "负向高能量" : "负向低能量";
                }
                distribution.put(q, distribution.get(q) + 1);
            }
            int total = moods.size();
            dominantQuadrant = distribution.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("正向低能量");
            int positive = distribution.get("正向高能量") + distribution.get("正向低能量");
            posRatio = (int) Math.round((positive * 100.0) / total);
            int highEnergy = distribution.get("正向高能量") + distribution.get("负向高能量");
            highEnergyRatio = (int) Math.round((highEnergy * 100.0) / total);
        }

        return new SummaryView(
                entity.getId(),
                entity.getTitle(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getAiSummary(),
                moods,
                topics,
                entity.getDiaryCount() != null ? entity.getDiaryCount() : 0,
                diaryIds,
                insights,
                suggestions,
                entity.getFollowUpPrompt(),
                entity.getReportType(),
                entity.getCreatedAt(),
                dominantQuadrant,
                posRatio,
                highEnergyRatio,
                distribution
        );
    }
}
