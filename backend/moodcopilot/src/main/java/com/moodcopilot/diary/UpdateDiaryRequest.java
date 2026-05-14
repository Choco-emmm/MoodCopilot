package com.moodcopilot.diary;

public record UpdateDiaryRequest(
        String content,
        String visibility) {
}
