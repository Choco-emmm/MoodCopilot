package com.moodcopilot.diary;

import com.moodcopilot.entity.DiaryAnalysisEntity;
import com.moodcopilot.entity.DiaryCommentEntity;
import com.moodcopilot.entity.DiaryEntity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
                buildCommentTree(comments)
        );
    }

    static DiaryView from(DiaryEntity diary, List<DiaryCommentEntity> comments) {
        return from(diary, null, comments);
    }

    private static List<DiaryComment> buildCommentTree(List<DiaryCommentEntity> entities) {
        var topLevel = entities.stream()
                .filter(c -> c.getRootCommentId() == null)
                .toList();
        var repliesByRoot = entities.stream()
                .filter(c -> c.getRootCommentId() != null)
                .collect(Collectors.groupingBy(DiaryCommentEntity::getRootCommentId));
        var authorById = entities.stream()
                .collect(Collectors.toMap(DiaryCommentEntity::getId, DiaryCommentEntity::getAuthorName, (a, b) -> a));
        return topLevel.stream()
                .map(c -> DiaryComment.from(c, null,
                        repliesByRoot.getOrDefault(c.getId(), List.of()).stream()
                                .map(r -> DiaryComment.from(r,
                                        r.getParentCommentId() != null ? authorById.get(r.getParentCommentId()) : null,
                                        List.of()))
                                .toList()))
                .toList();
    }
}
