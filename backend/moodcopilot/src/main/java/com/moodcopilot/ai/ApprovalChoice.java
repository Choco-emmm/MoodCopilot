package com.moodcopilot.ai;

/**
 * 用户对**一条**待批准工具调用的表态。
 *
 * @param toolCallId 明确点名哪一次工具调用；缺失或无效 ID 永远不能授予批准
 * @param approved   是否放行
 * @param reason     拒绝理由，会作为工具结果回给模型
 */
public record ApprovalChoice(String toolCallId, boolean approved, String reason, String editedValue) {
    public ApprovalChoice(String toolCallId, boolean approved, String reason) { this(toolCallId, approved, reason, null); }
}
