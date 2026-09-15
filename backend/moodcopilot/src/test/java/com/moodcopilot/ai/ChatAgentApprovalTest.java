package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.ai.tool.ChatToolRegistry;
import com.moodcopilot.langgraph.InMemoryCheckpointSaver;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工具执行前的人工批准，以及批准之后的续跑。
 * <p>
 * 恢复最容易被写错的地方是「正文丢半截」：暂停发生在 agentNode 跑完之后，那时前半段正文
 * 已经推给客户端了；如果恢复时新建一个空累加器，落库的助手回复就只剩后半段。
 */
class ChatAgentApprovalTest {

    private static final AgentLoopOptions FLASH = new AgentLoopOptions(
            "test-flash", 4096, null, null, 5, false, "CHAT_STREAM", "FLASH");
    private static final String THREAD = "run-1";
    private static final String SAVE = "saveMemoryFunction";

    private final DeepSeekClient deepSeekClient = mock(DeepSeekClient.class);
    private final ChatToolRegistry toolRegistry = mock(ChatToolRegistry.class);
    private final InMemoryCheckpointSaver checkpointSaver = new InMemoryCheckpointSaver();
    private final ChatAgentLoop loop = new ChatAgentLoop(deepSeekClient, toolRegistry, checkpointSaver,
            new ObjectMapper());

    private static DeepSeekStreamEvent.TextChunk text(String value) {
        return new DeepSeekStreamEvent.TextChunk(value);
    }

    private static DeepSeekStreamEvent.ToolCallReady saveMemoryCall() {
        return new DeepSeekStreamEvent.ToolCallReady("call-1", SAVE,
                "{\"attributeKey\":\"讨厌的食物\",\"attributeValue\":\"番茄\"}");
    }

    private RunnableConfig config() {
        return RunnableConfig.builder().threadId(THREAD).build();
    }

    private List<Map<String, Object>> stateMessages() {
        Object messages = checkpointSaver.get(config()).orElseThrow().getState().get(ChatLoopState.MESSAGES);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) messages;
        return list;
    }

    private void toolNeedsApproval() {
        when(toolRegistry.requiresApproval(SAVE)).thenReturn(true);
        when(toolRegistry.displayName(SAVE)).thenReturn("saveMemory");
        when(toolRegistry.approvalPreview(eq(SAVE), any(), any())).thenReturn(Map.of(
                "attributeKey", "讨厌的食物", "oldValue", "", "newValue", "番茄", "kind", "added"));
    }

    private static List<Map<String, Object>> newMessages() {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "sys"));
        messages.add(Map.of("role", "user", "content", "帮我记一下：我讨厌吃番茄"));
        return messages;
    }

    private static List<String> drain(AgentLoopOutcome outcome) {
        return outcome.chunks().collectList().block();
    }

    @Test
    void aToolThatNeedsApprovalPausesBeforeRunningAndLeavesACheckpoint() throws Exception {
        toolNeedsApproval();
        when(deepSeekClient.stream(any(), any(), any()))
                .thenReturn(reactor.core.publisher.Flux.just(text("好的，帮你记一下 "), saveMemoryCall()));

        AgentLoopOutcome outcome = loop.run(newMessages(), null, null, FLASH, ChatTurn.of(List.of()), THREAD);

        assertEquals(List.of("好的，帮你记一下 "), drain(outcome));
        assertTrue(outcome.paused(), "该停下来等用户点头");
        verify(toolRegistry, never()).execute(anyString(), any(), any());

        Checkpoint checkpoint = checkpointSaver.get(config()).orElseThrow();
        assertEquals("toolsNode", checkpoint.getNextNodeId(), "断点该停在待执行的那个节点前");
        // 检查点里要留着「待执行哪几个工具」，恢复时才不用重新问一遍模型
        assertNotNull(checkpoint.getState().get("pendingToolCalls"));
        // 正文也必须落进检查点，否则恢复时拼不回完整的助手回复
        assertEquals("好的，帮你记一下 ", checkpoint.getState().get("accumulatedReply"));
        // 预览不进检查点：它是给人看的，走事件表的 approval_required 帧，刷新时靠事件重放恢复
        assertEquals(Map.of("items", List.of(Map.of(
                        "toolCallId", "call-1", "toolName", "saveMemory",
                        "attributeKey", "讨厌的食物", "oldValue", "", "newValue", "番茄", "kind", "added"))),
                outcome.pendingApproval().get("approval"));
    }

    @Test
    void approvingRunsTheToolAndKeepsTheReplyWrittenBeforeThePause() throws Exception {
        toolNeedsApproval();
        when(deepSeekClient.stream(any(), any(), any()))
                .thenReturn(reactor.core.publisher.Flux.just(text("好的，帮你记一下 "), saveMemoryCall()))
                .thenReturn(reactor.core.publisher.Flux.just(text("记好了")));
        when(toolRegistry.execute(eq(SAVE), any(), any())).thenReturn(Map.of("success", true));
        when(toolRegistry.emit(eq(SAVE), any(), any())).thenReturn(List.of());

        // chunks() 是惰性的：不消费就根本没跑图，也就没有检查点可供恢复
        drain(loop.run(newMessages(), null, null, FLASH, ChatTurn.of(List.of()), THREAD));
        AgentLoopOutcome resumed = loop.resume(null, null, FLASH, ChatTurn.of(List.of()), THREAD,
                ChatAgentLoop.approvalDecision(true, null));

        assertEquals(List.of("记好了"), drain(resumed));
        assertFalse(resumed.paused());
        verify(toolRegistry).execute(eq(SAVE), any(), any());

        // 最关键的一条：正文必须是暂停前后拼起来的
        assertEquals("好的，帮你记一下 记好了", resumed.reply());
    }

    @Test
    void rejectingFeedsTheReasonBackToTheModelWithoutRunningTheTool() throws Exception {
        toolNeedsApproval();
        when(deepSeekClient.stream(any(), any(), any()))
                .thenReturn(reactor.core.publisher.Flux.just(saveMemoryCall()))
                .thenReturn(reactor.core.publisher.Flux.just(text("那就不记了")));

        drain(loop.run(newMessages(), null, null, FLASH, ChatTurn.of(List.of()), THREAD));
        AgentLoopOutcome resumed = loop.resume(null, null, FLASH, ChatTurn.of(List.of()), THREAD,
                ChatAgentLoop.approvalDecision(false, "这条我不想记"));

        assertEquals(List.of("那就不记了"), drain(resumed));
        verify(toolRegistry, never()).execute(anyString(), any(), any());

        Map<String, Object> toolMessage = stateMessages().get(stateMessages().size() - 1);
        assertEquals("tool", toolMessage.get("role"));
        assertEquals("call-1", toolMessage.get("tool_call_id"));
        assertTrue(String.valueOf(toolMessage.get("content")).contains("这条我不想记"),
                "拒绝理由要原样回给模型：" + toolMessage.get("content"));
    }

    @Test
    void rejectingWithoutAReasonStillTellsTheModelNotToRetry() throws Exception {
        // 理由留空的话模型只看到一个空字符串，很容易原样再调一次，于是又弹一次框
        toolNeedsApproval();
        when(deepSeekClient.stream(any(), any(), any()))
                .thenReturn(reactor.core.publisher.Flux.just(saveMemoryCall()))
                .thenReturn(reactor.core.publisher.Flux.just(text("那就不记了")));

        drain(loop.run(newMessages(), null, null, FLASH, ChatTurn.of(List.of()), THREAD));
        // chunks() 惰性：不消费就根本没跑图，state 里也就看不到工具结果
        drain(loop.resume(null, null, FLASH, ChatTurn.of(List.of()), THREAD,
                ChatAgentLoop.approvalDecision(false, "")));

        Map<String, Object> toolMessage = stateMessages().get(stateMessages().size() - 1);
        String content = String.valueOf(toolMessage.get("content"));
        assertTrue(content.contains("不要再尝试"), "要明确让模型收手，而不是给一句空理由：" + content);
    }

    @Test
    void aChannelThatCannotAskRejectsInsteadOfHanging() throws Exception {
        toolNeedsApproval();
        when(deepSeekClient.stream(any(), any(), any()))
                .thenReturn(reactor.core.publisher.Flux.just(saveMemoryCall()))
                .thenReturn(reactor.core.publisher.Flux.just(text("请你在 App 里确认一下")));

        // 非流式 /reply：没人能点确认，中断就成了「永远没人来」
        AgentLoopOutcome outcome = loop.run(newMessages(), null, null, FLASH, ChatTurn.of(List.of()), THREAD,
                false);

        assertEquals(List.of("请你在 App 里确认一下"), drain(outcome));
        assertFalse(outcome.paused());
        verify(toolRegistry, never()).execute(anyString(), any(), any());

        Map<String, Object> toolMessage = stateMessages().get(stateMessages().size() - 1);
        assertTrue(String.valueOf(toolMessage.get("content")).contains("App"), "要给出可行动的理由");
    }

    @Test
    void toolsThatDoNotNeedApprovalStillRunSilently() throws Exception {
        when(deepSeekClient.stream(any(), any(), any()))
                .thenReturn(reactor.core.publisher.Flux.just(
                                new DeepSeekStreamEvent.ToolCallReady("call-9", "diarySearchFunction", "{}")),
                        reactor.core.publisher.Flux.just(text("查到 2 条")));
        when(toolRegistry.execute(eq("diarySearchFunction"), any(), any())).thenReturn(Map.of("hits", 2));
        when(toolRegistry.emit(eq("diarySearchFunction"), any(), any())).thenReturn(List.of());

        AgentLoopOutcome outcome = loop.run(newMessages(), null, null, FLASH, ChatTurn.of(List.of()), THREAD);

        assertEquals(List.of("查到 2 条"), drain(outcome));
        assertFalse(outcome.paused(), "不该为了不需要批准的工具停下来");
        verify(toolRegistry).execute(eq("diarySearchFunction"), any(), any());
    }

    @Test
    void aSecondApprovalInTheSameTurnAsksAgain() throws Exception {
        // 决定只用一次：用过就从 state 摘掉。否则模型第二次要写记忆时会被当成「已经批过了」直接落库 ——
        // 那就等于一次确认顺手把后面所有写入都放行了。
        toolNeedsApproval();
        when(deepSeekClient.stream(any(), any(), any()))
                .thenReturn(reactor.core.publisher.Flux.just(saveMemoryCall()));
        when(toolRegistry.execute(eq(SAVE), any(), any())).thenReturn(Map.of("success", true));
        when(toolRegistry.emit(eq(SAVE), any(), any())).thenReturn(List.of());

        AgentLoopOutcome first = loop.run(newMessages(), null, null, FLASH, ChatTurn.of(List.of()), THREAD);
        drain(first);
        assertTrue(first.paused());

        AgentLoopOutcome afterApproval = loop.resume(null, null, FLASH, ChatTurn.of(List.of()), THREAD,
                ChatAgentLoop.approvalDecision(true, null));
        drain(afterApproval);

        verify(toolRegistry).execute(eq(SAVE), any(), any());
        assertTrue(afterApproval.paused(), "模型又要求记一次，应当重新停下来问");
    }

    // ── 逐条决策：一批里有好几组待批准时，用户可以只批其中几组 ──

    private static final String MERGE = "mergeMemoryFunction";

    private static DeepSeekStreamEvent.ToolCallReady mergeCall(String id, String targetKey) {
        return new DeepSeekStreamEvent.ToolCallReady(id, MERGE, "{\"targetKey\":\"" + targetKey + "\"}");
    }

    private void twoMergeGroups() throws Exception {
        when(toolRegistry.requiresApproval(MERGE)).thenReturn(true);
        when(toolRegistry.displayName(MERGE)).thenReturn("mergeMemory");
        when(toolRegistry.approvalPreview(eq(MERGE), any(), any()))
                .thenReturn(Map.of("attributeKey", "讨厌的食物", "kind", "merged"));
        when(toolRegistry.execute(eq(MERGE), any(), any())).thenReturn(Map.of("success", true));
        when(toolRegistry.emit(eq(MERGE), any(), any())).thenReturn(List.of());
    }

    private Map<String, Object> toolMessageFor(String toolCallId) {
        return stateMessages().stream()
                .filter(message -> toolCallId.equals(message.get("tool_call_id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("没有 " + toolCallId + " 的工具结果：" + stateMessages()));
    }

    private AgentLoopOutcome pauseWithTwoMergeGroups() throws Exception {
        twoMergeGroups();
        when(deepSeekClient.stream(any(), any(), any()))
                .thenReturn(reactor.core.publisher.Flux.just(
                        mergeCall("call-a", "讨厌的食物"),
                        mergeCall("call-b", "喜欢的食物"),
                        text("我来整理一下")));
        AgentLoopOutcome outcome = loop.run(newMessages(), null, null, FLASH, ChatTurn.of(List.of()), THREAD);
        drain(outcome);
        return outcome;
    }

    @Test
    void aBatchWithSeveralGroupsPausesWithOneItemPerGroup() throws Exception {
        // 分页弹框的前提：一次工具调用 = 一个审批项 = 一页。合成一项的话用户就没法分别表态了。
        AgentLoopOutcome outcome = pauseWithTwoMergeGroups();

        assertTrue(outcome.paused());
        @SuppressWarnings("unchecked")
        Map<String, Object> approval = (Map<String, Object>) outcome.pendingApproval().get("approval");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) approval.get("items");
        assertEquals(2, items.size());
        assertEquals(List.of("call-a", "call-b"), items.stream().map(item -> item.get("toolCallId")).toList());
    }

    @Test
    void eachGroupGetsItsOwnDecision() throws Exception {
        // 用户批了第一组、拒了第二组 —— 整批共用一个布尔做不到这件事
        pauseWithTwoMergeGroups();
        when(deepSeekClient.stream(any(), any(), any()))
                .thenReturn(reactor.core.publisher.Flux.just(text("第一组合并好了")));

        AgentLoopOutcome resumed = loop.resume(null, null, FLASH, ChatTurn.of(List.of()), THREAD,
                ChatAgentLoop.approvalDecision(List.of(
                        new ApprovalChoice("call-a", true, null),
                        new ApprovalChoice("call-b", false, "第二组先别动"))));

        assertEquals(List.of("第一组合并好了"), drain(resumed));

        ArgumentCaptor<String> arguments = ArgumentCaptor.forClass(String.class);
        verify(toolRegistry, times(1)).execute(eq(MERGE), arguments.capture(), any());
        assertTrue(arguments.getValue().contains("讨厌的食物"), "执行的应当是批过的那一组");
        assertTrue(String.valueOf(toolMessageFor("call-b").get("content")).contains("第二组先别动"),
                "被拒的那组要把理由回给模型");
    }

    @Test
    void aGroupTheUserNeverMentionedIsTreatedAsRejected() throws Exception {
        // 逐条决策下漏掉一条是很容易发生的（比如前端只提交了已翻到的页）。
        // 没表态的一律按拒绝 —— 没点头的事不该做。
        pauseWithTwoMergeGroups();
        when(deepSeekClient.stream(any(), any(), any()))
                .thenReturn(reactor.core.publisher.Flux.just(text("好")));

        drain(loop.resume(null, null, FLASH, ChatTurn.of(List.of()), THREAD,
                ChatAgentLoop.approvalDecision(List.of(new ApprovalChoice("call-a", true, null)))));

        verify(toolRegistry, times(1)).execute(eq(MERGE), any(), any());
        String content = String.valueOf(toolMessageFor("call-b").get("content"));
        assertTrue(content.contains("不要再尝试"), "没表态的那条要明确让模型收手：" + content);
    }

    @Test
    void theBatchDecisionStillCoversEveryCall() throws Exception {
        // 不带 toolCallId 的那条是通配，整批共用 —— 只有一条待批准时走的就是它
        pauseWithTwoMergeGroups();
        when(deepSeekClient.stream(any(), any(), any()))
                .thenReturn(reactor.core.publisher.Flux.just(text("都合并好了")));

        drain(loop.resume(null, null, FLASH, ChatTurn.of(List.of()), THREAD,
                ChatAgentLoop.approvalDecision(true, null)));

        verify(toolRegistry, times(2)).execute(eq(MERGE), any(), any());
    }
}
