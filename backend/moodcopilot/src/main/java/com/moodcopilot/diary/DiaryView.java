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
        long authorUserId,
        String authorName,
        String authorAvatar,
        String content,
        DiaryVisibility visibility,
        DiaryAnalysis analysis,
        LocalDateTime createdAt,
        int resonanceCount,
        List<DiaryComment> comments
) {
    static DiaryView from(DiaryEntity diary, DiaryAnalysisEntity analysis, List<DiaryCommentEntity> comments, String authorAvatar) {
        return build(diary, analysis, comments, authorAvatar, false);
    }

    /** 公开视图：仅暴露情绪标签和主题，不暴露强度、摘要、反馈 */
    static DiaryView fromPublic(DiaryEntity diary, DiaryAnalysisEntity analysis, List<DiaryCommentEntity> comments, String authorAvatar) {
        return build(diary, analysis, comments, authorAvatar, true);
    }

    private static DiaryView build(DiaryEntity diary, DiaryAnalysisEntity analysis,
                                    List<DiaryCommentEntity> comments, String authorAvatar, boolean isPublic) {
        DiaryAnalysis viewAnalysis = null;
        if (analysis != null) {
            if (isPublic) {
                viewAnalysis = new DiaryAnalysis(
                        analysis.getMoodLabel(),
                        0,
                        analysis.getTopicLabelsJson(),
                        null,
                        null
                );
            } else {
                viewAnalysis = new DiaryAnalysis(
                        analysis.getMoodLabel(),
                        analysis.getMoodIntensity(),
                        analysis.getTopicLabelsJson(),
                        analysis.getSecondaryMoodsJson() != null ? analysis.getSecondaryMoodsJson() : List.of(),
                        analysis.getSummary(),
                        analysis.getFeedback()
                );
            }
        }
        return new DiaryView(
                diary.getId(),
                diary.getAuthorUserId(),
                diary.getAuthorName(),
                authorAvatar,
                diary.getContent(),
                DiaryVisibility.valueOf(diary.getVisibility()),
                viewAnalysis,
                diary.getCreatedAt(),
                diary.getResonanceCount(),
                buildCommentTree(comments)
        );
    }

    static DiaryView from(DiaryEntity diary, List<DiaryCommentEntity> comments, String authorAvatar) {
        return from(diary, null, comments, authorAvatar);
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
