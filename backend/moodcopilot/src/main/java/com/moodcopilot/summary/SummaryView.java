package com.moodcopilot.summary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.diary.WeeklyReportView.DailyMood;
import com.moodcopilot.entity.DiarySummaryEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
        LocalDateTime createdAt
) {
    static SummaryView from(DiarySummaryEntity entity, ObjectMapper mapper) {
        List<DailyMood> moods = List.of();
        Map<String, Integer> topics = Map.of();
        List<Long> diaryIds = List.of();
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
        } catch (Exception ignored) {
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
                entity.getCreatedAt()
        );
    }
}
