package com.moodcopilot.dto;

import java.time.LocalDateTime;

public record SuggestionDTO(
        Long id,
        Long userId,
        String userName,
        String userAvatar,
        String content,
        String status,
        LocalDateTime createdAt
) {
}
