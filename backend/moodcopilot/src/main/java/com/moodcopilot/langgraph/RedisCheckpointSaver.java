package com.moodcopilot.langgraph;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 图检查点落到现有的 {@link StringRedisTemplate}。
 * <p>
 * 用 Redis List 而不是 Hash 存「最新的那一条」：{@code getStateHistory} 要按时间倒序看快照，
 * 只留一条就没法回溯到「用户批准前」那一刻的 state。条目数封顶、整体带 TTL，
 * 与 {@code chat:run:*} 那套事件表的生命周期保持一致。
 * <p>
 * 状态值必须是 JSON 原生类型（String / Number / Boolean / List / Map）。
 * 这不是本类的额外约束 —— 图自己的 {@code ObjectStreamStateSerializer} 也是拿
 * {@code ObjectOutputStream} 写 state，非可序列化对象在那里就会炸。
 */
@Component
public class RedisCheckpointSaver implements BaseCheckpointSaver {

    private static final String KEY_PREFIX = "langgraph:checkpoint:";

    /** 与 chat:run:* 事件表同一天过期：检查点只服务于「跑到一半的那次 run」。 */
    private static final Duration TTL = Duration.ofDays(1);

    /** 一次 run 的节点轮次有限，留够回溯空间即可，防止异常循环把 Redis 撑爆。 */
    private static final int MAX_CHECKPOINTS = 50;

    private static final TypeReference<Map<String, Object>> PAYLOAD = new TypeReference<>() {
    };

    private final StringRedisTemplate redisTemplate;

    /**
     * 专用 ObjectMapper，不吃 Spring 那个全局的：检查点格式由本类独占，
     * 不该因为别处注册了模块或改了可见性配置而悄悄变样。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RedisCheckpointSaver(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String key(RunnableConfig config) {
        return KEY_PREFIX + threadId(config);
    }

    /** getStateHistory 约定「第一个元素是最新的」，所以这里倒序返回。 */
    @Override
    public List<Checkpoint> list(RunnableConfig config) {
        List<String> rows = redisTemplate.opsForList().range(key(config), 0, -1);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Checkpoint> checkpoints = new ArrayList<>(rows.size());
        for (String row : rows) {
            Checkpoint checkpoint = read(row);
            if (checkpoint != null) {
                checkpoints.add(checkpoint);
            }
        }
        Collections.reverse(checkpoints);
        return checkpoints;
    }

    @Override
    public Optional<Checkpoint> get(RunnableConfig config) {
        return Optional.ofNullable(redisTemplate.opsForList().index(key(config), -1))
                .map(this::read);
    }

    @Override
    public RunnableConfig put(RunnableConfig config, Checkpoint checkpoint) throws Exception {
        String key = key(config);
        redisTemplate.opsForList().rightPush(key, write(checkpoint));
        redisTemplate.opsForList().trim(key, -MAX_CHECKPOINTS, -1);
        redisTemplate.expire(key, TTL);
        return config;
    }

    @Override
    public Tag release(RunnableConfig config) throws Exception {
        List<Checkpoint> released = list(config);
        redisTemplate.delete(key(config));
        return new Tag(threadId(config), released);
    }

    private String write(Checkpoint checkpoint) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "id", checkpoint.getId(),
                "nodeId", checkpoint.getNodeId(),
                "nextNodeId", checkpoint.getNextNodeId(),
                "state", checkpoint.getState()));
    }

    /** 读坏了当成没有检查点：宁可让这一轮从头发起，也不能把坏数据当 state 喂回图里。 */
    private Checkpoint read(String row) {
        try {
            Map<String, Object> payload = objectMapper.readValue(row, PAYLOAD);
            return Checkpoint.builder()
                    .id((String) payload.get("id"))
                    .nodeId((String) payload.get("nodeId"))
                    .nextNodeId((String) payload.get("nextNodeId"))
                    .state(stateOf(payload))
                    .build();
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stateOf(Map<String, Object> payload) {
        Object state = payload.get("state");
        return (state instanceof Map) ? (Map<String, Object>) state : Map.of();
    }
}
