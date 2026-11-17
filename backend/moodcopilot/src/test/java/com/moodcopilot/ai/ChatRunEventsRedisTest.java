package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.moodcopilot.entity.UserEntity;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Explicit opt-in; use a disposable Redis, never the application's Redis. */
@EnabledIfEnvironmentVariable(named = "MOODCOPILOT_TEST_REDIS_PORT", matches = "\\d+")
class ChatRunEventsRedisTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final String runId = "test-" + UUID.randomUUID();
    private final List<LettuceConnectionFactory> factories = new ArrayList<>();
    private final List<ReactiveRedisMessageListenerContainer> containers = new ArrayList<>();
    private final List<ChatRunEventNotifier> notifiers = new ArrayList<>();
    private StringRedisTemplate redis;
    private ChatRunEvents writer;
    private ChatRunEvents reader;

    @BeforeEach
    void setUp() {
        writer = instance();
        reader = instance(); // separate Redis connection factory and Pub/Sub subscription
        redis.opsForHash().putAll(meta(), Map.of("userId", "7", "conversationId", "42", "lastSequence", "0", "status", "RUNNING"));
    }

    private ChatRunEvents instance() {
        var factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1",
                Integer.parseInt(System.getenv("MOODCOPILOT_TEST_REDIS_PORT"))));
        factory.afterPropertiesSet();
        factory.start();
        factories.add(factory);
        var template = new StringRedisTemplate(factory);
        if (redis == null) redis = template;
        var container = new ReactiveRedisMessageListenerContainer(factory);
        containers.add(container);
        var notifier = new ChatRunEventNotifier(container, mapper);
        notifiers.add(notifier);
        notifier.start();
        return new ChatRunEvents(template, mapper, notifier);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        notifiers.forEach(ChatRunEventNotifier::close);
        containers.forEach(ReactiveRedisMessageListenerContainer::destroy);
        if (redis != null) redis.delete(List.of(meta(), key()));
        factories.forEach(LettuceConnectionFactory::destroy);
    }

    @Test
    void existingControllerStillSendsSseFramesWithTheSameJsonProtocol() throws Exception {
        var chat = mock(ChatService.class);
        var memory = mock(MemoryExtractionService.class);
        var generation = new ChatGenerationService(chat, memory, redis, mapper, Runnable::run, reader);
        var controller = new ChatController(chat, memory, mapper, mock(ChatReferenceResolver.class), generation);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        var user = new UserEntity();
        user.setId(7L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        assertTrue(writer.append(runId, "{\"type\":\"chunk\",\"content\":\"hello\"}"));
        var request = mvc.perform(get("/api/chat/conversations/42/runs/{runId}/stream", runId))
                .andExpect(request().asyncStarted()).andReturn();
        assertTrue(writer.append(runId, "{\"type\":\"tool_references\",\"items\":[]}"));
        assertTrue(writer.append(runId, "{\"type\":\"done\"}"));
        request.getAsyncResult(5000);
        var response = mvc.perform(asyncDispatch(request))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"))
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andReturn().getResponse().getContentAsString();
        assertTrue(response.contains("data:"));
        assertTrue(response.contains("\"content\":\"hello\""));
        assertTrue(response.contains("\"type\":\"tool_references\""));
        assertTrue(response.contains("\"type\":\"done\""));
        assertFalse(response.contains("[[TOOL_EVENT]]"));
    }

    @Test
    void replaysAndReceivesTextToolsApprovalAndCompletionAcrossInstances() throws Exception {
        assertTrue(writer.append(runId, "{\"type\":\"chunk\",\"content\":\"你好\"}"));
        var received = new CopyOnWriteArrayList<String>();
        var completed = new AtomicBoolean();
        var approvalReadyOnDelivery = new AtomicBoolean();
        var errors = new CopyOnWriteArrayList<Throwable>();
        var subscription = reader.stream(runId, 0).subscribe(event -> {
            if (event.contains("\"type\":\"approval_required\"")) {
                approvalReadyOnDelivery.set("AWAITING_APPROVAL".equals(redis.opsForHash().get(meta(), "status")));
            }
            received.add(event);
        }, errors::add, () -> completed.set(true));
        try {
            ChatRunEventsTest.await(() -> received.size() == 1);
            assertTrue(writer.append(runId, "{\"type\":\"tool_references\",\"items\":[]}"));
            assertTrue(writer.append(runId, "{\"type\":\"approval_required\",\"items\":[]}"));
            ChatRunEventsTest.await(() -> received.size() == 3);
            assertEquals("AWAITING_APPROVAL", redis.opsForHash().get(meta(), "status"));
            assertTrue(approvalReadyOnDelivery.get(), "approval status must be visible when its event arrives");
            assertFalse(completed.get(), "approval does not complete the SSE stream");
            assertTrue(writer.append(runId, "{\"type\":\"chunk\",\"content\":\" 续接\\n\"}"));
            assertTrue(writer.append(runId, "{\"type\":\"done\"}"));
            ChatRunEventsTest.await(completed::get);
            assertEquals(5, received.size());
            for (int i = 0; i < received.size(); i++) assertEquals(i + 1, mapper.readTree(received.get(i)).path("seq").asInt());
            assertTrue(mapper.readTree(received.get(1)).path("items").isArray(), "empty arrays must stay arrays");
            assertEquals(" 续接\n", mapper.readTree(received.get(3)).path("content").asText());
            assertTrue(errors.isEmpty());
            assertTrue(redis.getExpire(meta()) > 86_000);
            assertTrue(redis.getExpire(key()) > 86_000);
        } finally {
            subscription.dispose();
        }
    }

    @Test
    void disconnectedReaderDoesNotStopGenerationAndReconnectUsesSequenceNotListIndex() {
        // Legacy events can have gaps in seq; a List index is not a sequence number.
        redis.opsForList().rightPush(key(), "{\"seq\":3,\"type\":\"chunk\",\"content\":\"旧内容\"}");
        redis.opsForHash().put(meta(), "lastSequence", "3");
        for (int i = 0; i < 600; i++) assertTrue(writer.append(runId, "{\"type\":\"chunk\",\"content\":\"x\"}"));
        assertTrue(writer.append(runId, "{\"type\":\"done\"}"));
        var replay = reader.stream(runId, 300).collectList().block(Duration.ofSeconds(5));
        assertNotNull(replay);
        assertEquals(304, replay.size());
        assertTrue(replay.getFirst().contains("\"seq\":301"));
        assertTrue(replay.getLast().contains("\"seq\":604"));
    }

    @Test
    void rejectsInvalidEventBeforeChangingSequenceOrPublishing() {
        assertFalse(writer.append(runId, "{\"content\":\"missing type\"}"));
        assertEquals("0", redis.opsForHash().get(meta(), "lastSequence"));
        assertEquals(0, redis.opsForList().size(key()));
        redis.opsForValue().set(key(), "wrong type");
        assertFalse(writer.append(runId, "{\"type\":\"chunk\",\"content\":\"x\"}"));
        assertEquals("0", redis.opsForHash().get(meta(), "lastSequence"));
    }

    @Test
    void cancelledRunsCannotPublishANewApprovalOrReopenTheirStatus() {
        redis.opsForHash().put(meta(), "status", "CANCELLED");
        assertFalse(writer.append(runId, "{\"type\":\"approval_required\",\"items\":[]}"));
        assertEquals("CANCELLED", redis.opsForHash().get(meta(), "status"));
        assertEquals("0", redis.opsForHash().get(meta(), "lastSequence"));
        assertEquals(0, redis.opsForList().size(key()));
    }

    @Test
    void reconnectReconcilesEventsEvenWhenTheirPubSubNotificationsWereMissed() throws Exception {
        assertTrue(writer.append(runId, "{\"type\":\"chunk\",\"content\":\"before\"}"));
        var received = new CopyOnWriteArrayList<String>();
        var errors = new CopyOnWriteArrayList<Throwable>();
        var complete = new AtomicBoolean();
        var subscription = reader.stream(runId, 0).subscribe(received::add, errors::add, () -> complete.set(true));
        try {
            ChatRunEventsTest.await(() -> received.size() == 1);
            // This test opts into a disposable Redis. Sever only its Pub/Sub connections.
            redis.execute((RedisCallback<Object>) connection -> {
                for (var client : connection.serverCommands().getClientList()) {
                    if (client.getChannelSubscribtions() == null || client.getChannelSubscribtions() == 0) continue;
                    String address = client.getAddressPort();
                    int colon = address.lastIndexOf(':');
                    connection.serverCommands().killClient(address.substring(0, colon),
                            Integer.parseInt(address.substring(colon + 1)));
                }
                return null;
            });
            assertTrue(writer.append(runId, "{\"type\":\"chunk\",\"content\":\"during reconnect\"}"));
            assertTrue(writer.append(runId, "{\"type\":\"done\"}"));
            ChatRunEventsTest.await(() -> complete.get() || !errors.isEmpty());
            if (!errors.isEmpty()) {
                // Same behavior as the browser: reconnect using the last delivered seq.
                long after = mapper.readTree(received.getLast()).path("seq").asLong();
                received.addAll(reader.stream(runId, after).collectList().block(Duration.ofSeconds(5)));
            }
            assertEquals(3, received.size());
            assertTrue(received.getLast().contains("done"));
        } finally {
            subscription.dispose();
        }
    }

    private String meta() { return "chat:run:" + runId + ":meta"; }
    private String key() { return "chat:run:" + runId + ":events"; }
}
