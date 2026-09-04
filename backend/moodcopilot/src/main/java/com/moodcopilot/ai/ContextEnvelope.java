package com.moodcopilot.ai;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Internal context contract. It intentionally contains no rendered prompt text. */
public record ContextEnvelope(
        String contextId,
        Long conversationId,
        long userId,
        ContextPurpose contextPurpose,
        Instant generatedAt,
        String plannerVersion,
        List<ContextItem> coreMemory,
        List<ContextItem> shortTermState,
        List<ContextItem> userReferences,
        List<ContextItem> retrievedContext,
        List<ContextItem> timelineContext,
        List<ContextItem> toolResults) {

    public ContextEnvelope {
        contextId = contextId == null || contextId.isBlank() ? "unknown" : contextId;
        contextPurpose = contextPurpose == null ? ContextPurpose.CHAT : contextPurpose;
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
        plannerVersion = plannerVersion == null || plannerVersion.isBlank() ? "1" : plannerVersion;
        coreMemory = immutable(coreMemory);
        shortTermState = immutable(shortTermState);
        userReferences = immutable(userReferences);
        retrievedContext = immutable(retrievedContext);
        timelineContext = immutable(timelineContext);
        toolResults = immutable(toolResults);
    }

    private static List<ContextItem> immutable(List<ContextItem> values) {
        return values == null ? List.of() : List.copyOf(values.stream().filter(Objects::nonNull).toList());
    }
}
