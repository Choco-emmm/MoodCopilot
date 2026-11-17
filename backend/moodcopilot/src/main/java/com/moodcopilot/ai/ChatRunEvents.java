package com.moodcopilot.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Durable append + ephemeral wakeup, followed by cursor-based replay/tailing. */
@Component
public class ChatRunEvents {
    private static final Logger log = LoggerFactory.getLogger(ChatRunEvents.class);
    private static final int PAGE_SIZE = 256;
    private static final Duration TTL = Duration.ofDays(1);
    static final DefaultRedisScript<Long> APPEND = new DefaultRedisScript<>("""
            local metaType = redis.call('TYPE', KEYS[1]).ok
            local eventsType = redis.call('TYPE', KEYS[2]).ok
            if metaType ~= 'hash' then return redis.error_reply('run metadata missing or invalid') end
            if eventsType ~= 'none' and eventsType ~= 'list' then return redis.error_reply('invalid run events type') end
            local payload = cjson.decode(ARGV[1])
            if type(payload) ~= 'table' or type(payload.type) ~= 'string' then
                return redis.error_reply('invalid run event')
            end
            local ttl = tonumber(ARGV[3])
            if not ttl or ttl <= 0 then return redis.error_reply('invalid ttl') end
            local previous = redis.call('HGET', KEYS[1], 'lastSequence')
            if not previous or not string.match(previous, '^%d+$') then
                return redis.error_reply('invalid run sequence')
            end
            if payload.type == 'approval_required' and redis.call('HGET', KEYS[1], 'status') ~= 'RUNNING' then
                return redis.error_reply('run is not running')
            end
            local seq = redis.call('HINCRBY', KEYS[1], 'lastSequence', 1)
            -- Preserve Jackson's arrays/objects verbatim: Lua cjson turns empty arrays into objects.
            local event = '{"seq":' .. string.format('%.0f', seq) .. ',' .. string.sub(ARGV[1], 2)
            redis.call('RPUSH', KEYS[2], event)
            -- Readers must never observe an approval event while status still says RUNNING.
            -- Persist first, then change status, then publish, with no interleaving commands.
            if payload.type == 'approval_required' then
                redis.call('HSET', KEYS[1], 'status', 'AWAITING_APPROVAL', 'updatedAt', ARGV[5])
            end
            redis.call('EXPIRE', KEYS[1], ttl)
            redis.call('EXPIRE', KEYS[2], ttl)
            redis.call('PUBLISH', ARGV[4], cjson.encode({runId=ARGV[2], seq=seq}))
            return seq
            """, Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final ChatRunEventNotifier notifier;

    public ChatRunEvents(StringRedisTemplate redis, ObjectMapper mapper, ChatRunEventNotifier notifier) {
        this.redis = redis;
        this.mapper = mapper;
        this.notifier = notifier;
    }

    public boolean append(String runId, String json) {
        try {
            Map<String, Object> payload = mapper.readValue(json, new TypeReference<>() {});
            payload.remove("seq");
            Long seq = redis.execute(APPEND, List.of(metaKey(runId), eventsKey(runId)),
                    mapper.writeValueAsString(payload), runId, Long.toString(TTL.toSeconds()),
                    ChatRunEventNotifier.CHANNEL, LocalDateTime.now().toString());
            return seq != null && seq > 0;
        } catch (Exception error) {
            log.warn("保存聊天生成事件失败 runId={} errorType={}", runId, error.getClass().getSimpleName());
            return false;
        }
    }

    public Flux<String> stream(String runId, long afterSequence) {
        return Flux.defer(() -> {
            Cursor cursor = new Cursor(Math.max(0, afterSequence));
            return notifier.watch(runId)
                    // One pending wakeup is enough: the drain reads every persisted new event.
                    .onBackpressureLatest()
                    .publishOn(Schedulers.boundedElastic(), 1)
                    .concatMap(ignored -> drain(runId, cursor), 1)
                    .takeUntil(this::terminal)
                    .timeout(Duration.ofMinutes(30));
        });
    }

    private Flux<String> drain(String runId, Cursor cursor) {
        return Flux.defer(() -> {
            List<String> page = redis.opsForList().range(eventsKey(runId), cursor.index,
                    cursor.index + PAGE_SIZE - 1);
            if (page == null || page.isEmpty()) return Flux.empty();
            cursor.index += page.size();
            Flux<String> events = Flux.fromIterable(page).filter(json -> {
                long sequence = sequence(json);
                if (sequence <= cursor.sequence) return false;
                cursor.sequence = sequence;
                return true;
            });
            // Defer the next read until downstream has consumed this page.
            return page.size() == PAGE_SIZE ? events.concatWith(drain(runId, cursor)) : events;
        });
    }

    private boolean terminal(String json) {
        try {
            String type = mapper.readTree(json).path("type").asText();
            return "done".equals(type) || "error".equals(type);
        } catch (Exception error) {
            throw new IllegalStateException("聊天事件无法读取", error);
        }
    }

    private long sequence(String json) {
        try {
            return mapper.readTree(json).path("seq").asLong();
        } catch (Exception error) {
            throw new IllegalStateException("聊天事件无法读取", error);
        }
    }

    private static String metaKey(String runId) { return "chat:run:" + runId + ":meta"; }
    private static String eventsKey(String runId) { return "chat:run:" + runId + ":events"; }

    private static final class Cursor {
        long index;
        long sequence;
        Cursor(long sequence) { this.sequence = sequence; }
    }
}
