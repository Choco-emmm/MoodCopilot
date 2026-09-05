package com.moodcopilot.ai;

import java.util.List;

/** Normalized semantic-search request. It is separate from the RediSearch query string. */
public record RagQuery(
        long userId,
        String queryKind,
        String queryText,
        List<String> sourceTypes,
        TimeExpressionParser.TimeRange timeRange,
        int topK,
        ContextPurpose contextPurpose) {

    public RagQuery {
        queryText = RagQueryBuilder.embeddingText(queryText);
        sourceTypes = sourceTypes == null ? List.of() : sourceTypes.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(java.util.Locale.ROOT))
                .distinct().toList();
        topK = Math.max(1, Math.min(topK, 50));
        contextPurpose = contextPurpose == null ? ContextPurpose.CHAT : contextPurpose;
    }
}
