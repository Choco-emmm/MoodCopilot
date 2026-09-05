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
    private static final String[] TECHNICAL_TERMS = {
            "java", "javascript", "typescript", "python", "go语言", "golang", "c++", "c#", "redis", "mysql",
            "sql", "spring", "docker", "kubernetes", "后端", "前端", "程序员", "开发工程师", "软件工程师"
    };
    private static final String[] SKILL_CLAIM_TERMS = {
            "掌握", "熟悉", "精通", "擅长", "专家", "技能", "水平", "能力", "开发", "工程师", "程序员", "职业"
    };
    private static final String[] TRANSIENT_SCHEDULE_TERMS = {
            "时间", "日期", "开始", "结束", "截止", "安排", "预约", "考试", "课程", "上课", "会议", "面试",
            "出发", "行程", "旅行", "活动", "报名", "开学", "放假"
    };

    private MemorySafetyPolicy() {
    }

    public static boolean isSupportedType(String memoryType) {
        return memoryType != null && SUPPORTED_MEMORY_TYPES.contains(memoryType.toLowerCase(Locale.ROOT));
    }

    /**
     * Attribute keys are user-facing labels. New AI-extracted labels must be
     * Chinese, while the caller may separately allow an exact legacy key so old
     * records can still be echoed without renaming database identities.
     */
    public static boolean isChineseAttributeKey(String attributeKey) {
        if (attributeKey == null || attributeKey.isBlank()) return false;
        return attributeKey.matches(".*\\p{IsHan}.*")
                && !attributeKey.matches(".*[A-Za-z].*");
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

    /** Calendar-like facts belong to the event scheduler, not the long-term profile. */
    public static boolean isTransientScheduleFact(String memoryType, String attributeKey, String attributeValue) {
        if (!"event".equalsIgnoreCase(memoryType)) return false;
        String key = normalize(attributeKey);
        String value = normalize(attributeValue);
        if (key.isBlank() || value.isBlank()) return false;
        boolean scheduleKey = containsAny(key, TRANSIENT_SCHEDULE_TERMS);
        boolean dateValue = value.matches(".*\\d{4}[-年/]\\d{1,2}([-/月]\\d{1,2})?.*")
                || value.matches(".*\\d{1,2}月\\d{1,2}日.*")
                || value.matches(".*\\d{1,2}:\\d{2}.*");
        return scheduleKey && dateValue;
    }

    /**
     * Technical topics are not proof of a user's skill or occupation. Claims about
     * them require an explicit first-person statement instead of an inferred model label.
     */
    public static boolean isTechnicalKnowledgeClaim(String attributeKey, String attributeValue) {
        String text = normalize(attributeKey) + " " + normalize(attributeValue);
        return containsAny(text, TECHNICAL_TERMS) && containsAny(text, SKILL_CLAIM_TERMS);
    }

    public static boolean hasExplicitTechnicalBackground(String evidence) {
        String text = normalize(evidence);
        if (!containsAny(text, TECHNICAL_TERMS)) return false;
        return text.matches(".*我(?:是|从事|做|负责|主要做|的职业是).*(开发|工程师|程序员|后端|前端).*")
                || text.matches(".*我(?:掌握|熟悉|精通|擅长).*");
    }

    private static boolean containsAny(String text, String[] terms) {
        for (String term : terms) if (text.contains(term)) return true;
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }
}
