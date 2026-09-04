package com.moodcopilot.ai;

import java.time.Instant;

/** Structured provenance for context data; renderers must not infer provenance from display text. */
public record ContextSource(
        String sourceType,
        String sourceId,
        String authorType,
        String contentType,
        Instant eventTime,
        String derivedFrom,
        TrustLevel trustLevel,
        Long userId) {

    public enum TrustLevel {
        AUTHORITATIVE,
        SUPPORTING,
        UNTRUSTED
    }

    public ContextSource {
        sourceType = sourceType == null ? "unknown" : sourceType;
        authorType = authorType == null ? "unknown" : authorType;
        contentType = contentType == null ? "unknown" : contentType;
        trustLevel = trustLevel == null ? TrustLevel.UNTRUSTED : trustLevel;
    }
}
