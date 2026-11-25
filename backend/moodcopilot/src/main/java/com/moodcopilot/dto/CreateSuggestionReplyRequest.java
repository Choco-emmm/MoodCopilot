package com.moodcopilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSuggestionReplyRequest(
        @NotBlank(message = "回复内容不能为空")
        @Size(max = 1000, message = "回复内容不能超过1000字")
        String content,
        Long parentReplyId
) {
}
