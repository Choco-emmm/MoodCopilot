package com.moodcopilot.diary;

import com.moodcopilot.entity.MusicMeta;

public record UpdateDiaryRequest(
        String content,
        String visibility,
        Boolean isPinned,
        MusicMeta musicMeta) {
}
