package com.moodcopilot.ai;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class ChatHistoryMergeTest {

    /**
     * 前端的 PUT /history 只带内容，不带 id —— 服务端写的历史用 UUID 做 id，
     * 所以命中要靠 (role, content) 回退。
     */
    private static Map<String, Object> row(String role, String content) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("role", role);
        row.put("content", content);
        return row;
    }

    @Test
    void matchingRowKeepsServerContentAndGainsClientFields() {
        // 服务端写的历史带 UUID 做 id，客户端不带 —— 必须靠 (role, content) 回退命中，
        // 否则每一轮都会把同一行再追加一遍。
        List<Map<String, Object>> stored = new ArrayList<>(List.of(row("user", "今天怎么样")));
        stored.get(0).put("id", "server-uuid");

        List<Map<String, Object>> incoming = new ArrayList<>(List.of(row("user", "今天怎么样")));
        incoming.get(0).put("reasoningContent", "客户端带来的思考");

        List<Map<String, Object>> merged = ChatService.mergeHistory(stored, incoming);

        assertEquals(1, merged.size(), "命中不该追加新行");
        assertEquals("今天怎么样", merged.get(0).get("content"));
        assertEquals("server-uuid", merged.get(0).get("id"), "服务端 id 应保留");
        assertEquals("客户端带来的思考", merged.get(0).get("reasoningContent"), "客户端字段应被覆盖上去");
    }

    @Test
    void aNewOptimisticUserRowIsAppendedExactlyOnce() {
        List<Map<String, Object>> stored = new ArrayList<>(List.of(row("ai", "在的，怎么了")));
        List<Map<String, Object>> incoming = List.of(row("ai", "在的，怎么了"), row("user", "刚发的新消息"));

        List<Map<String, Object>> merged = ChatService.mergeHistory(stored, incoming);

        assertEquals(2, merged.size());
        assertEquals("刚发的新消息", merged.get(1).get("content"));
    }

    @Test
    void replayingTheSamePayloadIsIdempotent() {
        List<Map<String, Object>> stored = new ArrayList<>(List.of(row("user", "hi"), row("ai", "你好")));
        List<Map<String, Object>> incoming = List.of(row("user", "hi"), row("ai", "你好"), row("user", "新的一轮"));

        List<Map<String, Object>> once = ChatService.mergeHistory(stored, incoming);
        List<Map<String, Object>> twice = ChatService.mergeHistory(once, incoming);

        assertEquals(once.size(), twice.size(), "重放同一载荷不应再增长");
        assertEquals(once.toString(), twice.toString(), "重放结果应逐字一致");
    }

    @Test
    void serverRowsMissingFromThePayloadSurvive() {
        List<Map<String, Object>> stored = new ArrayList<>(List.of(
                row("user", "第一轮"), row("ai", "第一轮回复"), row("user", "第二轮")));
        // 前端只带了自己记得的部分
        List<Map<String, Object>> incoming = List.of(row("user", "第二轮"));

        List<Map<String, Object>> merged = ChatService.mergeHistory(stored, incoming);

        assertEquals(3, merged.size(), "payload 里缺失的服务端行必须保留");
        assertEquals("第一轮回复", merged.get(1).get("content"));
    }

    @Test
    void matchingByIdTakesPrecedenceOverContent() {
        Map<String, Object> storedRow = row("user", "旧内容");
        storedRow.put("id", "abc");
        List<Map<String, Object>> stored = new ArrayList<>(List.of(storedRow));

        Map<String, Object> incomingRow = row("user", "客户端以为的内容");
        incomingRow.put("id", "abc");
        incomingRow.put("quoteRef", Map.of("content", "引用"));
        List<Map<String, Object>> incoming = List.of(incomingRow);

        List<Map<String, Object>> merged = ChatService.mergeHistory(stored, incoming);

        assertEquals(1, merged.size());
        assertEquals("旧内容", merged.get(0).get("content"));
        assertEquals(Map.of("content", "引用"), merged.get(0).get("quoteRef"));
    }

    @Test
    void repeatedContentDoesNotCollapseOntoOneRow() {
        // 两条内容相同的消息：消费掉索引，第二条去匹配下一行而不是又改第一行
        List<Map<String, Object>> stored = new ArrayList<>(List.of(row("user", "111"), row("ai", "收到")));
        List<Map<String, Object>> incoming = new ArrayList<>(List.of(row("user", "111"), row("ai", "收到")));
        incoming.get(0).put("reasoningContent", "A");
        incoming.get(1).put("reasoningContent", "B");

        List<Map<String, Object>> merged = ChatService.mergeHistory(stored, incoming);

        assertEquals(2, merged.size());
        assertEquals("A", merged.get(0).get("reasoningContent"));
        assertEquals("B", merged.get(1).get("reasoningContent"));
    }

    @Test
    void mergingDoesNotMutateTheStoredRowsInPlace() {
        Map<String, Object> original = row("user", "hi");
        List<Map<String, Object>> stored = new ArrayList<>(List.of(original));

        List<Map<String, Object>> merged = ChatService.mergeHistory(stored, List.of(row("user", "hi")));

        assertNotSame(original, merged.get(0), "应复制而非复用同一对象");
        assertSame(original, stored.get(0), "入参列表不应被改动");
    }

    @Test
    void clientAiRoleMatchesServerAssistantRole() {
        // 前端推 role:'ai'，服务端写 role:'assistant' —— 不归一会导致 AI 消息每轮被重复追加。
        // 这正是「输出结束后弹出一条一模一样的」那个 bug。
        Map<String, Object> serverRow = row("assistant", "累了就歇一会儿吧");
        serverRow.put("id", "server-uuid");
        List<Map<String, Object>> stored = new ArrayList<>(List.of(row("user", "今天有点累"), serverRow));

        List<Map<String, Object>> incoming = List.of(
                row("user", "今天有点累"),
                row("ai", "累了就歇一会儿吧"));

        List<Map<String, Object>> merged = ChatService.mergeHistory(stored, incoming);

        assertEquals(2, merged.size(), "AI 行不该被追加成第三条");
        assertEquals("assistant", merged.get(1).get("role"), "服务端的 role 应保留");
    }

    @Test
    void anUnmatchedAssistantRowIsDroppedRatherThanDuplicated() {
        // 真实事故：服务端正文是「9月2日 20:54」，客户端累积的却是「9月2日20:54」。
        // 差一个空格，文本比对就失效。助手回复由服务端权威写入，这时追加会凭空多出一条。
        List<Map<String, Object>> stored = new ArrayList<>(List.of(
                row("user", "这个图片写了什么"),
                row("assistant", "**9月2日 20:54**（日记 2008）")));

        List<Map<String, Object>> incoming = List.of(
                row("user", "这个图片写了什么"),
                row("ai", "**9月2日20:54**（日记2008）"));

        List<Map<String, Object>> merged = ChatService.mergeHistory(stored, incoming);

        assertEquals(2, merged.size(), "漂移的助手行不该被追加");
        assertEquals("**9月2日 20:54**（日记 2008）", merged.get(1).get("content"), "服务端正文应保留");
    }

    @Test
    void anUnmatchedUserRowIsStillAppended() {
        // 用户行不同：它可能是前端乐观推入、服务端还没写下来的
        List<Map<String, Object>> stored = new ArrayList<>(List.of(row("assistant", "你好")));

        List<Map<String, Object>> merged = ChatService.mergeHistory(stored,
                List.of(row("assistant", "你好"), row("user", "还没被服务端写下来的那条")));

        assertEquals(2, merged.size());
        assertEquals("还没被服务端写下来的那条", merged.get(1).get("content"));
    }

    @Test
    void aMatchingAssistantRowStillGetsAnnotated() {
        List<Map<String, Object>> stored = new ArrayList<>(List.of(row("assistant", "晚安")));
        stored.get(0).put("id", "server-uuid");

        List<Map<String, Object>> incoming = new ArrayList<>(List.of(row("ai", "晚安")));
        incoming.get(0).put("reasoningContent", "客户端带来的思考");

        List<Map<String, Object>> merged = ChatService.mergeHistory(stored, incoming);

        assertEquals(1, merged.size());
        assertEquals("客户端带来的思考", merged.get(0).get("reasoningContent"));
    }

    @Test
    void emptyInputsAreSafe() {
        assertEquals(List.of(), ChatService.mergeHistory(List.of(), List.of()));
        assertEquals(1, ChatService.mergeHistory(List.of(), List.of(row("user", "hi"))).size());
        assertEquals(1, ChatService.mergeHistory(List.of(row("user", "hi")), List.of()).size());
    }
}
