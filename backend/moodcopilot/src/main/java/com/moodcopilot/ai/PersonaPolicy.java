package com.moodcopilot.ai;

import java.util.List;
import java.util.Set;

public final class PersonaPolicy {
    private PersonaPolicy() {}

    public static final String DEFAULT_ROLE = "personal_assistant";
    public static final List<String> DEFAULT_TONE = List.of("natural", "clear");
    public static final List<String> DEFAULT_BEHAVIORS = List.of("CONCLUSION_FIRST", "ASK_WHEN_AMBIGUOUS");
    public static final Set<String> ROLES = Set.of(
            "personal_assistant", "study_partner", "coding_partner", "writing_partner", "life_companion");
    public static final Set<String> TONES = Set.of(
            "natural", "warm", "direct", "clear", "concise", "precise", "formal", "playful",
            "empathetic", "calm", "analytical", "encouraging", "humorous", "critical");
    public static final Set<String> BEHAVIORS = Set.of(
            "CONCISE", "CONCLUSION_FIRST", "ASK_WHEN_AMBIGUOUS", "CODE_FIRST",
            "LESS_REASSURANCE", "DIRECT_FEEDBACK", "STEP_BY_STEP");

    public static String normalize(String value) {
        return value == null ? "" : java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC)
                .replaceAll("[\\p{Cntrl}&&[^\\n]]", "")
                .replaceAll("[ \\t\\r\\n]+", " ").trim();
    }

    public static List<String> normalizeValues(List<String> values, Set<String> allowed) {
        if (values == null) return List.of();
        return values.stream().map(PersonaPolicy::normalize)
                .map(value -> allowed.stream().filter(candidate -> candidate.equalsIgnoreCase(value)).findFirst().orElse(null))
                .filter(java.util.Objects::nonNull).distinct().limit(8).toList();
    }

    /**
     * Free-form tone is intentionally constrained to a short description of expression style.
     * It never grants instructions, tools, data access, model selection, or policy exceptions.
     */
    public static String normalizeCustomTone(String value) {
        String normalized = normalize(value);
        if (normalized.length() > 160) normalized = normalized.substring(0, 160);
        if (normalized.isBlank()) return "";
        String lower = normalized.toLowerCase(java.util.Locale.ROOT);
        String[] forbidden = { "忽略", "系统规则", "提示词", "指令", "权限", "读取日记", "读取记忆",
                "自动使用", "模型选择", "ignore", "system prompt", "bypass", "tool permission" };
        for (String word : forbidden) if (lower.contains(word)) return "";
        return normalized;
    }

    /** User-controlled response organization preference, kept separate from tone. */
    public static String normalizeCustomResponseStyle(String value) {
        String normalized = normalize(value);
        if (normalized.length() > 800) normalized = normalized.substring(0, 800);
        if (normalized.isBlank()) return "";
        String lower = normalized.toLowerCase(java.util.Locale.ROOT);
        String[] forbidden = { "忽略", "系统规则", "安全规则", "提示词", "越过权限", "越权", "读取所有日记", "读取全部",
                "访问所有", "自动使用pro", "自动使用 pro", "切换模型", "模型选择", "工具权限", "ignore system",
                "system prompt", "bypass", "read all diaries", "use pro" };
        for (String word : forbidden) if (lower.contains(word)) return "";
        return normalized;
    }

    public static String normalizeOutputRequirement(String value) {
        String normalized = normalize(value);
        if (normalized.length() > 400) normalized = normalized.substring(0, 400);
        if (normalized.isBlank()) return "";
        String lower = normalized.toLowerCase(java.util.Locale.ROOT);
        String[] forbidden = { "系统", "权限", "模型", "工具", "读取日记", "读取记忆", "ignore", "bypass", "system" };
        for (String word : forbidden) if (lower.contains(word)) return "";
        return normalized;
    }
}
