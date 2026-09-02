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
        Boolean analyze,
        Boolean useReasoning) {
    public boolean isAnalyze() {
        return analyze == null || analyze;
    }

    /** 是否使用深度推理模型：未传视为 false */
    public boolean isUseReasoning() {
        return Boolean.TRUE.equals(useReasoning);
    }
}
