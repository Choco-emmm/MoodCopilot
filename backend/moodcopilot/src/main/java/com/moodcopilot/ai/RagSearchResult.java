package com.moodcopilot.ai;

import java.util.List;

/**
 * Structured result for a RAG lookup. The mode is operational metadata and must
 * not be rendered into a user-facing prompt.
 */
public record RagSearchResult(Mode mode, List<RagMemoryService.RagHit> hits) {
    public enum Mode {
        VECTOR,
        LEXICAL_FALLBACK,
        EMPTY
    }

    public RagSearchResult {
        mode = mode == null ? Mode.EMPTY : mode;
        hits = hits == null ? List.of() : List.copyOf(hits);
    }

    public static RagSearchResult empty() {
        return new RagSearchResult(Mode.EMPTY, List.of());
    }

    public static RagSearchResult vector(List<RagMemoryService.RagHit> hits) {
        return new RagSearchResult(Mode.VECTOR, hits);
    }

    public static RagSearchResult lexicalFallback(List<RagMemoryService.RagHit> hits) {
        return new RagSearchResult(Mode.LEXICAL_FALLBACK, hits);
    }
}
