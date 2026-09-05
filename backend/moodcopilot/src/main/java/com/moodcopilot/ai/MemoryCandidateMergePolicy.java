package com.moodcopilot.ai;

import java.util.Locale;

/** Strict, deterministic fingerprint matching for candidate values. */
final class MemoryCandidateMergePolicy {
    private MemoryCandidateMergePolicy() {
    }

    static boolean compatible(String left, String right) {
        String a = normalizeValue(left);
        String b = normalizeValue(right);
        return !a.isBlank() && a.equals(b);
    }

    static String normalizeValue(String value) {
        return (value == null ? "" : value.replaceAll("\\s+", "").trim().toLowerCase(Locale.ROOT))
                .replaceAll("[\\p{P}\\p{S}]", "");
    }
}
