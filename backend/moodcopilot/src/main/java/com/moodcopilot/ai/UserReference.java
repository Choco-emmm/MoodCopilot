package com.moodcopilot.ai;

/** A user-selected source kept separate from ordinary retrieved context. */
public record UserReference(
        String content,
        ContextSource source,
        ReferencePurpose referencePurpose,
        double relevanceScore,
        int priority,
        boolean conflict) {

    public UserReference {
        content = content == null ? "" : content;
        source = source == null ? new ContextSource("USER_MESSAGE", "reference", "user",
                "explicit_reference", null, null, ContextSource.TrustLevel.AUTHORITATIVE, null) : source;
        referencePurpose = referencePurpose == null ? ReferencePurpose.DISCUSS : referencePurpose;
        relevanceScore = Double.isFinite(relevanceScore) ? relevanceScore : 1D;
    }
}
