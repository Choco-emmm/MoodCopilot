package com.moodcopilot.ai;

/** A selected, provenance-aware item ready for context planning/rendering. */
public record ContextItem(
        String content,
        ContextSource source,
        double relevanceScore,
        int priority,
        boolean conflict) {

    public ContextItem {
        content = content == null ? "" : content;
        relevanceScore = Double.isFinite(relevanceScore) ? relevanceScore : 0D;
    }
}
