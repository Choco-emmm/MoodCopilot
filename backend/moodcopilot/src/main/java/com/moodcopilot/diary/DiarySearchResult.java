package com.moodcopilot.diary;

import java.time.LocalDate;
import java.util.List;

public record DiarySearchResult(
        String keyword,
        LocalDate startDate,
        LocalDate endDate,
        int total,
        List<DiarySummary> diaries,
        String note
) {

    public record DiarySummary(
            Long id,
            String date,
            String snippet
    ) {
    }
}
