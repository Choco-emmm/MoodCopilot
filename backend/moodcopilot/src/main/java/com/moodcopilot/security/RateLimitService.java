package com.moodcopilot.security;

import com.moodcopilot.common.RateLimitException;
import com.moodcopilot.entity.UserEntity;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RateLimitService {

    private final StringRedisTemplate redis;

    public RateLimitService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public enum AiApiType {
        CHAT(ResetPeriod.DAILY),
        ANALYSIS(ResetPeriod.DAILY),
        REASONING(ResetPeriod.DAILY),
        RESONANCE(ResetPeriod.DAILY),
        REPORT(ResetPeriod.MONTHLY),
        IMAGE_UPLOAD(ResetPeriod.DAILY),
        IMAGE_ANALYSIS(ResetPeriod.DAILY);

        public enum ResetPeriod { DAILY, MONTHLY }

        private final ResetPeriod resetPeriod;

        AiApiType(ResetPeriod resetPeriod) {
            this.resetPeriod = resetPeriod;
        }

        public ResetPeriod getResetPeriod() {
            return resetPeriod;
        }
    }

    // Row 0 = Pro, Row 1..6 = Lv.1..Lv.6
    // Col order must match AiApiType enum: CHAT, ANALYSIS, REASONING, RESONANCE, REPORT, IMAGE_UPLOAD, IMAGE_ANALYSIS
    private static final int[][] QUOTA = {
            {150, 50,  30,  50,  30,  50, 50},   // Pro
            {15,  5,   2,   0,   0,   3,  2},    // Lv.1
            {25,  8,   4,   3,   2,   5,  3},    // Lv.2
            {35,  12,  6,   5,   4,   8,  5},    // Lv.3
            {45,  16,  8,   8,   7,   12, 8},    // Lv.4
            {55,  20,  10,  10,  11,  16, 12},   // Lv.5
            {65,  25,  12,  12,  16,  20, 15},   // Lv.6
    };

    public static int getDynamicLimit(AiApiType type, Integer level, boolean isPro) {
        int safeLevel = (level != null) ? level : 1;
        int row = isPro ? 0 : Math.clamp(safeLevel, 1, 6);
        return QUOTA[row][type.ordinal()];
    }

    private static boolean isPro(UserEntity user) {
        return user.getProExpireTime() != null && user.getProExpireTime().isAfter(LocalDateTime.now());
    }

    private static boolean isAdmin(UserEntity user) {
        if (user == null || user.getRole() == null) {
            return false;
        }
        String role = user.getRole().toUpperCase();
        return role.equals("ADMIN") || role.equals("ROLE_ADMIN");
    }

    // ── tryAcquire ──

    /** Convenience overload for system/scheduler calls: defaults to non-Pro Lv.1. */
    public void tryAcquire(Long userId, AiApiType type) {
        int limit = getDynamicLimit(type, 1, false);
        tryAcquireInternal(userId, type, limit);
    }

    /** Main entry point for user-initiated calls. */
    public void tryAcquire(UserEntity user, AiApiType type) {
        if (isAdmin(user)) {
            return;
        }
        int limit = getDynamicLimit(type, user.getLevel(), isPro(user));
        tryAcquireInternal(user.getId(), type, limit);
    }

    private void tryAcquireInternal(Long userId, AiApiType type, int limit) {
        if (limit <= 0) {
            throw new RateLimitException(type.name(),
                    "当前等级暂未解锁" + typeLabel(type) + "功能，升级后即可使用～");
        }
        String key = buildKey(userId, type);
        Long count = redis.opsForValue().increment(key);
        if (count == 1) {
            long secondsUntilReset = secondsUntilReset(type.getResetPeriod());
            redis.expire(key, Duration.ofSeconds(secondsUntilReset));
        }
        if (count > limit) {
            boolean isMonthly = type.getResetPeriod() == AiApiType.ResetPeriod.MONTHLY;
            String msg = isMonthly
                    ? "本月" + typeLabel(type) + "次数已用完（" + limit + "次/月），下个月再来吧～"
                    : "今日" + typeLabel(type) + "次数已用完（" + limit + "次/天），明天再来吧～";
            throw new RateLimitException(type.name(), msg);
        }
    }

    // ── getRemaining ──

    public long getRemaining(Long userId, AiApiType type) {
        return getRemainingInternal(userId, type, getDynamicLimit(type, 1, false));
    }

    public long getRemaining(UserEntity user, AiApiType type) {
        if (isAdmin(user)) {
            return 9999L;
        }
        int limit = getDynamicLimit(type, user.getLevel(), isPro(user));
        return getRemainingInternal(user.getId(), type, limit);
    }

    private long getRemainingInternal(Long userId, AiApiType type, int limit) {
        try {
            String key = buildKey(userId, type);
            String val = redis.opsForValue().get(key);
            long used = parseUsedCount(val);
            return Math.max(0, limit - used);
        } catch (Exception ignored) {
            return limit;
        }
    }

    // ── getAllRemaining ──

    public Map<String, Long> getAllRemaining(Long userId) {
        return getAllRemaining(userId, 1, false);
    }

    public Map<String, Long> getAllRemaining(UserEntity user) {
        if (isAdmin(user)) {
            Map<String, Long> result = new LinkedHashMap<>();
            for (AiApiType type : AiApiType.values()) {
                result.put(type.name(), 9999L);
            }
            return result;
        }
        return getAllRemaining(user.getId(), user.getLevel(), isPro(user));
    }

    private Map<String, Long> getAllRemaining(Long userId, Integer level, boolean isPro) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (AiApiType type : AiApiType.values()) {
            int limit = getDynamicLimit(type, level, isPro);
            result.put(type.name(), getRemainingInternal(userId, type, limit));
        }
        return result;
    }

    // ── build helpers ──

    private long parseUsedCount(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        String value = raw.trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1);
        }
        return Long.parseLong(value);
    }

    private String buildKey(Long userId, AiApiType type) {
        String period = switch (type.getResetPeriod()) {
            case DAILY -> LocalDate.now().toString();
            case MONTHLY -> YearMonth.now().toString();
        };
        return "ratelimit:" + userId + ":" + period + ":" + type.name();
    }

    private long secondsUntilReset(AiApiType.ResetPeriod period) {
        return switch (period) {
            case DAILY -> LocalDateTime.now().until(
                    LocalDate.now().plusDays(1).atStartOfDay(), ChronoUnit.SECONDS);
            case MONTHLY -> LocalDateTime.now().until(
                    YearMonth.now().plusMonths(1).atDay(1).atStartOfDay(), ChronoUnit.SECONDS);
        };
    }

    private String typeLabel(AiApiType type) {
        return switch (type) {
            case CHAT -> "AI 聊天";
            case ANALYSIS -> "AI 分析";
            case REASONING -> "AI 深度思考";
            case RESONANCE -> "共鸣检索";
            case REPORT -> "报告生成";
            case IMAGE_UPLOAD -> "图片上传";
            case IMAGE_ANALYSIS -> "图片深度分析";
        };
    }
}
