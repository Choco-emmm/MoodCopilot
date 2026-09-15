package com.moodcopilot.langgraph;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 内存检查点。语义与 {@link RedisCheckpointSaver} 对齐：条目封顶、{@code list} 倒序（最新在前）。
 * <p>
 * 关键是**进出都真的过一遍 JSON**。直接存对象引用的话，state 里那些只靠「同一个实例」
 * 才对得上的东西（嵌套 Map / List 的具体类型、数字的装箱类型）就永远不会被检验，
 * 而生产上它们必须活着穿过一次序列化 —— 测试替身比真身宽松，正是让这类 bug 溜到线上的方式。
 */
public class InMemoryCheckpointSaver implements BaseCheckpointSaver {

    private static final int MAX_CHECKPOINTS = 50;

    private static final TypeReference<Map<String, Object>> PAYLOAD = new TypeReference<>() {
    };

    /** 每个 thread 一条列表，只保留最近 MAX_CHECKPOINTS 条 —— 与 Redis 那边的 LTRIM 一致。 */
    private final Map<String, List<Checkpoint>> store = new HashMap<>();

    /** 记录每一次 put，供断言「中断时确实落过检查点」。 */
    private final List<Checkpoint> written = new ArrayList<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Checkpoint> written() {
        return List.copyOf(written);
    }

    @Override
    public List<Checkpoint> list(RunnableConfig config) {
        List<Checkpoint> rows = store.get(threadId(config));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Checkpoint> newestFirst = new ArrayList<>(rows);
        Collections.reverse(newestFirst);
        return newestFirst;
    }

    @Override
    public Optional<Checkpoint> get(RunnableConfig config) {
        List<Checkpoint> rows = store.get(threadId(config));
        return (rows == null || rows.isEmpty()) ? Optional.empty() : Optional.of(rows.get(rows.size() - 1));
    }

    @Override
    public RunnableConfig put(RunnableConfig config, Checkpoint checkpoint) throws Exception {
        Checkpoint stored = roundTrip(checkpoint);
        List<Checkpoint> rows = store.computeIfAbsent(threadId(config), key -> new ArrayList<>());
        rows.add(stored);
        if (rows.size() > MAX_CHECKPOINTS) {
            rows.subList(0, rows.size() - MAX_CHECKPOINTS).clear();
        }
        written.add(stored);
        return config;
    }

    @Override
    public Tag release(RunnableConfig config) {
        List<Checkpoint> removed = store.remove(threadId(config));
        return new Tag(threadId(config), removed == null ? List.of() : List.copyOf(removed));
    }

    /** 与 {@link RedisCheckpointSaver} 的 write/read 同一套形状。 */
    private Checkpoint roundTrip(Checkpoint checkpoint) throws Exception {
        String json = objectMapper.writeValueAsString(Map.of(
                "id", checkpoint.getId(),
                "nodeId", checkpoint.getNodeId(),
                "nextNodeId", checkpoint.getNextNodeId(),
                "state", checkpoint.getState()));
        Map<String, Object> payload = objectMapper.readValue(json, PAYLOAD);
        @SuppressWarnings("unchecked")
        Map<String, Object> state = payload.get("state") instanceof Map
                ? (Map<String, Object>) payload.get("state") : Map.of();
        return Checkpoint.builder()
                .id((String) payload.get("id"))
                .nodeId((String) payload.get("nodeId"))
                .nextNodeId((String) payload.get("nextNodeId"))
                .state(state)
                .build();
    }
}
