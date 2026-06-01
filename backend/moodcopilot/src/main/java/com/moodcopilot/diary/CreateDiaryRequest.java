package com.moodcopilot.diary;

import com.moodcopilot.entity.MusicMeta;
import com.moodcopilot.entity.DiaryImageMeta;

public record CreateDiaryRequest(
        String content,
        String visibility,
        MusicMeta musicMeta,
        java.util.List<String> images,
        java.util.List<DiaryImageMeta> imageMeta,
        Boolean analyze // null or true = 开启AI分析; false = 用户主动关闭
) {
    /** 是否开启 AI 分析：未传或 true 视为开启 */
    public boolean isAnalyze() {
        return analyze == null || analyze;
    }
}
