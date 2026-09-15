package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「跑到一半要等人」在驱动器这一层的落地。
 * <p>
 * 这里要守住的是一条很容易写错、又完全无声的边界：暂停**不是**结束。少写一个分支，
 * 看起来一切正常 —— 弹框照弹、事件照发 —— 但 done 帧提前发了、画像抽取拿着半截正文跑了，
 * 用户确认之后记忆却永远写不进去。图上是对的，接线错了，所以测试钉在接线这一层。
 */
class ChatGenerationServiceTest {

    private static final long USER = 7L;
    private static final long CONV = 42L;
    private static final String MESSAGE = "帮我记一下：我讨厌吃番茄";

    private final Map<String, Map<String, String>> hashes = new LinkedHashMap<>();
    private final Map<String, List<String>> lists = new LinkedHashMap<>();
    private final Map<String, String> values = new LinkedHashMap<>();

    private final ChatService chatService = mock(ChatService.class);
    private final MemoryExtractionService memoryExtractionService = mock(MemoryExtractionService.class);

    private String runId;
    private ChatGenerationService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ListOperations<String, String> listOps = mock(ListOperations.class);
        when(listOps.range(anyString(), anyLong(), anyLong())).thenAnswer(invocation -> {
            List<String> rows = lists.get(invocation.getArgument(0));
            return rows == null ? List.of() : List.copyOf(rows);
        });
        when(listOps.rightPush(anyString(), anyString())).thenAnswer(invocation -> {
            List<String> rows = lists.computeIfAbsent(invocation.getArgument(0), k -> new ArrayList<>());
            rows.add(invocation.getArgument(1));
            return (long) rows.size();
        });

        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(invocation -> {
            if (values.putIfAbsent(invocation.getArgument(0), invocation.getArgument(1)) == null) {
                return Boolean.TRUE;
            }
            return Boolean.FALSE;
        });
        when(valueOps.get(anyString())).thenAnswer(invocation -> values.get(invocation.getArgument(0)));

        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        doAnswer(invocation -> {
            Map<String, String> target = hashes.computeIfAbsent(invocation.getArgument(0), k -> new LinkedHashMap<>());
            ((Map<?, ?>) invocation.getArgument(1))
                    .forEach((k, v) -> target.put(String.valueOf(k), String.valueOf(v)));
            return null;
        }).when(hashOps).putAll(anyString(), anyMap());
        when(hashOps.get(anyString(), any())).thenAnswer(invocation -> {
            Map<String, String> hash = hashes.get(invocation.getArgument(0));
            return hash == null ? null : hash.get(String.valueOf((Object) invocation.getArgument(1)));
        });
        doAnswer(invocation -> {
            Map<String, String> hash = hashes.computeIfAbsent(invocation.getArgument(0), k -> new LinkedHashMap<>());
            hash.put(String.valueOf((Object) invocation.getArgument(1)),
                    String.valueOf((Object) invocation.getArgument(2)));
            return null;
        }).when(hashOps).put(anyString(), any(), any());
        when(hashOps.increment(anyString(), any(), anyLong())).thenAnswer(invocation -> {
            Map<String, String> hash = hashes.computeIfAbsent(invocation.getArgument(0), k -> new LinkedHashMap<>());
            String field = String.valueOf((Object) invocation.getArgument(1));
            long next = Long.parseLong(hash.getOrDefault(field, "0")) + (Long) invocation.getArgument(2);
            hash.put(field, String.valueOf(next));
            return next;
        });

        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForList()).thenReturn(listOps);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForHash()).thenReturn(hashOps);
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(Boolean.TRUE);
        // 生产里的 CAS 是段 Lua；测试只需要它的语义：状态对得上才迁移
        when(redis.execute(any(RedisScript.class), any(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    List<?> keys = invocation.getArgument(1);
                    Object expected = invocation.getArgument(2);
                    Object next = invocation.getArgument(3);
                    Map<String, String> hash = hashes.get(String.valueOf(keys.get(0)));
                    if (hash == null || !String.valueOf(expected).equals(hash.get("status"))) {
                        return 0L;
                    }
                    hash.put("status", String.valueOf(next));
                    return 1L;
                });

        service = new ChatGenerationService(chatService, memoryExtractionService, redis, new ObjectMapper(),
                Runnable::run);
    }

    /** 跑一轮；模型这一步由 chatService 的桩决定，返回的 outcome 决定这一轮停不停。 */
    private String startRun(AgentLoopOutcome outcome) {
        when(chatService.chat(any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any(), any(),
                anyBoolean())).thenReturn(new ChatService.ChatStreamContext("", outcome.chunks(), outcome));
        runId = service.start(new ChatGenerationService.StartRequest(USER, CONV, "req-1", MESSAGE,
                List.of(), List.of(), ReferencePurpose.DISCUSS, false,
                new UsernamePasswordAuthenticationToken("user", null, List.of()), List.of(), true)).runId();
        return runId;
    }

    private static AgentLoopOutcome outcome(String reply) {
        AgentLoopOutcome outcome = new AgentLoopOutcome();
        outcome.attachChunks(Flux.just(reply));
        outcome.appendText(reply);
        return outcome;
    }

    /**
     * 续跑取回的累加器：暂停前那半截已经在 {@code reply()} 里（检查点带来的），
     * 分片流只产出续跑这一段。这条区别就是「恢复后正文会不会丢半截」的全部内容。
     */
    private static AgentLoopOutcome resumedOutcome(String carriedOver, String streamed) {
        AgentLoopOutcome outcome = new AgentLoopOutcome();
        outcome.appendText(carriedOver);
        outcome.attachChunks(Flux.just(streamed));
        outcome.appendText(streamed);
        return outcome;
    }

    private static AgentLoopOutcome pausedOutcome(String reply) {
        AgentLoopOutcome outcome = outcome(reply);
        outcome.pause(Map.of(ChatAgentLoop.APPROVAL_KEY, Map.of("items", List.of(Map.of(
                "toolCallId", "call-1", "toolName", "saveMemory",
                "attributeKey", "讨厌的食物", "oldValue", "", "newValue", "番茄", "kind", "added")))));
        return outcome;
    }

    private List<String> events() {
        return lists.getOrDefault("chat:run:" + runId + ":events", List.of());
    }

    private String status() {
        return hashes.get("chat:run:" + runId + ":meta").get("status");
    }

    private String meta(String field) {
        return hashes.get("chat:run:" + runId + ":meta").get(field);
    }

    private boolean hasEvent(String type) {
        return events().stream().anyMatch(json -> json.contains("\"type\":\"" + type + "\""));
    }

    @Test
    void aPausedRunAsksForApprovalInsteadOfFinishing() {
        startRun(pausedOutcome("好的，帮你记一下 "));

        assertTrue(hasEvent("approval_required"), "要发待批准帧：" + events());
        assertFalse(hasEvent("done"), "还没结束，不能发 done：" + events());
        assertEquals("AWAITING_APPROVAL", status());
        verify(memoryExtractionService, never()).extractAndSyncMemoryFromChat(anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void theApprovalFrameCarriesWhatWillChange() {
        startRun(pausedOutcome("好的，帮你记一下 "));

        String frame = events().stream().filter(json -> json.contains("approval_required")).findFirst().orElseThrow();
        assertTrue(frame.contains("\"attributeKey\":\"讨厌的食物\""), frame);
        assertTrue(frame.contains("\"oldValue\":\"\""), frame);
        assertTrue(frame.contains("\"newValue\":\"番茄\""), frame);
        assertTrue(frame.contains("\"kind\":\"added\""), frame);
        assertTrue(frame.contains("\"toolCallId\":\"call-1\""), frame);
    }

    @Test
    void aPausedRunKeepsEverythingTheResumeWillNeed() {
        // 用户可能几分钟后才点确认，中间还换过一次后端进程 —— 到那时只能从 meta 里拿
        startRun(pausedOutcome("好的，帮你记一下 "));

        assertEquals("false", meta("useReasoning"));
        assertEquals(MESSAGE, meta("userMessage"));
        assertEquals("[]", meta("imageUrls"));
        assertEquals("[]", meta("userReferences"));
    }

    @Test
    void approvingResumesTheSameRunAndOnlyThenFinishes() {
        startRun(pausedOutcome("好的，帮你记一下 "));
        AgentLoopOutcome resumed = resumedOutcome("好的，帮你记一下 ", "记好了");
        when(chatService.resumeChat(anyLong(), any(), anyBoolean(), any(), any()))
                .thenReturn(new ChatService.ChatStreamContext("", resumed.chunks(), resumed));

        ChatGenerationService.RunSnapshot snapshot = service.approve(runId, USER, CONV,
                new UsernamePasswordAuthenticationToken("user", null, List.of()),
                List.of(ApprovalChoice.forAll(true, null)));

        assertEquals("RUNNING", snapshot.status());
        assertTrue(hasEvent("done"), "确认之后这一轮才算结束：" + events());
        assertEquals("SUCCEEDED", status());
        // 正文必须是暂停前后拼起来的整段 —— 画像抽取拿半截，记下来的就是错的东西
        verify(memoryExtractionService).extractAndSyncMemoryFromChat(USER, CONV, MESSAGE, List.of(),
                "好的，帮你记一下 记好了");
    }

    @Test
    void theDecisionIsHandedToTheGraphUnchanged() {
        startRun(pausedOutcome("好的，帮你记一下 "));
        AgentLoopOutcome resumed = resumedOutcome("好的，帮你记一下 ", "那就不记了");
        when(chatService.resumeChat(anyLong(), any(), anyBoolean(), any(), any()))
                .thenReturn(new ChatService.ChatStreamContext("", resumed.chunks(), resumed));

        service.approve(runId, USER, CONV, new UsernamePasswordAuthenticationToken("user", null, List.of()),
                List.of(ApprovalChoice.forAll(false, "这条我不想记")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> decision = ArgumentCaptor.forClass(Map.class);
        verify(chatService).resumeChat(eq(USER), any(ChatTurn.class), eq(false), decision.capture(), eq(runId));
        @SuppressWarnings("unchecked")
        Map<String, Object> approval = (Map<String, Object>) decision.getValue().get(ChatLoopState.APPROVAL_DECISION);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) approval.get("decisions");
        assertEquals(1, entries.size());
        assertEquals(Boolean.FALSE, entries.get(0).get("approved"));
        assertEquals("这条我不想记", entries.get(0).get("reason"));
        // 通配决定不该带 toolCallId，带了就被当成「只对某一次调用有效」
        assertFalse(entries.get(0).containsKey("toolCallId"));
    }

    @Test
    void aSecondApprovalInTheSameTurnPausesAgainInsteadOfSneakingThrough() {
        // 一次点头只放行这一次。否则模型在同一轮里再要写一条，就会被当成「已经批过了」直接落库。
        startRun(pausedOutcome("好的，帮你记一下 "));
        AgentLoopOutcome pausedAgain = pausedOutcome("好的，帮你记一下 再记一条 ");
        when(chatService.resumeChat(anyLong(), any(), anyBoolean(), any(), any()))
                .thenReturn(new ChatService.ChatStreamContext("", pausedAgain.chunks(), pausedAgain));

        service.approve(runId, USER, CONV, new UsernamePasswordAuthenticationToken("user", null, List.of()),
                List.of(ApprovalChoice.forAll(true, null)));

        assertEquals("AWAITING_APPROVAL", status());
        assertFalse(hasEvent("done"), "又停下来问了一次，还没结束：" + events());
        verify(memoryExtractionService, never()).extractAndSyncMemoryFromChat(anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void aClientThatCannotShowThePromptTellsTheGraphSo() {
        // 小程序端没有弹框。这个声明没传到图里的话，那一轮就会停在工具执行前等一个
        // 没人能点的确认 —— 输入框一直转，用户完全不知道发生了什么。
        AgentLoopOutcome outcome = outcome("今天天气不错");
        when(chatService.chat(any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any(), any(),
                anyBoolean())).thenReturn(new ChatService.ChatStreamContext("", outcome.chunks(), outcome));

        service.start(new ChatGenerationService.StartRequest(USER, CONV, "req-1", MESSAGE,
                List.of(), List.of(), ReferencePurpose.DISCUSS, false,
                new UsernamePasswordAuthenticationToken("user", null, List.of()), List.of(), false));

        verify(chatService).chat(eq(CONV), eq(MESSAGE), any(), any(), anyBoolean(), any(), any(), any(), any(),
                any(), eq(false));
    }

    @Test
    void aRunThatIsNotWaitingCannotBeApproved() {
        startRun(outcome("今天天气不错"));

        // 正常跑完的 run 没有检查点可恢复；放行就等于凭空执行一次工具
        assertThrows(RuntimeException.class, () -> service.approve(runId, USER, CONV,
                new UsernamePasswordAuthenticationToken("user", null, List.of()),
                List.of(ApprovalChoice.forAll(true, null))));
    }

    @Test
    void approvingTwiceIsRejectedTheSecondTime() {
        startRun(pausedOutcome("好的，帮你记一下 "));
        AgentLoopOutcome resumed = outcome("记好了");
        when(chatService.resumeChat(anyLong(), any(), anyBoolean(), any(), any()))
                .thenReturn(new ChatService.ChatStreamContext("", resumed.chunks(), resumed));
        var auth = new UsernamePasswordAuthenticationToken("user", null, List.of());
        service.approve(runId, USER, CONV, auth, List.of(ApprovalChoice.forAll(true, null)));

        // 连点两下确认：第二下必须在恢复检查点之前就被挡住，否则工具会跑两次
        assertThrows(RuntimeException.class,
                () -> service.approve(runId, USER, CONV, auth, List.of(ApprovalChoice.forAll(true, null))));
    }

    @Test
    void aPausedRunCanStillBeCancelled() {
        startRun(pausedOutcome("好的，帮你记一下 "));

        service.cancel(runId, USER, CONV);

        assertEquals("CANCELLED", status());
        assertTrue(hasEvent("error"), "取消要有个可见的收尾帧：" + events());
    }

    @Test
    void anOrdinaryRunFinishesExactlyLikeItAlwaysDid() {
        startRun(outcome("今天天气不错"));

        assertTrue(hasEvent("done"));
        assertFalse(hasEvent("approval_required"));
        assertEquals("SUCCEEDED", status());
        verify(memoryExtractionService).extractAndSyncMemoryFromChat(USER, CONV, MESSAGE, List.of(), "今天天气不错");
    }
}
