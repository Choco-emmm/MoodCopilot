package com.moodcopilot.ai;

import org.bsc.langgraph4j.state.AgentState;

import java.util.List;
import java.util.Map;

/**
 * Chat Agent 图的状态。
 * <p>
 * 只放**跨节点、且要能进检查点**的东西。累加器 {@link AgentLoopOutcome} 刻意不在这里：
 * 它持有 Flux，序列化不了，而图在每个节点前都会 {@code cloneState}（走 ObjectInputStream）。
 * 它由每次 {@code run()} 的闭包捕获即可，效果一样。
 * <p>
 * 字段值必须是 JSON 原生类型（String / Number / List / Map），理由同上 ——
 * 这是 {@code RedisCheckpointSaver} 和图自带序列化器共同的前提。
 */
class ChatLoopState extends AgentState {

    static final String MESSAGES = "messages";
    static final String PENDING_TOOL_CALLS = "pendingToolCalls";
    static final String LOOP_COUNT = "loopCount";

    ChatLoopState(Map<String, Object> initData) {
        super(initData);
    }

    /** 当前完整对话，含此前回灌的 assistant / tool 消息。 */
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> messages() {
        Object value = data().get(MESSAGES);
        return value instanceof List ? (List<Map<String, Object>>) value : List.of();
    }

    /** 本轮模型要求调用的工具，等 toolsNode 执行。 */
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> pendingToolCalls() {
        Object value = data().get(PENDING_TOOL_CALLS);
        return value instanceof List ? (List<Map<String, Object>>) value : List.of();
    }

    /** 已经调用过几次模型，深度守卫看的就是它。 */
    int loopCount() {
        Object value = data().get(LOOP_COUNT);
        return value instanceof Number number ? number.intValue() : 0;
    }
}
