package com.moodcopilot.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AddDiaryToCollectionRequest(
        @NotEmpty(message = "日记ID列表不能为空")
        @NotNull(message = "日记ID列表不能为null")
        List<Long> diaryIds
) {
}