package com.moodcopilot.ai;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.CancellationException;

/** Redis is authoritative, including when cancellation reaches a different backend instance. */
@Component
public class ChatRunCancellation {
    private final StringRedisTemplate redis;

    public ChatRunCancellation(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void check(String runId) {
        if (cancelled(runId)) {
            throw new CancellationException("生成任务已取消");
        }
    }

    /** Signals cancellation even without model output; the caller must dispose the upstream. */
    public Mono<Void> signal(String runId) {
        return Flux.interval(Duration.ZERO, Duration.ofMillis(250))
                .publishOn(Schedulers.boundedElastic())
                .filter(ignored -> cancelled(runId))
                .next()
                .flatMap(ignored -> Mono.error(new CancellationException("生成任务已取消")));
    }

    private boolean cancelled(String runId) {
        return "CANCELLED".equals(redis.opsForHash().get("chat:run:" + runId + ":meta", "status"));
    }
}
