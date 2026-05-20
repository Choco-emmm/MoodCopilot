package com.moodcopilot.diary;

import com.moodcopilot.entity.MusicMeta;

public record CreateDiaryRequest(
        String content,
        String visibility,
        MusicMeta musicMeta
) {
}
