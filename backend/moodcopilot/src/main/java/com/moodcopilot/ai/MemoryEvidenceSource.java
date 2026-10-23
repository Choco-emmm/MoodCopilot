package com.moodcopilot.ai;

import java.util.List;
import java.util.Set;

/** Selects user-authored evidence copies without altering the original conversation. */
public final class MemoryEvidenceSource {
    private MemoryEvidenceSource() {}
    public static String currentMessage(String original) {
        if (original == null) return "";
        // Legacy Web quote transport embeds a previous assistant reply into the user message.
        // Preserve it for conversation, but never promote that quoted reply into user evidence.
        if (original.startsWith("[用户引用了之前的发言：\n\"")) {
            String separator = "\"]\n\n用户的回复是：\n";
            int boundary = original.lastIndexOf(separator);
            if (boundary >= 0) return original.substring(boundary + separator.length());
            return "";
        }
        return original;
    }

    public static List<String> references(List<UserReference> references, long userId) {
        if (references == null) return List.of();
        return references.stream().filter(reference -> reference != null && reference.source() != null)
                .filter(reference -> Long.valueOf(userId).equals(reference.source().userId()))
                .filter(reference -> "user".equalsIgnoreCase(reference.source().authorType()))
                .filter(reference -> Set.of("USER_MESSAGE", "USER_DIARY", "USER_PROVIDED_LYRICS", "LIFE_EVENT")
                        .contains(reference.source().sourceType()))
                .map(UserReference::content).toList();
    }
}
