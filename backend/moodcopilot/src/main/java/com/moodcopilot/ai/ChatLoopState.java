package com.moodcopilot.ai;

import org.bsc.langgraph4j.state.AgentState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    /**
     * 用户对「待批准工具调用」的决定，恢复时由 {@code GraphInput.resume(...)} 回填进来。
     * 形如 {@code {"decisions": [{"toolCallId": "call_1", "approved": true, "reason": ""}]}}；
     * 不带 {@code toolCallId} 的那条是通配。用完置 null 从 state 里摘掉。
     */
    static final String APPROVAL_DECISION = "approvalDecision";

    /**
     * 这条通道能否让用户当场确认。非流式路径（公网 / 移动端的 /reply）为 false，
     * 此时要批准的工具一律直接拒绝并给出可行动的理由，而不是中断等一个永远不会来的点击。
     */
    static final String APPROVALS_INTERACTIVE = "approvalsInteractive";

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

    /** 用户对这批待批准工具调用的决定；还没表态时为空。 */
    @SuppressWarnings("unchecked")
    Map<String, Object> approvalDecision() {
        Object value = data().get(APPROVAL_DECISION);
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    /** 这一轮恢复是否带了决定。{@code interrupt} 靠它区分「首次暂停」和「被放行后的续跑」。 */
    boolean hasApprovalDecision() {
        return !approvalDecision().isEmpty();
    }

    /**
     * 取某一次调用的决定。逐条点名优先，其次落到不带 {@code toolCallId} 的通配决定。
     * <p>
     * 两条都没有就是**用户没表态**，由调用方按拒绝处理 —— 逐条审批下用户完全可能只批了其中几条，
     * 没点头的事不该做。多组工具调用混在一批里时，这个区别就是「批了第一组、漏了第二组」的全部含义。
     */
    Optional<ApprovalChoice> decisionFor(String toolCallId) {
        ApprovalChoice wildcard = null;
        for (Map<String, Object> entry : decisions()) {
            Object rawId = entry.get("toolCallId");
            String id = rawId == null ? null : String.valueOf(rawId);
            ApprovalChoice choice = new ApprovalChoice(id,
                    Boolean.TRUE.equals(entry.get("approved")),
                    entry.get("reason") == null ? "" : String.valueOf(entry.get("reason")));
            if (id == null) {
                wildcard = choice;
                continue;
            }
            if (id.equals(toolCallId)) {
                return Optional.of(choice);
            }
        }
        return Optional.ofNullable(wildcard);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> decisions() {
        Object value = approvalDecision().get("decisions");
        return value instanceof List ? (List<Map<String, Object>>) value : List.of();
    }

    /** 缺失时按「不能确认」处理：宁可让模型多问一句，也不要挂起一个没人能点的等待。 */
    boolean approvalsInteractive() {
        Object value = data().get(APPROVALS_INTERACTIVE);
        return value == null || Boolean.TRUE.equals(value);
    }
}
