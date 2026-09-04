package com.moodcopilot.ai;

import java.time.LocalDate;

/**
 * A memory hint returned together with the diary analysis.
 * It is persisted for the asynchronous memory task and is never a formal memory by itself.
 */
public record MemorySignal(
        String attributeKey,
        String attributeValue,
        String memoryType,
        String assertionType,
        Double confidence,
        String evidence,
        LocalDate validFrom,
        LocalDate validUntil,
        Boolean isCore
) {
}
