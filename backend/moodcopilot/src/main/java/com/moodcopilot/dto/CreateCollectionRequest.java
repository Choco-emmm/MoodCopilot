package com.moodcopilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCollectionRequest(
        @NotBlank(message = "合集名称不能为空")
        @Size(max = 100, message = "合集名称不能超过100字")
        String name,

        @Size(max = 500, message = "合集描述不能超过500字")
        String description,

        String coverUrl,

        String visibility
) {
}