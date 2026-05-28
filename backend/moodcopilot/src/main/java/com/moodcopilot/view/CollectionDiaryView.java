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
        Double sortOrder
) {

    public static CollectionDiaryView from(com.moodcopilot.entity.DiaryEntity diary,
                                          com.moodcopilot.entity.DiaryAnalysisEntity analysis,
                                          boolean likedByMe) {
        return from(diary, analysis, likedByMe, null);
    }

    public static CollectionDiaryView from(com.moodcopilot.entity.DiaryEntity diary,
                                          com.moodcopilot.entity.DiaryAnalysisEntity analysis,
                                          boolean likedByMe,
                                          Double sortOrder) {
        DiaryAnalysis viewAnalysis = null;
        if (analysis != null) {
            viewAnalysis = new DiaryAnalysis(
                    analysis.getMoodLabel(),
                    analysis.getMoodIntensity(),
                    analysis.getTopicLabelsJson(),
                    analysis.getSecondaryMoodsJson() != null ? analysis.getSecondaryMoodsJson() : java.util.List.of(),
                    analysis.getSummary(),
                    analysis.getFeedback());
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
                diary.getMusicMeta(),
                diary.getImages(),
                viewAnalysis != null ? "complete" : null,
                sortOrder
        );
    }
}