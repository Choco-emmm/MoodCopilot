package com.moodcopilot.diary;

import com.moodcopilot.entity.MusicMeta;
import com.moodcopilot.entity.DiaryImageMeta;

public record UpdateDiaryRequest(
        String content,
        String visibility,
        Boolean isPinned,
        MusicMeta musicMeta,
        java.util.List<String> images,
        java.util.List<DiaryImageMeta> imageMeta,
        Boolean analyze) {
    public boolean isAnalyze() {
        return analyze == null || analyze;
    }
}
