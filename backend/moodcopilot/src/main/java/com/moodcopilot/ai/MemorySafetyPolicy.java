package com.moodcopilot.ai;

import java.util.Locale;
import java.util.Set;

/** Prevents transient safety signals from becoming permanent core profile facts. */
public final class MemorySafetyPolicy {
    private static final Set<String> SUPPORTED_MEMORY_TYPES = Set.of(
            "preference", "relationship", "habit", "event", "short_term_state", "pattern");
    private static final String[] SAFETY_TERMS = {
            "自杀", "自残", "轻生", "想死", "不想活", "结束生命", "伤害自己", "割腕", "跳楼",
            "自杀意念", "严重心理危机", "心理危机", "危机干预"
    };

    private MemorySafetyPolicy() {
    }

    public static boolean isSupportedType(String memoryType) {
        return memoryType != null && SUPPORTED_MEMORY_TYPES.contains(memoryType.toLowerCase(Locale.ROOT));
    }

    public static boolean isSafetyState(String attributeKey, String attributeValue) {
        String text = ((attributeKey == null ? "" : attributeKey) + " "
                + (attributeValue == null ? "" : attributeValue)).toLowerCase(Locale.ROOT);
        for (String term : SAFETY_TERMS) {
            if (text.contains(term)) return true;
        }
        return false;
    }

    public static String normalizeType(String requestedType, String attributeKey, String attributeValue) {
        return isSafetyState(attributeKey, attributeValue) ? "short_term_state" : requestedType;
    }

    public static boolean allowCore(String memoryType, String attributeKey, String attributeValue) {
        return !"short_term_state".equals(memoryType)
                && !isSafetyState(attributeKey, attributeValue);
    }
}
