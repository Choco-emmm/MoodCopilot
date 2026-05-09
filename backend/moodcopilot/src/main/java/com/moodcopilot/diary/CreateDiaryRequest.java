package com.moodcopilot.diary;

public record CreateDiaryRequest(
        String content,
        String visibility
) {
}
