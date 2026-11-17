package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatRunEventsTest {
    @Test
    @SuppressWarnings("unchecked")
    void slowConsumersDoNotCauseTheWholeHistoryToBeBuffered() {
        var redis = mock(StringRedisTemplate.class);
        ListOperations<String, String> lists = mock(ListOperations.class);
        when(redis.opsForList()).thenReturn(lists);
        var page = java.util.stream.IntStream.rangeClosed(1, 256)
                .mapToObj(seq -> "{\"seq\":" + seq + ",\"type\":\"chunk\",\"content\":\"x\"}").toList();
        var reads = new AtomicInteger();
        when(lists.range(anyString(), anyLong(), anyLong())).thenAnswer(call -> {
            reads.incrementAndGet();
            long index = call.getArgument(1);
            return index == 0 ? page : List.of("{\"seq\":257,\"type\":\"done\"}");
        });
        var notifier = mock(ChatRunEventNotifier.class);
        when(notifier.watch("run")).thenReturn(Flux.concat(Flux.just(true), Flux.never()));
        var events = new ChatRunEvents(redis, new ObjectMapper(), notifier);
        StepVerifier.create(events.stream("run", 0), 0)
                .then(() -> {
                    try { await(() -> reads.get() == 1); }
                    catch (Exception error) { throw new AssertionError(error); }
                    assertEquals(1, reads.get());
                })
                .thenRequest(256)
                .expectNextCount(256)
                .thenRequest(1)
                .expectNext("{\"seq\":257,\"type\":\"done\"}")
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    @SuppressWarnings("unchecked")
    void anEventAppendedDuringReplayIsDeliveredOnce() {
        var redis = mock(StringRedisTemplate.class);
        ListOperations<String, String> lists = mock(ListOperations.class);
        when(redis.opsForList()).thenReturn(lists);
        var notifier = mock(ChatRunEventNotifier.class);
        var emitter = new java.util.concurrent.atomic.AtomicReference<reactor.core.publisher.FluxSink<Boolean>>();
        when(notifier.watch("run")).thenReturn(Flux.create(sink -> {
            emitter.set(sink);
            sink.next(true);
        }));
        String first = "{\"seq\":1,\"type\":\"chunk\",\"content\":\"first\"}";
        String second = "{\"seq\":2,\"type\":\"done\"}";
        when(lists.range(anyString(), anyLong(), anyLong())).thenAnswer(call -> {
            long index = call.getArgument(1);
            if (index == 0) {
                // The first LRANGE snapshot is already taken when a new event is published.
                emitter.get().next(true);
                emitter.get().next(true);
                return List.of(first);
            }
            return index == 1 ? List.of(second) : List.of();
        });
        var events = new ChatRunEvents(redis, new ObjectMapper(), notifier);
        StepVerifier.create(events.stream("run", 0))
                .expectNext(first, second)
                .expectComplete()
                .verify(Duration.ofSeconds(3));
    }

    @Test
    @SuppressWarnings("unchecked")
    void tenIdleMinutesDoNotReadRedisAgain() {
        var redis = mock(StringRedisTemplate.class);
        ListOperations<String, String> lists = mock(ListOperations.class);
        when(redis.opsForList()).thenReturn(lists);
        String event = "{\"seq\":1,\"type\":\"chunk\",\"content\":\"x\"}";
        when(lists.range(anyString(), anyLong(), anyLong())).thenReturn(List.of(event));
        var notifier = mock(ChatRunEventNotifier.class);
        when(notifier.watch("run")).thenReturn(Flux.concat(Flux.just(true), Flux.never()));
        var events = new ChatRunEvents(redis, new ObjectMapper(), notifier);
        StepVerifier.withVirtualTime(() -> events.stream("run", 0))
                .expectNext(event)
                .thenAwait(Duration.ofMinutes(10))
                .thenCancel()
                .verify(Duration.ofSeconds(3));
        verify(lists, times(1)).range(anyString(), anyLong(), anyLong());
    }

    @Test
    @SuppressWarnings("unchecked")
    void subscribesBeforeReplayAndReadsOnlyWhenNotified() throws Exception {
        var redis = mock(StringRedisTemplate.class);
        ListOperations<String, String> lists = mock(ListOperations.class);
        when(redis.opsForList()).thenReturn(lists);
        var notifier = mock(ChatRunEventNotifier.class);
        Sinks.Many<Boolean> signals = Sinks.many().unicast().onBackpressureBuffer();
        var subscribed = new AtomicInteger();
        when(notifier.watch("run")).thenReturn(signals.asFlux()
                .doOnSubscribe(ignored -> subscribed.incrementAndGet()));
        var rows = new CopyOnWriteArrayList<String>();
        rows.add("{\"seq\":2,\"type\":\"chunk\",\"content\":\"你好\"}");
        var reads = new AtomicInteger();
        when(lists.range(anyString(), anyLong(), anyLong())).thenAnswer(call -> {
            assertEquals(1, subscribed.get(), "notification listener must precede replay");
            reads.incrementAndGet();
            int from = Math.toIntExact(call.getArgument(1));
            int to = Math.min(rows.size(), Math.toIntExact((Long) call.getArgument(2)) + 1);
            return from >= rows.size() ? List.of() : List.copyOf(rows.subList(from, to));
        });
        var events = new ChatRunEvents(redis, new ObjectMapper(), notifier);
        var received = new CopyOnWriteArrayList<String>();
        var subscription = events.stream("run", 0).subscribe(received::add);
        try {
            signals.tryEmitNext(true); // subscription ready: replay
            await(() -> received.size() == 1);
            int initialReads = reads.get();
            Thread.sleep(600);
            assertEquals(initialReads, reads.get(), "idle streams must not poll Redis");
            rows.add("{\"seq\":4,\"type\":\"tool_references\",\"items\":[]}");
            signals.tryEmitNext(true);
            await(() -> received.size() == 2);
            signals.tryEmitNext(true); // duplicate notification must not duplicate events
            rows.add("{\"seq\":5,\"type\":\"done\"}");
            signals.tryEmitNext(true);
            await(() -> received.size() == 3);
            assertTrue(received.get(0).contains("你好"));
            assertTrue(received.get(1).contains("tool_references"));
        } finally {
            subscription.dispose();
        }
    }

    static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(condition.getAsBoolean(), "condition not reached before timeout");
    }
}
