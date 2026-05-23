package com.moodcopilot.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("日记图片深度分析请求参数")
public record DiaryImageAnalysisRequest(
        @JsonProperty(required = true, value = "diaryIds")
        @JsonPropertyDescription("需要深度分析图片的日记 ID 列表")
        List<Long> diaryIds,

        @JsonProperty(required = true, value = "prompt")
        @JsonPropertyDescription("希望视觉模型重点关注的提问要求")
        String prompt
) {}
