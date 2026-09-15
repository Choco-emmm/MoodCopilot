package com.moodcopilot.ai;

/**
 * 用户对**一条**待批准工具调用的表态。
 *
 * @param toolCallId 对应哪一次工具调用；为 {@code null} 表示**通配** ——
 *                   这条决定适用于所有没被逐条点名的调用。只有一条待批准时不必费劲传 id。
 * @param approved   是否放行
 * @param reason     拒绝理由，会作为工具结果回给模型
 */
public record ApprovalChoice(String toolCallId, boolean approved, String reason) {

    /** 通配决定：整批共用一条。 */
    public static ApprovalChoice forAll(boolean approved, String reason) {
        return new ApprovalChoice(null, approved, reason);
    }
}
