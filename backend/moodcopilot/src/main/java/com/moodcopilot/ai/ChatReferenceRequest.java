package com.moodcopilot.ai;

/**
 * Client-side reference descriptor. The client may identify a source, but its
 * content is never trusted; ChatReferenceResolver loads the content server-side.
 */
public record ChatReferenceRequest(String sourceType, Long sourceId, String referencePurpose) {
}
