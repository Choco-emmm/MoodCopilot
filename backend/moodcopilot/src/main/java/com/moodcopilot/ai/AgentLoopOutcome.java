package com.moodcopilot.ai;

import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次 Agent Loop 的结果视图。
 * <p>
 * 「什么算正文」只有这里一份实现：{@link #reply()} 按构造不含推理分片，
 * 所以调用方无论走哪条路径、读哪个累加器，都不会把 [[REASONING]] 写进历史。
 * 累加发生在流结束后才可读。
 */
public final class AgentLoopOutcome {

    /** DeepSeekClient 用这个前缀把 reasoning_content 复用成 TextChunk。 */
    static final String REASONING_MARKER = "[[REASONING]]";

    /** 摊平进图状态时用的键。恢复要能拼回同一个累加器，两边必须对得上。 */
    private static final String SNAPSHOT_REPLY = "accumulatedReply";
    private static final String SNAPSHOT_REASONING = "accumulatedReasoning";
    private static final String SNAPSHOT_TOOL_CALL_COUNT = "accumulatedToolCallCount";
    private static final String SNAPSHOT_TOOL_REFERENCES = "accumulatedToolReferences";
    private static final String SNAPSHOT_PAUSED = "paused";
    private static final String SNAPSHOT_PENDING_APPROVAL = "pendingApproval";

    private final StringBuilder reply = new StringBuilder();
    private final StringBuilder reasoning = new StringBuilder();
    private final List<Map<String, String>> toolReferences = Collections.synchronizedList(new ArrayList<>());
    private volatile int toolCallCount;
    private volatile boolean paused;
    private volatile Map<String, Object> pendingApproval = Map.of();
    private Flux<String> chunks = Flux.empty();

    void attachChunks(Flux<String> chunks) {
        this.chunks = chunks == null ? Flux.empty() : chunks;
    }

    void appendText(String chunk) {
        if (chunk == null) {
            return;
        }
        if (chunk.startsWith(REASONING_MARKER)) {
            reasoning.append(chunk.substring(REASONING_MARKER.length()));
        } else {
            reply.append(chunk);
        }
    }

    void recordToolCalls(int count, List<Map<String, String>> references) {
        toolCallCount += count;
        if (references != null && !references.isEmpty()) {
            toolReferences.addAll(references);
        }
    }

    void pause(Map<String, Object> approval) {
        this.paused = true;
        this.pendingApproval = approval == null ? Map.of() : Map.copyOf(approval);
    }

    /** 用户表过态、工具已经跑完：暂停态要收掉，否则恢复出来的 outcome 会一直自称「待批准」。 */
    void clearPause() {
        this.paused = false;
        this.pendingApproval = Map.of();
    }

    /**
     * 摊平成可进检查点的 JSON 原生值。
     * <p>
     * 每次节点收尾都要调用：图在每个节点前会深拷贝 state，所以累加结果必须写成 state 的一部分，
     * 否则「暂停 → 用户批准 → 恢复」时，暂停前已经推给客户端的那段正文在 state 里没有痕迹。
     */
    Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put(SNAPSHOT_REPLY, reply.toString());
        snapshot.put(SNAPSHOT_REASONING, reasoning.toString());
        snapshot.put(SNAPSHOT_TOOL_CALL_COUNT, toolCallCount);
        snapshot.put(SNAPSHOT_TOOL_REFERENCES, List.copyOf(toolReferences));
        snapshot.put(SNAPSHOT_PAUSED, paused);
        snapshot.put(SNAPSHOT_PENDING_APPROVAL, pendingApproval.isEmpty() ? null : Map.copyOf(pendingApproval));
        return snapshot;
    }

    /** 从检查点的 state 重建累加器；没有痕迹时等价于一个空 outcome。 */
    static AgentLoopOutcome restore(Map<String, Object> state) {
        AgentLoopOutcome outcome = new AgentLoopOutcome();
        if (state == null) {
            return outcome;
        }
        outcome.reply.append(asText(state.get(SNAPSHOT_REPLY)));
        outcome.reasoning.append(asText(state.get(SNAPSHOT_REASONING)));
        Object count = state.get(SNAPSHOT_TOOL_CALL_COUNT);
        outcome.toolCallCount = count instanceof Number number ? number.intValue() : 0;
        outcome.paused = Boolean.TRUE.equals(state.get(SNAPSHOT_PAUSED));
        if (state.get(SNAPSHOT_PENDING_APPROVAL) instanceof Map<?, ?> approval) {
            Map<String, Object> copy = new LinkedHashMap<>();
            approval.forEach((key, value) -> copy.put(String.valueOf(key), value));
            outcome.pendingApproval = Map.copyOf(copy);
        }
        if (state.get(SNAPSHOT_TOOL_REFERENCES) instanceof List<?> refs) {
            for (Object item : refs) {
                if (item instanceof Map<?, ?> ref) {
                    Map<String, String> entry = new LinkedHashMap<>();
                    ref.forEach((key, value) -> entry.put(String.valueOf(key),
                            value == null ? null : String.valueOf(value)));
                    outcome.toolReferences.add(entry);
                }
            }
        }
        return outcome;
    }

    private static String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** 原始分片，保留 [[REASONING]] 标记 —— 只给 SSE 客户端消费。 */
    public Flux<String> chunks() {
        return chunks;
    }

    /** 剥离标记后的正文，给持久化与记忆抽取消费。 */
    public String reply() {
        return reply.toString();
    }

    public String reasoning() {
        return reasoning.toString();
    }

    public int toolCallCount() {
        return toolCallCount;
    }

    public List<Map<String, String>> toolReferences() {
        return List.copyOf(toolReferences);
    }

    /**
     * 本轮是否停在「工具执行前等用户批准」。
     * <p>
     * 暂停不是结束：正文已经产出并发给了客户端，只是还差一个工具没执行。
     * {@link #chunks()} 会正常结束，恢复要另起一次 {@code resume}。
     */
    public boolean paused() {
        return paused;
    }

    /** 待批准的变更，恢复时要把用户的决定回填进去。 */
    public Map<String, Object> pendingApproval() {
        return pendingApproval;
    }
}
