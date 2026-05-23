package com.moodcopilot.diary;

import com.moodcopilot.entity.DiaryAnalysisEntity;
import com.moodcopilot.entity.DiaryCommentEntity;
import com.moodcopilot.entity.DiaryEntity;
import com.moodcopilot.entity.MusicMeta;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record DiaryView(
                long id,
                long authorUserId,
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
                List<DiaryComment> comments) {

        public DiaryView withAnalysisStatus(String status) {
            return new DiaryView(id, authorUserId, authorName, authorAvatar, authorLevel, authorRole,
                    content, visibility, analysis, createdAt, resonanceCount, likedByMe,
                    isPinned, musicMeta, images, status, comments);
        }

        public DiaryView withLikedByMe(boolean liked) {
            return new DiaryView(id, authorUserId, authorName, authorAvatar, authorLevel, authorRole,
                    content, visibility, analysis, createdAt, resonanceCount, liked,
                    isPinned, musicMeta, images, analysisStatus, comments);
        }
        static DiaryView from(DiaryEntity diary, DiaryAnalysisEntity analysis, List<DiaryCommentEntity> comments,
                        String authorAvatar, String authorName, Map<Long, String> commentAuthorNames,
                        boolean likedByMe) {
                return build(diary, analysis, comments, authorAvatar, authorName, null, null, commentAuthorNames, false, likedByMe);
        }

        static DiaryView from(DiaryEntity diary, DiaryAnalysisEntity analysis, List<DiaryCommentEntity> comments,
                        String authorAvatar, String authorName, Integer authorLevel, String authorRole, Map<Long, String> commentAuthorNames,
                        boolean likedByMe) {
                return build(diary, analysis, comments, authorAvatar, authorName, authorLevel, authorRole, commentAuthorNames, false, likedByMe);
        }

        /** 公开视图：仅暴露情绪标签和主题，不暴露强度、摘要、反馈 */
        static DiaryView fromPublic(DiaryEntity diary, DiaryAnalysisEntity analysis, List<DiaryCommentEntity> comments,
                        String authorAvatar, String authorName, Map<Long, String> commentAuthorNames,
                        boolean likedByMe) {
                return build(diary, analysis, comments, authorAvatar, authorName, null, null, commentAuthorNames, true, likedByMe);
        }

        static DiaryView fromPublic(DiaryEntity diary, DiaryAnalysisEntity analysis, List<DiaryCommentEntity> comments,
                        String authorAvatar, String authorName, Integer authorLevel, String authorRole, Map<Long, String> commentAuthorNames,
                        boolean likedByMe) {
                return build(diary, analysis, comments, authorAvatar, authorName, authorLevel, authorRole, commentAuthorNames, true, likedByMe);
        }

        private static DiaryView build(DiaryEntity diary, DiaryAnalysisEntity analysis,
                        List<DiaryCommentEntity> comments, String authorAvatar, String authorName,
                        Integer authorLevel, String authorRole, Map<Long, String> commentAuthorNames, boolean isPublic, boolean likedByMe) {
                DiaryAnalysis viewAnalysis = null;
                if (analysis != null) {
                        if (isPublic) {
                                viewAnalysis = new DiaryAnalysis(
                                                analysis.getMoodLabel(),
                                                0,
                                                analysis.getTopicLabelsJson(),
                                                null,
                                                null);
                        } else {
                                viewAnalysis = new DiaryAnalysis(
                                                analysis.getMoodLabel(),
                                                analysis.getMoodIntensity(),
                                                analysis.getTopicLabelsJson(),
                                                analysis.getSecondaryMoodsJson() != null
                                                                ? analysis.getSecondaryMoodsJson()
                                                                : List.of(),
                                                analysis.getSummary(),
                                                analysis.getFeedback());
                        }
                }
                return new DiaryView(
                                diary.getId(),
                                diary.getAuthorUserId(),
                                authorName,
                                authorAvatar,
                                authorLevel,
                                authorRole,
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
                                buildCommentTree(comments, commentAuthorNames));
        }

        static DiaryView from(DiaryEntity diary, List<DiaryCommentEntity> comments, String authorAvatar,
                        String authorName,
                        Map<Long, String> commentAuthorNames, boolean likedByMe) {
                return from(diary, null, comments, authorAvatar, authorName, commentAuthorNames, likedByMe);
        }

        /** Feed 模式公开视图：无评论、裁切内容、仅暴露情绪标签和主题 */
        static DiaryView fromPublicFeed(DiaryEntity diary, DiaryAnalysisEntity analysis,
                String authorName, String authorAvatar, Integer authorLevel, String authorRole, boolean likedByMe, String feedContent) {
            DiaryAnalysis va = analysis != null
                    ? new DiaryAnalysis(analysis.getMoodLabel(), 0, analysis.getTopicLabelsJson(), null, null)
                    : null;
            return new DiaryView(diary.getId(), diary.getAuthorUserId(), authorName, authorAvatar,
                    authorLevel, authorRole, feedContent, DiaryVisibility.valueOf(diary.getVisibility()), va,
                    diary.getCreatedAt(), diary.getResonanceCount(), likedByMe,
                    Boolean.TRUE.equals(diary.getIsPinned()), diary.getMusicMeta(), diary.getImages(),
                    va != null ? "complete" : null, List.of());
        }

        /** Feed 模式个人视图：无评论、裁切内容、完整分析 */
        static DiaryView fromFeed(DiaryEntity diary, DiaryAnalysisEntity analysis,
                String authorName, String authorAvatar, Integer authorLevel, String authorRole, boolean likedByMe, String feedContent) {
            DiaryAnalysis va = analysis != null
                    ? new DiaryAnalysis(analysis.getMoodLabel(), analysis.getMoodIntensity(),
                            analysis.getTopicLabelsJson(),
                            analysis.getSecondaryMoodsJson() != null ? analysis.getSecondaryMoodsJson() : List.of(),
                            analysis.getSummary(), analysis.getFeedback())
                    : null;
            return new DiaryView(diary.getId(), diary.getAuthorUserId(), authorName, authorAvatar,
                    authorLevel, authorRole, feedContent, DiaryVisibility.valueOf(diary.getVisibility()), va,
                    diary.getCreatedAt(), diary.getResonanceCount(), likedByMe,
                    Boolean.TRUE.equals(diary.getIsPinned()), diary.getMusicMeta(), diary.getImages(),
                    va != null ? "complete" : null, List.of());
        }

        private static List<DiaryComment> buildCommentTree(List<DiaryCommentEntity> entities,
                        Map<Long, String> commentAuthorNames) {
                var topLevel = entities.stream()
                                .filter(c -> c.getRootCommentId() == null)
                                .toList();
                var repliesByRoot = entities.stream()
                                .filter(c -> c.getRootCommentId() != null)
                                .collect(Collectors.groupingBy(DiaryCommentEntity::getRootCommentId));
                var authorById = entities.stream()
                                .collect(Collectors.toMap(
                                                DiaryCommentEntity::getId,
                                                c -> commentAuthorNames.getOrDefault(c.getAuthorUserId(),
                                                                c.getAuthorName()),
                                                (a, b) -> a));
                return topLevel.stream()
                                .map(c -> DiaryComment.from(c, null,
                                                repliesByRoot.getOrDefault(c.getId(), List.of()).stream()
                                                                .map(r -> DiaryComment.from(r,
                                                                                r.getParentCommentId() != null
                                                                                                ? authorById.get(r
                                                                                                                .getParentCommentId())
                                                                                                : null,
                                                                                List.<DiaryComment>of(),
                                                                                commentAuthorNames.getOrDefault(
                                                                                                r.getAuthorUserId(),
                                                                                                r.getAuthorName())))
                                                                .toList(),
                                                commentAuthorNames.getOrDefault(c.getAuthorUserId(),
                                                                c.getAuthorName())))
                                .toList();
        }
}
