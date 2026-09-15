package com.moodcopilot.ai;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 请求体里 {@code decisions} 的解析。
 * <p>
 * 这是审批链路上唯一一段没有类型保护的手写解析（JSON 进来就是个 Map），而且**解析错了方向很危险**：
 * 把没表态的当成批准，就会在用户没点头的情况下写库、甚至删记忆。所以单独钉住几条。
 */
class ChatControllerApprovalParseTest {

    @Test
    void parsesOneDecisionPerCall() {
        List<ApprovalChoice> decisions = ChatController.approvalDecisions(Map.of("decisions", List.of(
                Map.of("toolCallId", "call-a", "approved", true),
                Map.of("toolCallId", "call-b", "approved", false, "reason", "第二组先别动"))));

        assertEquals(2, decisions.size());
        assertEquals(new ApprovalChoice("call-a", true, ""), decisions.get(0));
        assertEquals(new ApprovalChoice("call-b", false, "第二组先别动"), decisions.get(1));
    }

    @Test
    void aMissingOrMalformedBodyApprovesNothing() {
        // 拿不准的时候必须倒向「谁都没批」，绝不能倒向「都批了」
        assertTrue(ChatController.approvalDecisions(null).isEmpty());
        assertTrue(ChatController.approvalDecisions(Map.of()).isEmpty());
        assertTrue(ChatController.approvalDecisions(Map.of("decisions", "不是数组")).isEmpty());
        assertTrue(ChatController.approvalDecisions(Map.of("decisions", List.of("不是对象"))).isEmpty());
    }

    @Test
    void onlyTheLiteralTrueIsTreatedAsApproval() {
        // 客户端传字符串 "true"、或者漏字段，都不该被当成点头
        List<ApprovalChoice> decisions = ChatController.approvalDecisions(Map.of("decisions", List.of(
                Map.of("toolCallId", "call-a", "approved", "true"),
                Map.of("toolCallId", "call-b"))));

        assertEquals(2, decisions.size());
        assertEquals(false, decisions.get(0).approved());
        assertEquals(false, decisions.get(1).approved());
    }

    @Test
    void anEntryWithoutAToolCallIdBecomesAWildcardInsteadOfAWronglyNarrowOne() {
        List<ApprovalChoice> decisions = ChatController.approvalDecisions(Map.of("decisions",
                List.of(Map.of("approved", true))));

        assertEquals(1, decisions.size());
        assertEquals(null, decisions.get(0).toolCallId());
        assertTrue(decisions.get(0).approved());
    }
}
