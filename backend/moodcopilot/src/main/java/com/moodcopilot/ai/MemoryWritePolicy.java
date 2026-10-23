package com.moodcopilot.ai;

import com.moodcopilot.entity.UserProfileMemoryEntity;
import java.text.Normalizer;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

/** One validation boundary for model proposals and authenticated user edits. */
public final class MemoryWritePolicy {
    public static final int EVIDENCE_LIMIT = 160;
    private MemoryWritePolicy() {}

    public static String text(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFC)
                .replaceAll("[\\p{Z}\\p{Cntrl}\\s]+", " ").strip();
    }

    public static int length(String value) { return value.codePointCount(0, value.length()); }

    public static String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(text(value).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public static String violation(String key, String value, String type, UserProfileMemoryEntity existing) {
        if (key == null || value == null || key.isBlank() || value.isBlank()) return "EMPTY_CONTENT";
        if (length(key) > 64 || length(value) > 500) return "CONTENT_TOO_LONG";
        boolean legacy = existing != null && key.equals(existing.getAttributeKey());
        if (!MemorySafetyPolicy.isChineseAttributeKey(key) && !legacy) return "INVALID_ATTRIBUTE_KEY";
        if (!MemorySafetyPolicy.isSupportedType(type)
                && !(legacy && java.util.Objects.equals(type, existing.getMemoryType()))) return "INVALID_MEMORY_TYPE";
        if (!SensitiveDataDetector.allowedForMemory(key, value, null) || containsPlaceholder(value)) return "RESTRICTED_CONTENT";
        return null;
    }

    public static void require(String key, String value, String type, UserProfileMemoryEntity existing) {
        String reason = violation(key, value, type, existing);
        if (reason != null) throw new ResponseStatusException(BAD_REQUEST, "RESTRICTED_CONTENT".equals(reason)
                ? "触发受限资料规则，请移除受限资料。" : "记忆内容未通过校验（" + reason + "）");
    }

    public static boolean evidence(String quote, String source) {
        String normalized = text(quote);
        return !normalized.isBlank() && length(normalized) <= EVIDENCE_LIMIT && !containsPlaceholder(normalized)
                && SensitiveDataDetector.allowedForMemory(null, null, normalized) && text(source).contains(normalized);
    }

    public static boolean containsPlaceholder(String value) {
        return value != null && (value.contains("[已隐藏敏感信息]") || value.contains("[已隐藏凭证]"));
    }
}
