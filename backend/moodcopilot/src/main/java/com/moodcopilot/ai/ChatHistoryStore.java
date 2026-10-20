package com.moodcopilot.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.dto.CustomChatMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/** Persist history edits against the latest stored rows, preserving client annotations. */
final class ChatHistoryStore {
    private static final String PREFIX = "chat:msgs:";
    private static final int MAX_UPDATE_ATTEMPTS = 12;
    private static final DefaultRedisScript<Long> COMPARE_AND_SET = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if ARGV[1] == '0' then
                if current then return 0 end
            elseif current ~= ARGV[2] then
                return 0
            end
            redis.call('SET', KEYS[1], ARGV[3], 'EX', ARGV[4])
            return 1
            """, Long.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    ChatHistoryStore(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    List<Map<String, Object>> annotate(long conversationId, List<Map<String, Object>> incoming) {
        return update(conversationId, stored -> ChatService.mergeHistory(stored, incoming));
    }

    void append(long conversationId, CustomChatMessage message) {
        Map<String, Object> row = mapper.convertValue(message, new TypeReference<>() {});
        update(conversationId, stored -> {
            List<Map<String, Object>> rows = new ArrayList<>(stored);
            for (int i = 0; i < rows.size(); i++) {
                Map<String, Object> existing = rows.get(i);
                boolean sameId = message.id() != null && message.id().equals(existing.get("id"));
                boolean optimisticTail = i == rows.size() - 1
                        && message.role().equals(existing.get("role"))
                        && message.content() != null && message.content().equals(existing.get("content"));
                if (sameId || optimisticTail) {
                    Map<String, Object> enriched = new LinkedHashMap<>(existing);
                    enriched.putAll(row);
                    rows.set(i, enriched);
                    return rows;
                }
            }
            rows.add(row);
            return rows;
        });
    }

    void trimToRecent(long conversationId, int limit) {
        update(conversationId, stored -> new ArrayList<>(stored.subList(Math.max(0, stored.size() - limit), stored.size())));
    }

    private List<Map<String, Object>> update(long conversationId, UnaryOperator<List<Map<String, Object>>> operation) {
        try {
            String key = PREFIX + conversationId;
            for (int attempt = 0; attempt < MAX_UPDATE_ATTEMPTS; attempt++) {
                String current = redis.opsForValue().get(key);
                List<Map<String, Object>> stored = current == null || current.isBlank()
                        ? List.of() : mapper.readValue(current, new TypeReference<>() {});
                List<Map<String, Object>> next = operation.apply(stored);
                Long written = redis.execute(COMPARE_AND_SET, List.of(key), current == null ? "0" : "1",
                        current == null ? "" : current, mapper.writeValueAsString(next),
                        String.valueOf(Duration.ofDays(7).toSeconds()));
                if (Long.valueOf(1L).equals(written)) return next;
            }
            throw new IllegalStateException("聊天历史更新冲突，请重试");
        } catch (Exception error) {
            throw new IllegalStateException("聊天历史更新失败", error);
        }
    }
}
