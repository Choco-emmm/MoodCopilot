package com.moodcopilot.entity.dto;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomChatMessage(
        String id,
        String role,
        String content,
        String reasoningContent,
        List<Map<String, Object>> toolCalls,
        Map<String, Object> quoteRef,
        List<String> references,
        List<Map<String, Object>> ragReferences
) {}
