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
        return MemoryWritePolicy.text(value).replace(" ", "").toLowerCase(Locale.ROOT);
    }

    /** Versioned storage fingerprint avoids collisions with legacy punctuation-stripped values. */
    static String fingerprint(String value) {
        return "v2:" + MemoryWritePolicy.hash(normalizeValue(value));
    }
}
