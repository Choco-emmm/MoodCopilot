package com.moodcopilot.view;

import com.moodcopilot.diary.DiaryAnalysis;
import com.moodcopilot.diary.DiaryVisibility;
import com.moodcopilot.entity.MusicMeta;

import java.time.LocalDateTime;

public record CollectionDiaryView(
        Long id,
        Long authorUserId,
        String authorName,
        String authorAvatar,
        Integer authorLevel,
        String authorRole,
        String content,
        DiaryVisibility visibility,
        DiaryAnalysis analysis,
        LocalDateTime createdAt,
        int resonanceCount,
        boolean likedByMe,
        boolean isPinned,
        MusicMeta musicMeta,
        java.util.List<String> images,
        String analysisStatus,
        Double sortOrder) {

    public static CollectionDiaryView from(com.moodcopilot.entity.DiaryEntity diary,
            com.moodcopilot.entity.DiaryAnalysisEntity analysis,
            boolean likedByMe) {
        return from(diary, analysis, likedByMe, null);
    }

    public static CollectionDiaryView from(com.moodcopilot.entity.DiaryEntity diary,
            com.moodcopilot.entity.DiaryAnalysisEntity analysis,
            boolean likedByMe,
            Double sortOrder) {
        return from(diary, analysis, likedByMe, sortOrder, true);
    }

    public static CollectionDiaryView from(com.moodcopilot.entity.DiaryEntity diary,
            com.moodcopilot.entity.DiaryAnalysisEntity analysis,
            boolean likedByMe,
            Double sortOrder,
            boolean includePrivateInsights) {
        DiaryAnalysis viewAnalysis = null;
        if (analysis != null) {
            if (includePrivateInsights) {
                viewAnalysis = new DiaryAnalysis(
                        analysis.getMoodLabel(),
                        analysis.getMoodIntensity(),
                        analysis.getValence(),
                        analysis.getArousal(),
                        analysis.getTopicLabelsJson(),
                        analysis.getSecondaryMoodsJson() != null ? analysis.getSecondaryMoodsJson()
                                : java.util.List.of(),
                        analysis.getSummary(),
                        analysis.getFeedback());
            }
        }

        return new CollectionDiaryView(
                diary.getId(),
                diary.getAuthorUserId(),
                diary.getAuthorName(),
                null,
                null,
                null,
                diary.getContent(),
                DiaryVisibility.valueOf(diary.getVisibility()),
                viewAnalysis,
                diary.getCreatedAt(),
                diary.getResonanceCount(),
                likedByMe,
                Boolean.TRUE.equals(diary.getIsPinned()),
                sanitizeMusicMeta(diary.getMusicMeta(), includePrivateInsights),
                diary.getImages(),
                diary.getAnalysisStatus() != null ? diary.getAnalysisStatus()
                                : (viewAnalysis != null ? "complete" : null),
                sortOrder);
    }

    private static MusicMeta sanitizeMusicMeta(MusicMeta musicMeta, boolean includePrivateInsights) {
        if (musicMeta == null) {
            return null;
        }
        if (includePrivateInsights) {
            return musicMeta;
        }
        return new MusicMeta(
                musicMeta.getTitle(),
                musicMeta.getArtist(),
                musicMeta.getCoverUrl(),
                musicMeta.getUserLyric(),
                musicMeta.getSongUrl(),
                null,
                null);
    }
}
