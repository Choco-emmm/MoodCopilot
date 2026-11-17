package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.SubscriptionListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Cross-instance wakeups only. Persisted run events remain the authority. */
@Component
public class ChatRunEventNotifier {
    static final String CHANNEL = "chat:run:events:available";
    private static final Logger log = LoggerFactory.getLogger(ChatRunEventNotifier.class);
    private final ReactiveRedisMessageListenerContainer container;
    private final ObjectMapper mapper;
    private final Map<String, Set<FluxSink<Boolean>>> watchers = new HashMap<>();
    private boolean ready;
    private boolean closed;
    private Disposable listener;

    public ChatRunEventNotifier(ReactiveRedisMessageListenerContainer container, ObjectMapper mapper) {
        this.container = container;
        this.mapper = mapper;
    }

    @PostConstruct
    public void start() {
        listener = Flux.defer(() -> container.receive(List.of(ChannelTopic.of(CHANNEL)), new SubscriptionListener() {
                    @Override
                    public void onChannelSubscribed(byte[] channel, long count) {
                        subscriptionReady(); // also fires after Redis reconnect/resubscribe
                    }

                    @Override
                    public void onChannelUnsubscribed(byte[] channel, long count) {
                        unavailable();
                    }
                }))
                .concatWith(Flux.error(new IllegalStateException("聊天事件通知订阅意外结束")))
                .doOnError(error -> {
                    log.warn("聊天事件通知订阅中断 errorType={}", error.getClass().getSimpleName());
                    unavailable();
                })
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(10)))
                .subscribe(message -> {
                    try {
                        String runId = mapper.readTree(message.getMessage()).path("runId").asText("");
                        if (!runId.isBlank()) wake(runId);
                    } catch (Exception error) {
                        log.warn("聊天事件通知格式无效 errorType={}", error.getClass().getSimpleName());
                    }
                }, error -> unavailable());
    }

    /** Register before replay. A true value means subscription-ready or new data. */
    public Flux<Boolean> watch(String runId) {
        return Flux.<Boolean>create(sink -> {
            synchronized (this) {
                if (closed) {
                    sink.error(new IllegalStateException("聊天事件通知已关闭"));
                    return;
                }
                watchers.computeIfAbsent(runId, key -> new HashSet<>()).add(sink);
                sink.onDispose(() -> remove(runId, sink));
                if (ready) sink.next(true);
            }
        }, FluxSink.OverflowStrategy.LATEST)
                // Only readiness has a five-second deadline; approval can wait silently.
                .timeout(Mono.delay(Duration.ofSeconds(5)), ignored -> Mono.never());
    }

    private synchronized void remove(String runId, FluxSink<Boolean> sink) {
        var sinks = watchers.get(runId);
        if (sinks == null) return;
        sinks.remove(sink);
        if (sinks.isEmpty()) watchers.remove(runId);
    }

    private synchronized void subscriptionReady() {
        ready = true;
        // Reconcile persisted events after every resubscription, even if no new publish arrives.
        watchers.values().stream().flatMap(Set::stream).toList().forEach(sink -> sink.next(true));
    }

    private synchronized void wake(String runId) {
        var sinks = watchers.get(runId);
        if (sinks != null) List.copyOf(sinks).forEach(sink -> sink.next(true));
    }

    private synchronized void unavailable() {
        ready = false;
        var sinks = watchers.values().stream().flatMap(Set::stream).toList();
        watchers.clear();
        sinks.forEach(sink -> sink.error(new IllegalStateException("聊天事件通知暂时不可用，请重新连接")));
    }

    @PreDestroy
    public void close() {
        synchronized (this) {
            closed = true;
        }
        if (listener != null) listener.dispose();
        unavailable();
    }
}
