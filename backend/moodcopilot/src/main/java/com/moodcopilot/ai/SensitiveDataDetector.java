package com.moodcopilot.ai;

import java.util.regex.Pattern;

/**
 * Detects values that must never become profile memory or profile RAG content.
 * The detector is deliberately conservative and does not modify the original diary.
 */
public final class SensitiveDataDetector {
    private static final String MASK = "[已隐藏敏感信息]";
    public enum Category { CREDENTIAL, PRIVATE_URL, CONTACT, RESTRICTED_MARKER }
    private static final Pattern CHINESE_CREDENTIAL = Pattern.compile(
            "(?:密码|口令|密钥|私钥|令牌|访问凭证)[\"'“”‘’`]?\\s*(?:是|为|：|:|=)\\s*"
                    + "(?:\"[^\"\\r\\n]+\"|'[^'\\r\\n]+'|“[^”\\r\\n]+”|‘[^’\\r\\n]+’|`[^`\\r\\n]+`|[\\p{L}\\p{N}_./+@!#$%&*?=-]{3,})");
    private static final Pattern[] SENSITIVE_PATTERNS = {
            Pattern.compile("-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z0-9 ]*PRIVATE KEY-----", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)\\b(?:api[_ -]?key|access[_ -]?key|private[_ -]?key|client[_ -]?secret|access[_ -]?token|refresh[_ -]?token|secret|password|passwd|token|session[_ -]?token|authorization|bearer)[\"']?\\s*[:=]\\s*[\"']?(?:(?:bearer|basic)\\s+)?[^\\s,;，。；、<>\"'}]+|\\bbearer\\s+[a-z0-9._~+/-]{8,}"),
            Pattern.compile("(?i)\\b(?:sk-[a-z0-9_-]{12,}|AKIA[0-9A-Z]{12,}|eyJ[a-zA-Z0-9_-]{10,}\\.[a-zA-Z0-9_-]{10,}\\.[a-zA-Z0-9_-]{10,})\\b"),
            Pattern.compile("(?i)\\b(?:ghp|gho|ghs|ghu|github_pat|xox[baprs]|ya29)_[a-z0-9_-]{10,}\\b"),
            Pattern.compile("(?i)https?://[^\\s/@:<>\"']+:[^\\s/@<>\"']+@[^\\s<>\"']+|https?://[^\\s<>\"']+(?:\\?|&(?:amp;)?)(?:access_token|token|api_key|signature|sig|x-amz-signature|x-goog-signature|credential)=[^\\s<>\"']+"),
            Pattern.compile("(?i)https?://(?:localhost|127(?:\\.\\d{1,3}){3}|10(?:\\.\\d{1,3}){3}|192\\.168(?:\\.\\d{1,3}){2}|172\\.(?:1[6-9]|2\\d|3[01])(?:\\.\\d{1,3}){2})(?::\\d+)?[^\\s<>\"']*"),
            Pattern.compile("(?i)\\b(?:[a-z0-9-]+\\.)+(?:internal|intranet|local)\\b[^\\s<>\"']*|https?://(?:internal|intranet|local)\\b[^\\s<>\"']*|(?:私有链接|私有地址|内部链接|私密链接|内部地址)[：:=为是\\s]+https?://[^\\s<>\"']+"),
            Pattern.compile("(?<!\\d)(?:\\+?86[- ]?)?1[3-9]\\d(?:[- ]?\\d){8}(?!\\d)"),
            Pattern.compile("[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}.-]+\\.[\\p{L}]{2,}")
            ,Pattern.compile("(?i)(?:公司机密|商业机密|客户资料|客户信息|内部资料|confidential|proprietary|private repository)")
    };

    private SensitiveDataDetector() {
    }

    public static boolean containsSensitiveData(String value) {
        return !classify(value).isEmpty();
    }

    /** Returns bounded, non-persistent text suitable for the memory model input. */
    public static String redact(String value) {
        if (value == null || value.isBlank()) return value == null ? "" : value;
        String result = CHINESE_CREDENTIAL.matcher(value).replaceAll(MASK);
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            result = pattern.matcher(result).replaceAll(MASK);
        }
        return result;
    }

    public static java.util.Set<Category> classify(String value) {
        if (value == null || value.isBlank()) return java.util.Set.of();
        java.util.EnumSet<Category> result = java.util.EnumSet.noneOf(Category.class);
        if (CHINESE_CREDENTIAL.matcher(value).find()) result.add(Category.CREDENTIAL);
        for (int i = 0; i < SENSITIVE_PATTERNS.length; i++) {
            if (SENSITIVE_PATTERNS[i].matcher(value).find()) result.add(i <= 3 ? Category.CREDENTIAL
                    : i <= 6 ? Category.PRIVATE_URL : i <= 8 ? Category.CONTACT : Category.RESTRICTED_MARKER);
        }
        return java.util.Set.copyOf(result);
    }

    /** Outbound model copy: credentials/private URLs, without broadening contact restrictions. */
    public static String redactCredentials(String value) {
        if (value == null) return "";
        String result = CHINESE_CREDENTIAL.matcher(value).replaceAll(MASK);
        for (int i = 0; i <= 6; i++) result = SENSITIVE_PATTERNS[i].matcher(result).replaceAll(MASK);
        return result;
    }

    public static boolean allowedForMemory(String attributeKey, String attributeValue, String evidence) {
        return !containsSensitiveData(attributeKey)
                && !containsSensitiveData(attributeValue)
                && !containsSensitiveData(evidence);
    }
}
