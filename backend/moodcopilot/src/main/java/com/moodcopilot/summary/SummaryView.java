package com.moodcopilot.summary;

import com.moodcopilot.entity.DiarySummaryEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SummaryView(
        long id,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        String aiSummary,
        LocalDateTime createdAt
) {
    static SummaryView from(DiarySummaryEntity entity) {
        return new SummaryView(
                entity.getId(),
                entity.getTitle(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getAiSummary(),
                entity.getCreatedAt()
        );
    }
}
