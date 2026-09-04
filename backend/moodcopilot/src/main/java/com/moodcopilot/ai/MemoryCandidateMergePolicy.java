package com.moodcopilot.ai;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Conservative, deterministic compatibility check for same-key candidate values. */
final class MemoryCandidateMergePolicy {
    private MemoryCandidateMergePolicy() {
    }

    static boolean compatible(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        if (a.isBlank() || b.isBlank()) return false;
        if (a.equals(b) || a.contains(b) || b.contains(a)) return !oppositePolarity(a, b);
        if (oppositePolarity(a, b) || a.length() < 4 || b.length() < 4) return false;
        Set<String> leftBigrams = bigrams(a);
        Set<String> rightBigrams = bigrams(b);
        long overlap = leftBigrams.stream().filter(rightBigrams::contains).count();
        return overlap >= 2
                && overlap / (double) Math.min(leftBigrams.size(), rightBigrams.size()) >= .55
                && overlap / (double) Math.max(leftBigrams.size(), rightBigrams.size()) >= .40;
    }

    private static Set<String> bigrams(String value) {
        Set<String> result = new LinkedHashSet<>();
        for (int i = 0; i + 1 < value.length(); i++) result.add(value.substring(i, i + 2));
        return result;
    }

    private static boolean oppositePolarity(String left, String right) {
        return containsNegation(left) != containsNegation(right)
                || (left.contains("讨厌") != right.contains("讨厌"));
    }

    private static boolean containsNegation(String text) {
        return text.contains("不喜欢") || text.contains("不再") || text.contains("不是")
                || text.contains("否认") || text.contains("改为") || text.contains("转而");
    }

    private static String normalize(String value) {
        return (value == null ? "" : value.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT))
                .replaceAll("[\\p{P}\\p{S}]", "");
    }
}
