package com.moodcopilot.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SuggestionReplyDTO(
        Long id,
        Long suggestionId,
        Long parentReplyId,
        Long rootReplyId,
        Long authorUserId,
        String authorName,
        String authorAvatar,
        String authorRole,
        String replyToUserName,
        String content,
        LocalDateTime createdAt,
        List<SuggestionReplyDTO> replies
) {
}
