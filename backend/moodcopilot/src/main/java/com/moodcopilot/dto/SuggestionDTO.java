package com.moodcopilot.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SuggestionDTO(
        Long id,
        Long userId,
        String userName,
        String userAvatar,
        String content,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        int replyCount,
        List<SuggestionReplyDTO> replies
) {
}
