package com.moodcopilot.langgraph;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 检查点的序列化层 —— 图那边一旦调 {@code put} 就必然经过这里，
 * 而它出问题的表现是「恢复时 state 少了一半字段」，很难从上游看出来。
 */
class RedisCheckpointSaverTest {

    private static final String KEY = "langgraph:checkpoint:t1";

    private final Map<String, List<String>> store = new HashMap<>();

    private ListOperations<String, String> listOps;
    private StringRedisTemplate redisTemplate;
    private RedisCheckpointSaver saver;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ListOperations<String, String> listOps = mock(ListOperations.class);

        when(listOps.rightPush(anyString(), anyString())).thenAnswer(invocation -> {
            List<String> rows = store.computeIfAbsent(invocation.getArgument(0), k -> new ArrayList<>());
            rows.add(invocation.getArgument(1));
            return (long) rows.size();
        });
        when(listOps.index(anyString(), anyLong())).thenAnswer(invocation -> {
            List<String> rows = store.get(invocation.getArgument(0));
            if (rows == null || rows.isEmpty()) {
                return null;
            }
            long index = invocation.getArgument(1);
            return rows.get(index < 0 ? rows.size() + (int) index : (int) index);
        });
        when(listOps.range(anyString(), anyLong(), anyLong())).thenAnswer(invocation -> {
            List<String> rows = store.get(invocation.getArgument(0));
            return rows == null ? List.of() : List.copyOf(rows);
        });

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(redisTemplate.delete(anyString())).thenAnswer(invocation -> {
            store.remove(invocation.getArgument(0));
            return Boolean.TRUE;
        });

        this.listOps = listOps;
        this.redisTemplate = redisTemplate;
        this.saver = new RedisCheckpointSaver(redisTemplate);
    }

    private static RunnableConfig config(String threadId) {
        return RunnableConfig.builder().threadId(threadId).build();
    }

    @Test
    void stateSurvivesARoundTripThroughRedis() throws Exception {
        Map<String, Object> state = new HashMap<>();
        state.put("loopCount", 2);
        state.put("reply", "吃了碗拉面");
        state.put("exposeReasoning", true);
        state.put("messages", List.of(Map.of("role", "user", "content", "在吗")));

        saver.put(config("t1"), Checkpoint.builder()
                .id("ck-1")
                .nodeId("agentNode")
                .nextNodeId("toolsNode")
                .state(state)
                .build());

        Checkpoint restored = saver.get(config("t1")).orElseThrow();

        assertEquals("ck-1", restored.getId());
        assertEquals("agentNode", restored.getNodeId());
        assertEquals("toolsNode", restored.getNextNodeId());
        assertEquals(state, restored.getState());
    }

    @Test
    void listReturnsTheNewestCheckpointFirst() throws Exception {
        // getStateHistory 约定「第一个是最新的」，顺序反了会让 lastStateOf 拿到起点
        saver.put(config("t1"), checkpoint("ck-1", "START", "agentNode"));
        saver.put(config("t1"), checkpoint("ck-2", "agentNode", "toolsNode"));

        List<Checkpoint> history = List.copyOf(saver.list(config("t1")));

        assertEquals(List.of("ck-2", "ck-1"), history.stream().map(Checkpoint::getId).toList());
        assertEquals("ck-2", saver.get(config("t1")).orElseThrow().getId());
    }

    @Test
    void anUnknownThreadHasNoCheckpoint() {
        assertTrue(saver.get(config("nobody")).isEmpty());
        assertTrue(saver.list(config("nobody")).isEmpty());
    }

    @Test
    void checkpointsAreCappedAndExpire() throws Exception {
        saver.put(config("t1"), checkpoint("ck-1", "START", "agentNode"));

        verify(listOps).trim(KEY, -50, -1);
        verify(redisTemplate).expire(KEY, Duration.ofDays(1));
    }

    @Test
    void aCorruptRowReadsAsNoCheckpointInsteadOfBlowingUp() throws Exception {
        // 宁可让这一轮从头发起，也不能把半截 JSON 当 state 喂回图里
        store.computeIfAbsent(KEY, k -> new ArrayList<>()).add("{ 这不是 json");

        assertTrue(saver.get(config("t1")).isEmpty());
        assertTrue(saver.list(config("t1")).isEmpty());
    }

    @Test
    void releaseReturnsWhatItDeleted() throws Exception {
        saver.put(config("t1"), checkpoint("ck-1", "START", "agentNode"));

        var tag = saver.release(config("t1"));

        assertEquals("t1", tag.threadId());
        assertEquals(1, tag.checkpoints().size());
        assertTrue(saver.get(config("t1")).isEmpty());
    }

    private static Checkpoint checkpoint(String id, String nodeId, String nextNodeId) {
        return Checkpoint.builder()
                .id(id)
                .nodeId(nodeId)
                .nextNodeId(nextNodeId)
                .state(Map.of("node", nodeId))
                .build();
    }

    @Test
    void deletingAThreadRemovesItsKey() throws Exception {
        saver.put(config("t1"), checkpoint("ck-1", "START", "agentNode"));
        assertFalse(store.getOrDefault(KEY, List.of()).isEmpty());

        saver.release(config("t1"));

        assertTrue(store.getOrDefault(KEY, List.of()).isEmpty());
    }
}
