package com.moodcopilot.diary;

import java.time.LocalDate;

public record DiarySearchRequest(
        String keyword,
        LocalDate startDate,
        LocalDate endDate
) {
}
