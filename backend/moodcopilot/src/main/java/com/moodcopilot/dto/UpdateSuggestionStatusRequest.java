package com.moodcopilot.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateSuggestionStatusRequest(
        @NotBlank(message = "状态不能为空")
        String status
) {
}
