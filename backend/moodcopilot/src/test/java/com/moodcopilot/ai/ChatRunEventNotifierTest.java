package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.SubscriptionListener;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatRunEventNotifierTest {
    @Test
    void readinessResubscriptionAndCancellationAreConnectionScoped() {
        var container = mock(ReactiveRedisMessageListenerContainer.class);
        var callback = new AtomicReference<SubscriptionListener>();
        when(container.receive(anyIterable(), any(SubscriptionListener.class))).thenAnswer(call -> {
            callback.set(call.getArgument(1));
            return Flux.never();
        });
        var notifier = new ChatRunEventNotifier(container, new ObjectMapper());
        notifier.start();
        var first = new CopyOnWriteArrayList<Boolean>();
        var second = new CopyOnWriteArrayList<Boolean>();
        var one = notifier.watch("run").subscribe(first::add);
        var two = notifier.watch("run").subscribe(second::add);
        try {
            assertTrue(first.isEmpty(), "replay cannot start before Redis confirms subscription");
            callback.get().onChannelSubscribed(new byte[0], 1);
            assertEquals(1, first.size());
            assertEquals(1, second.size());
            one.dispose();
            callback.get().onChannelSubscribed(new byte[0], 1); // reconnect wakes remaining readers
            assertEquals(1, first.size());
            assertEquals(2, second.size());
        } finally {
            one.dispose();
            two.dispose();
            notifier.close();
        }
    }

    @Test
    void subscriptionReadinessHasAFiveSecondDeadline() {
        var notifier = new ChatRunEventNotifier(mock(ReactiveRedisMessageListenerContainer.class), new ObjectMapper());
        try {
            StepVerifier.withVirtualTime(() -> notifier.watch("run"))
                    .thenAwait(Duration.ofSeconds(5))
                    .expectError(java.util.concurrent.TimeoutException.class)
                    .verify(Duration.ofSeconds(3));
        } finally {
            notifier.close();
        }
    }
}
