package com.moodcopilot.diary;

import java.time.LocalDateTime;

public record DiaryComment(
        long id,
        String authorName,
        String content,
        LocalDateTime createdAt
) {
}
