package com.moodcopilot.security;

import com.moodcopilot.common.RateLimitException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
        CHAT(30),
        ANALYSIS(10),
        REPORT(5),
        COACHING(10),
        ENCOURAGEMENT(15);

        private final int dailyLimit;

        AiApiType(int dailyLimit) { this.dailyLimit = dailyLimit; }

        public int getDailyLimit() { return dailyLimit; }
    }

    public void tryAcquire(Long userId, AiApiType type) {
        String key = buildKey(userId, type);
        Long count = redis.opsForValue().increment(key);
        if (count == 1) {
            long secondsUntilMidnight = LocalDateTime.now().until(
                    LocalDate.now().plusDays(1).atStartOfDay(), ChronoUnit.SECONDS);
            redis.expire(key, Duration.ofSeconds(secondsUntilMidnight));
        }
        if (count > type.getDailyLimit()) {
            throw new RateLimitException(type.name(), "今日" + typeLabel(type) + "次数已用完（" + type.getDailyLimit() + "次/天），明天再来吧～");
        }
    }

    public long getRemaining(Long userId, AiApiType type) {
        String key = buildKey(userId, type);
        String val = redis.opsForValue().get(key);
        long used = val != null ? Long.parseLong(val) : 0;
        return Math.max(0, type.getDailyLimit() - used);
    }

    public Map<String, Long> getAllRemaining(Long userId) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (AiApiType type : AiApiType.values()) {
            result.put(type.name(), getRemaining(userId, type));
        }
        return result;
    }

    private String buildKey(Long userId, AiApiType type) {
        return "ratelimit:" + userId + ":" + LocalDate.now() + ":" + type.name();
    }

    private String typeLabel(AiApiType type) {
        return switch (type) {
            case CHAT -> "聊天";
            case ANALYSIS -> "日记分析";
            case REPORT -> "报告生成";
            case COACHING -> "陪跑建议";
            case ENCOURAGEMENT -> "鼓励语";
        };
    }
}
