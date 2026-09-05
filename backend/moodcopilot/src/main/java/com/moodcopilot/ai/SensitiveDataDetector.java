package com.moodcopilot.ai;

import java.util.regex.Pattern;

/**
 * Detects values that must never become profile memory or profile RAG content.
 * The detector is deliberately conservative and does not modify the original diary.
 */
public final class SensitiveDataDetector {
    private static final String MASK = "[已隐藏敏感信息]";
    private static final Pattern[] SENSITIVE_PATTERNS = {
            Pattern.compile("-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z0-9 ]*PRIVATE KEY-----", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)\\b(?:api[_ -]?key|access[_ -]?key|secret|password|passwd|token|session[_ -]?token|authorization|bearer)\\s*[:=]\\s*[^\\s,;]+"),
            Pattern.compile("(?i)\\b(?:sk-[a-z0-9_-]{12,}|AKIA[0-9A-Z]{12,}|eyJ[a-zA-Z0-9_-]{10,}\\.[a-zA-Z0-9_-]{10,}\\.[a-zA-Z0-9_-]{10,})\\b"),
            Pattern.compile("(?i)\\b(?:ghp|gho|ghs|ghu|github_pat|xox[baprs]|ya29)_[a-z0-9_-]{10,}\\b"),
            Pattern.compile("(?i)https?://[^\\s/@:]+:[^\\s/@]+@[^\\s]+"),
            Pattern.compile("(?i)https?://(?:localhost|127(?:\\.\\d{1,3}){3}|10(?:\\.\\d{1,3}){3}|192\\.168(?:\\.\\d{1,3}){2}|172\\.(?:1[6-9]|2\\d|3[01])(?:\\.\\d{1,3}){2})(?::\\d+)?[^\\s]*"),
            Pattern.compile("(?i)\\b(?:[a-z0-9-]+\\.)*(?:internal|intranet|local)\\b[^\\s]*"),
            Pattern.compile("(?<!\\d)(?:\\+?86[- ]?)?1[3-9]\\d{9}(?!\\d)"),
            Pattern.compile("(?i)\\b[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}\\b")
            ,Pattern.compile("(?i)(?:公司机密|商业机密|客户资料|客户信息|内部资料|confidential|proprietary|private repository)")
    };

    private SensitiveDataDetector() {
    }

    public static boolean containsSensitiveData(String value) {
        if (value == null || value.isBlank()) return false;
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            if (pattern.matcher(value).find()) return true;
        }
        return false;
    }

    /** Returns bounded, non-persistent text suitable for the memory model input. */
    public static String redact(String value) {
        if (value == null || value.isBlank()) return value == null ? "" : value;
        String result = value;
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            result = pattern.matcher(result).replaceAll(MASK);
        }
        return result;
    }

    public static boolean allowedForMemory(String attributeKey, String attributeValue, String evidence) {
        return !containsSensitiveData(attributeKey)
                && !containsSensitiveData(attributeValue)
                && !containsSensitiveData(evidence);
    }
}
