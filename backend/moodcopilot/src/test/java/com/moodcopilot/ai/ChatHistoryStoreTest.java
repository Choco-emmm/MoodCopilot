package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.dto.CustomChatMessage;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatHistoryStoreTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<String> storage = new AtomicReference<>();
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);

    @SuppressWarnings("unchecked")
    private ChatHistoryStore store() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(call -> storage.get());
        doAnswer(call -> { storage.set(call.getArgument(1)); return null; })
                .when(values).set(anyString(), anyString(), any(Duration.class));
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(call -> {
                    String expected = "0".equals(call.getArgument(2)) ? null : call.getArgument(3);
                    String current = storage.get();
                    if (!java.util.Objects.equals(expected, current)) return 0L;
                    return storage.compareAndSet(current, call.getArgument(4)) ? 1L : 0L;
                });
        return new ChatHistoryStore(redis, mapper);
    }

    private CustomChatMessage message(String id, String role, String content) {
        return new CustomChatMessage(id, role, content, null, null, null, null, null, null);
    }

    @Test
    void delayedAnnotationCannotEraseACompletedTurn() throws Exception {
        ChatHistoryStore store = store();
        store.append(42, message("u1", "user", "hello"));
        CountDownLatch read = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean delayOnce = new AtomicBoolean(true);
        when(values.get(anyString())).thenAnswer(call -> {
            String snapshot = storage.get();
            if (Thread.currentThread().getName().equals("annotation") && delayOnce.getAndSet(false)) {
                read.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
            }
            return snapshot;
        });
        try (var executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "annotation"))) {
            var pending = executor.submit(() -> store.annotate(42, List.of(Map.of(
                    "id", "u1", "role", "user", "content", "hello", "quoteRef", Map.of("content", "quoted")))));
            try {
                assertTrue(read.await(5, TimeUnit.SECONDS));
                store.append(42, message("a1", "assistant", "reply"));
            } finally { release.countDown(); }
            pending.get(5, TimeUnit.SECONDS);
        }
        var rows = mapper.readTree(storage.get());
        assertEquals(2, rows.size(), "late annotation must preserve the authoritative assistant");
        assertEquals("reply", rows.get(1).path("content").asText());
        assertEquals("quoted", rows.get(0).path("quoteRef").path("content").asText());
    }

    @Test
    void appendPreservesAnnotationsAndDistinguishesRepeatedTurns() throws Exception {
        ChatHistoryStore store = store();
        store.annotate(42, List.of(Map.of("role", "user", "content", "hello", "quoteRef", Map.of("content", "quoted"))));
        store.append(42, message("u1", "user", "hello"));
        store.append(42, message("a1", "assistant", "reply"));
        store.append(42, message("u2", "user", "hello"));
        store.append(42, message("a2", "assistant", "reply"));
        assertEquals(4, mapper.readTree(storage.get()).size());
        assertEquals("quoted", mapper.readTree(storage.get()).get(0).path("quoteRef").path("content").asText());
        store.trimToRecent(42, 2);
        assertEquals("u2", mapper.readTree(storage.get()).get(0).path("id").asText());
    }

    @Test
    void sustainedConflictsFailWithoutOverwritingHistory() {
        ChatHistoryStore store = store();
        storage.set("[]");
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(0L);
        assertThrows(IllegalStateException.class, () -> store.append(42, message("u1", "user", "hello")));
        assertEquals("[]", storage.get());
        verify(redis, times(12)).execute(any(RedisScript.class), eq(List.of("chat:msgs:42")),
                anyString(), anyString(), anyString(), eq("604800"));
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void invalidStoredHistoryIsNotReplacedWithAnEmptySnapshot() {
        ChatHistoryStore store = store();
        storage.set("invalid-json");
        assertThrows(IllegalStateException.class, () -> store.annotate(42, List.of()));
        assertEquals("invalid-json", storage.get());
        verify(redis, never()).execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString());
    }
}
