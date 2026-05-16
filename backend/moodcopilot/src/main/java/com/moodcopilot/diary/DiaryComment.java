package com.moodcopilot.diary;

import java.time.LocalDateTime;
import java.util.List;

public record DiaryComment(
        long id,
        Long parentCommentId,
        String replyToUserName,
        String authorName,
        String content,
        LocalDateTime createdAt,
        List<DiaryComment> replies
) {
    public static DiaryComment from(com.moodcopilot.entity.DiaryCommentEntity c,
                                     String replyToUserName,
                                     List<DiaryComment> replies) {
        return from(c, replyToUserName, replies, c.getAuthorName());
    }

    public static DiaryComment from(com.moodcopilot.entity.DiaryCommentEntity c,
                                     String replyToUserName,
                                     List<DiaryComment> replies,
                                     String authorName) {
        return new DiaryComment(
                c.getId(),
                c.getParentCommentId(),
                replyToUserName,
                authorName,
                c.getContent(),
                c.getCreatedAt(),
                replies
        );
    }
}
