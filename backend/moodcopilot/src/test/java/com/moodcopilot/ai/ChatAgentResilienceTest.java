package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.ai.tool.ChatToolRegistry;
import com.moodcopilot.langgraph.InMemoryCheckpointSaver;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Fault injection at the real graph boundary; no external model or user data. */
class ChatAgentResilienceTest {
    private final DeepSeekClient client = mock(DeepSeekClient.class);
    private final ChatToolRegistry tools = mock(ChatToolRegistry.class);
    private final InMemoryCheckpointSaver saver = new InMemoryCheckpointSaver();
    private final ChatRunCancellation cancellation = new ChatRunCancellation(mock(StringRedisTemplate.class, RETURNS_DEEP_STUBS));
    private final ChatAgentLoop loop = new ChatAgentLoop(client, tools, saver, new ObjectMapper(), cancellation);

    private AgentLoopOptions options(boolean pro) {
        return new AgentLoopOptions(pro ? "test-pro" : "test-flash", 256, null,
                pro ? "high" : null, 3, pro, "RESILIENCE_TEST", pro ? "PRO" : "FLASH");
    }

    private List<Map<String, Object>> messages() {
        return new ArrayList<>(List.of(Map.of("role", "user", "content", "synthetic request")));
    }

    private DeepSeekStreamEvent.ToolCallReady call(String id, String args) {
        return new DeepSeekStreamEvent.ToolCallReady(id, "diarySearchFunction", args);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void silentModelTimesOutAndCancelsUpstreamWithoutRetry(boolean pro) throws Exception {
        // The production default is longer; inject a short deadline without changing the stream.
        org.springframework.test.util.ReflectionTestUtils.setField(loop, "streamIdleTimeout", Duration.ofMillis(80));
        var cancelled = new CountDownLatch(1);
        when(client.stream(any(), any(), any())).thenReturn(
                Flux.<DeepSeekStreamEvent>never().doOnCancel(cancelled::countDown));
        var executor = Executors.newSingleThreadExecutor(r -> {
            var thread = new Thread(r, "agent-timeout-regression");
            thread.setDaemon(true);
            return thread;
        });
        var outcome = loop.run(messages(), null, null, options(pro), ChatTurn.of(List.of()), "timeout");
        Future<?> task = executor.submit(() -> outcome.chunks().blockLast());
        try {
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> task.get(2, TimeUnit.SECONDS), "silent model must terminate before the test guard");
            Throwable cause = failure;
            while (cause.getCause() != null) cause = cause.getCause();
            assertInstanceOf(TimeoutException.class, cause);
            assertTrue(cancelled.await(1, TimeUnit.SECONDS));
            assertFalse(outcome.paused());
            verify(client, times(1)).stream(any(), any(), any());
            verify(tools, never()).execute(anyString(), anyString(), any());
        } finally {
            task.cancel(true);
            executor.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void toolExceptionProducesValidResultAndNextModelRound(boolean pro) throws Exception {
        when(client.stream(any(), any(), any())).thenReturn(
                Flux.just(call("failed-call", "{}")),
                Flux.just(new DeepSeekStreamEvent.TextChunk("recovered")));
        when(tools.execute(anyString(), anyString(), any())).thenThrow(new IllegalStateException("synthetic failure"));
        var messages = messages();
        var outcome = loop.run(messages, null, null, options(pro), ChatTurn.of(List.of()), "tool-error");
        outcome.chunks().blockLast();
        var result = messages.get(messages.size() - 1);
        assertEquals("failed-call", result.get("tool_call_id"));
        assertEquals("TOOL_EXECUTION_FAILED", new ObjectMapper().readTree((String) result.get("content")).get("error").asText());
        assertEquals("recovered", outcome.reply());
        verify(tools, times(1)).execute(anyString(), anyString(), any());
        verify(client, times(2)).stream(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void repeatedCallIdAcrossRoundsReusesResultWithoutExecutingAgain(boolean pro) throws Exception {
        when(client.stream(any(), any(), any())).thenReturn(
                Flux.just(call("same-id", "{}")), Flux.just(call("same-id", "{}")),
                Flux.just(new DeepSeekStreamEvent.TextChunk("done")));
        when(tools.execute(anyString(), anyString(), any())).thenReturn(Map.of("hits", 2));
        var messages = messages();
        var outcome = loop.run(messages, null, null, options(pro), ChatTurn.of(List.of()), "duplicate");
        outcome.chunks().blockLast();
        verify(tools, times(1)).execute(anyString(), anyString(), any());
        assertEquals(1, outcome.toolCallCount());
        var results = messages.stream().filter(m -> "tool".equals(m.get("role"))).toList();
        assertEquals(2, results.size());
        assertEquals(results.get(0).get("content"), results.get(1).get("content"));
        assertEquals("done", outcome.reply());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void duplicateInOneBatchHasOneProtocolEntryAndOneExecution(boolean pro) throws Exception {
        when(client.stream(any(), any(), any())).thenReturn(
                Flux.just(call("one", "{}"), call("one", "{}")), Flux.empty());
        when(tools.execute(anyString(), anyString(), any())).thenReturn(Map.of("hits", 1));
        var messages = messages();
        loop.run(messages, null, null, options(pro), ChatTurn.of(List.of()), "batch").chunks().blockLast();
        verify(tools, times(1)).execute(anyString(), anyString(), any());
        assertEquals(1, ((List<?>) messages.get(1).get("tool_calls")).size());
        assertEquals(1, messages.stream().filter(m -> "tool".equals(m.get("role"))).count());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedToolIsNotRetriedByDuplicateDelivery(boolean pro) throws Exception {
        when(client.stream(any(), any(), any())).thenReturn(
                Flux.just(call("one", "{}")), Flux.just(call("one", "{}")), Flux.empty());
        when(tools.execute(anyString(), anyString(), any())).thenThrow(new IllegalStateException("partial failure"));
        var messages = messages();
        loop.run(messages, null, null, options(pro), ChatTurn.of(List.of()), "failure-replay").chunks().blockLast();
        verify(tools, times(1)).execute(anyString(), anyString(), any());
        var results = messages.stream().filter(m -> "tool".equals(m.get("role"))).toList();
        assertEquals(2, results.size());
        assertEquals(results.get(0).get("content"), results.get(1).get("content"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reusedIdWithDifferentArgumentsFailsClosed(boolean pro) throws Exception {
        when(client.stream(any(), any(), any())).thenReturn(
                Flux.just(call("one", "{}")), Flux.just(call("one", "{\"keyword\":\"different\"}")));
        when(tools.execute(anyString(), anyString(), any())).thenReturn(Map.of());
        var outcome = loop.run(messages(), null, null, options(pro), ChatTurn.of(List.of()), "conflict");
        assertThrows(RuntimeException.class, () -> outcome.chunks().blockLast());
        verify(tools, times(1)).execute(anyString(), anyString(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void distinctIdsAndDistinctRunsAreNotDeduplicated(boolean pro) throws Exception {
        when(client.stream(any(), any(), any())).thenReturn(
                Flux.just(call("one", "{}"), call("two", "{}")), Flux.empty(),
                Flux.just(call("one", "{}")), Flux.empty());
        when(tools.execute(anyString(), anyString(), any())).thenReturn(Map.of());
        loop.run(messages(), null, null, options(pro), ChatTurn.of(List.of()), "run-a").chunks().blockLast();
        loop.run(messages(), null, null, options(pro), ChatTurn.of(List.of()), "run-b").chunks().blockLast();
        verify(tools, times(3)).execute(anyString(), anyString(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void receiptsSurviveAnApprovalCheckpointAndResume(boolean pro) throws Exception {
        when(client.stream(any(), any(), any())).thenReturn(
                Flux.just(call("read", "{}")),
                Flux.just(new DeepSeekStreamEvent.ToolCallReady("write", "saveMemoryFunction", "{}")),
                Flux.just(call("read", "{}")), Flux.empty());
        when(tools.requiresApproval("saveMemoryFunction")).thenReturn(true);
        when(tools.displayName("saveMemoryFunction")).thenReturn("saveMemory");
        when(tools.execute(anyString(), anyString(), any())).thenReturn(Map.of("success", true));
        var first = loop.run(messages(), null, null, options(pro), ChatTurn.of(List.of()), "resume");
        first.chunks().blockLast();
        assertTrue(first.paused());
        // Recreate the loop to ensure receipts live in the checkpoint, not a process map.
        var recreated = new ChatAgentLoop(client, tools, saver, new ObjectMapper(), cancellation);
        var resumed = recreated.resume(null, null, options(pro), ChatTurn.of(List.of()), "resume",
                ChatAgentLoop.approvalDecision(List.of(new ApprovalChoice("write", true, "approved"))));
        resumed.chunks().blockLast();
        assertFalse(resumed.paused());
        assertEquals(2, resumed.toolCallCount());
        verify(tools, times(1)).execute(eq("diarySearchFunction"), anyString(), any());
        verify(tools, times(1)).execute(eq("saveMemoryFunction"), anyString(), any());
    }
}
