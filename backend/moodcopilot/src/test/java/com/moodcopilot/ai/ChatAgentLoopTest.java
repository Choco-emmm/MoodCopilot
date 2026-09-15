package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.ai.tool.ChatToolRegistry;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatAgentLoopTest {

    private static final AgentLoopOptions FLASH = new AgentLoopOptions(
            "test-flash", 4096, null, null, 5, false, "CHAT_STREAM", "FLASH");
    private static final AgentLoopOptions PRO = new AgentLoopOptions(
            "test-pro", 4096, null, "high", 5, true, "CHAT_AGENT_STREAM", "PRO");

    private static final String THREAD = "test-thread";

    private final DeepSeekClient deepSeekClient = mock(DeepSeekClient.class);
    private final ChatToolRegistry toolRegistry = mock(ChatToolRegistry.class);
    private final com.moodcopilot.langgraph.InMemoryCheckpointSaver checkpointSaver =
            new com.moodcopilot.langgraph.InMemoryCheckpointSaver();
    private final ChatAgentLoop loop = new ChatAgentLoop(deepSeekClient, toolRegistry, checkpointSaver,
            new ObjectMapper());

    private List<Map<String, Object>> newMessages() {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "sys"));
        messages.add(Map.of("role", "user", "content", "hi"));
        return messages;
    }

    private static DeepSeekStreamEvent.TextChunk text(String value) {
        return new DeepSeekStreamEvent.TextChunk(value);
    }

    private static DeepSeekStreamEvent.TextChunk reasoning(String value) {
        return new DeepSeekStreamEvent.TextChunk(AgentLoopOutcome.REASONING_MARKER + value);
    }

    private List<String> drain(AgentLoopOutcome outcome) {
        return outcome.chunks().collectList().block();
    }

    @Test
    void plainTextStreamPassesThroughAndReplyMatches() {
        when(deepSeekClient.stream(any(), any(), any()))
                .thenReturn(reactor.core.publisher.Flux.just(text("Hello"), text(" world")));

        AgentLoopOutcome outcome = loop.run(newMessages(), null, null, FLASH, ChatTurn.of(List.of()), THREAD);
        List<String> chunks = drain(outcome);

        assertEquals(List.of("Hello", " world"), chunks);
        assertEquals("Hello world", outcome.reply());
        assertEquals("", outcome.reasoning());
        assertEquals(0, outcome.toolCallCount());
    }

    @Test
    void reasoningChunksReachTheClientButNeverTheReply() {
        when(deepSeekClient.stream(any(), any(), any()))
                .thenReturn(reactor.core.publisher.Flux.just(reasoning("thinking"), text("answer")));

        AgentLoopOutcome outcome = loop.run(newMessages(), null, null, PRO, ChatTurn.of(List.of()), THREAD);
        List<String> chunks = drain(outcome);

        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).startsWith(AgentLoopOutcome.REASONING_MARKER));
        assertEquals("answer", outcome.reply());
        assertEquals("thinking", outcome.reasoning());
    }

    @Test
    void exposeReasoningFalseSuppressesTheMarkerButStillAccumulatesIt() {
        when(deepSeekClient.stream(any(), any(), any()))
                .thenReturn(reactor.core.publisher.Flux.just(reasoning("thinking"), text("answer")));

        AgentLoopOutcome outcome = loop.run(newMessages(), null, null, FLASH, ChatTurn.of(List.of()), THREAD);
        List<String> chunks = drain(outcome);

        // flash 此前不暴露思考过程，保持行为不变：客户端只拿到正文
        assertEquals(List.of("answer"), chunks);
        assertEquals("answer", outcome.reply());
        assertEquals("thinking", outcome.reasoning());
    }

    @Test
    void aToolCallRoundExecutesTheToolAndRecurses() throws Exception {
        when(deepSeekClient.stream(any(), any(), any()))
                .thenReturn(reactor.core.publisher.Flux.just(
                                text("let me check "),
                                new DeepSeekStreamEvent.ToolCallReady("call-1", "diarySearchFunction", "{\"keyword\":\"x\"}")),
                        reactor.core.publisher.Flux.just(text("done")));
        when(toolRegistry.execute(eq("diarySearchFunction"), anyString(), any()))
                .thenReturn(Map.of("hits", 2));
        when(toolRegistry.emit(eq("diarySearchFunction"), any(), any())).thenReturn(List.of());

        List<Map<String, Object>> messages = newMessages();
        AgentLoopOutcome outcome = loop.run(messages, null, null, FLASH, ChatTurn.of(List.of()), THREAD);
        List<String> chunks = drain(outcome);

        assertEquals(List.of("let me check ", "done"), chunks);
        assertEquals("let me check done", outcome.reply());
        assertEquals(1, outcome.toolCallCount());
        verify(deepSeekClient, times(2)).stream(any(), any(), any());

        // 助手消息（带 tool_calls）与工具结果消息都必须回灌进对话
        Map<String, Object> assistantMsg = messages.get(messages.size() - 2);
        assertEquals("assistant", assistantMsg.get("role"));
        assertEquals("let me check ", assistantMsg.get("content"));
        assertNotNull(assistantMsg.get("tool_calls"));

        Map<String, Object> toolMsg = messages.get(messages.size() - 1);
        assertEquals("tool", toolMsg.get("role"));
        assertEquals("call-1", toolMsg.get("tool_call_id"));
        assertTrue(String.valueOf(toolMsg.get("content")).contains("hits"));
    }

    @Test
    void aFailingToolStillProducesAToolMessageAndTheLoopContinues() throws Exception {
        when(deepSeekClient.stream(any(), any(), any()))
                .thenReturn(reactor.core.publisher.Flux.just(
                                new DeepSeekStreamEvent.ToolCallReady("call-9", "updateEventStatusFunction", "{}")),
                        reactor.core.publisher.Flux.just(text("recovered")));
        when(toolRegistry.execute(anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("事件不存在"));

        List<Map<String, Object>> messages = newMessages();
        AgentLoopOutcome outcome = loop.run(messages, null, null, FLASH, ChatTurn.of(List.of()), THREAD);
        List<String> chunks = drain(outcome);

        // 每个 tool_call_id 都必须有对应的 tool 消息，否则协议不成立、模型会卡住
        Map<String, Object> toolMsg = messages.get(messages.size() - 1);
        assertEquals("tool", toolMsg.get("role"));
        assertEquals("call-9", toolMsg.get("tool_call_id"));
        assertTrue(String.valueOf(toolMsg.get("content")).contains("error"));
        assertTrue(String.valueOf(toolMsg.get("content")).contains("事件不存在"));

        assertEquals(List.of("recovered"), chunks);
        assertEquals("recovered", outcome.reply());
    }

    @Test
    void chunksArriveWhileTheModelCallIsStillRunning() throws Exception {
        // 图里的节点用 toIterable() 阻塞等待分片，而真实路径上分片来自 reactor-netty 的
        // 事件循环、不是订阅线程。这里把模型调用钉在另一线程上并中途卡住，
        // 证明分片是「来一片推一片」，而不是攒到调用结束才一起吐出来。
        List<String> seen = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch release = new CountDownLatch(1);

        when(deepSeekClient.stream(any(), any(), any())).thenReturn(
                Flux.<DeepSeekStreamEvent>create(sink -> {
                    sink.next(text("一"));
                    try {
                        release.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    sink.next(text("二"));
                    sink.complete();
                }).subscribeOn(Schedulers.boundedElastic()));

        AgentLoopOutcome outcome = loop.run(newMessages(), null, null, FLASH, ChatTurn.of(List.of()), THREAD);
        Thread consumer = new Thread(() -> outcome.chunks().doOnNext(seen::add).blockLast());
        consumer.start();

        long deadline = System.currentTimeMillis() + 5_000;
        while (seen.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(List.of("一"), seen, "模型还没结束时第一片就该到客户端了");

        release.countDown();
        consumer.join(5_000);
        assertEquals(List.of("一", "二"), seen);
        assertEquals("一二", outcome.reply());
    }

    @Test
    void depthLimitTerminatesWithoutRecursing() throws Exception {
        when(deepSeekClient.stream(any(), any(), any()))
                .thenReturn(reactor.core.publisher.Flux.just(
                        new DeepSeekStreamEvent.ToolCallReady("call-1", "diarySearchFunction", "{}")));
        when(toolRegistry.execute(anyString(), anyString(), any())).thenReturn(Map.of());
        when(toolRegistry.emit(anyString(), any(), any())).thenReturn(List.of());

        AgentLoopOptions noDepth = new AgentLoopOptions(
                "test-flash", 4096, null, null, 0, false, "CHAT_STREAM", "FLASH");

        AgentLoopOutcome outcome = loop.run(newMessages(), null, null, noDepth, ChatTurn.of(List.of()), THREAD);

        assertEquals(List.of(), drain(outcome));
        assertEquals(1, outcome.toolCallCount());
        verify(deepSeekClient, times(1)).stream(any(), any(), any());
    }
}
