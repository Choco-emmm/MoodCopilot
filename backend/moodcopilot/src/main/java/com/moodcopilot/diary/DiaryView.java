package com.moodcopilot.diary;

import com.moodcopilot.entity.DiaryAnalysisEntity;
import com.moodcopilot.entity.DiaryCommentEntity;
import com.moodcopilot.entity.DiaryEntity;

import java.time.LocalDateTime;
import java.util.List;

public record DiaryView(
        long id,
        String authorName,
        String content,
        DiaryVisibility visibility,
        DiaryAnalysis analysis,
        LocalDateTime createdAt,
        int resonanceCount,
        List<DiaryComment> comments
) {
    static DiaryView from(DiaryEntity diary, DiaryAnalysisEntity analysis, List<DiaryCommentEntity> comments) {
        return new DiaryView(
                diary.getId(),
                diary.getAuthorName(),
                diary.getContent(),
                DiaryVisibility.valueOf(diary.getVisibility()),
                analysis != null ? new DiaryAnalysis(
                        analysis.getMoodLabel(),
                        analysis.getMoodIntensity(),
                        analysis.getTopicLabelsJson(),
                        analysis.getSummary(),
                        analysis.getFeedback()
                ) : null,
                diary.getCreatedAt(),
                diary.getResonanceCount(),
                comments.stream()
                        .map(c -> new DiaryComment(c.getId(), c.getAuthorName(), c.getContent(), c.getCreatedAt()))
                        .toList()
        );
    }

    static DiaryView from(DiaryEntity diary, List<DiaryCommentEntity> comments) {
        return from(diary, null, comments);
    }
}
