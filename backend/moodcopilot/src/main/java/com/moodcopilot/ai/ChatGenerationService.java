package com.moodcopilot.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.UserEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将聊天生成任务与 SSE 连接解耦，并在 Redis 中保留有序事件，支持断线后按序号回放。
 */
@Service
public class ChatGenerationService {
    private static final Logger log = LoggerFactory.getLogger(ChatGenerationService.class);
    private static final String RUN_PREFIX = "chat:run:";
    private static final String IDEMPOTENCY_PREFIX = "chat:run:idempotency:";
    private static final Duration RUN_TTL = Duration.ofDays(1);
    private static final Duration CHAT_HISTORY_TTL = Duration.ofDays(7);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(250);
    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(30);
    private static final DefaultRedisScript<Long> TRANSITION_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('HGET', KEYS[1], 'status') == ARGV[1] then "
                    + "redis.call('HSET', KEYS[1], 'status', ARGV[2], 'updatedAt', ARGV[3]); "
                    + "redis.call('EXPIRE', KEYS[1], ARGV[4]); return 1; end; return 0;",
            Long.class);

    private final ChatService chatService;
    private final MemoryExtractionService memoryExtractionService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Executor aiExecutor;

    public ChatGenerationService(ChatService chatService, MemoryExtractionService memoryExtractionService,
            StringRedisTemplate redis, ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Qualifier("aiExecutor") Executor aiExecutor) {
        this.chatService = chatService;
        this.memoryExtractionService = memoryExtractionService;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.aiExecutor = aiExecutor;
    }

    public record StartRequest(
            long userId,
            long conversationId,
            String clientRequestId,
            String message,
            List<String> references,
            List<UserReference> resolvedReferences,
            ReferencePurpose referencePurpose,
            boolean useReasoning,
            Authentication authentication) {
    }

    public record RunSnapshot(String runId, String status, long lastSequence) {
    }

    public RunSnapshot start(StartRequest request) {
        if (request.clientRequestId() == null || request.clientRequestId().isBlank()) {
            throw new IllegalArgumentException("clientRequestId 不能为空");
        }
        String idempotencyKey = IDEMPOTENCY_PREFIX + request.userId() + ":" + request.clientRequestId();
        String existing = redis.opsForValue().get(idempotencyKey);
        if (existing != null && !existing.isBlank()) {
            return snapshot(existing, request.userId(), request.conversationId());
        }

        String runId = UUID.randomUUID().toString();
        Boolean claimed = redis.opsForValue().setIfAbsent(idempotencyKey, runId, RUN_TTL);
        if (!Boolean.TRUE.equals(claimed)) {
            String winner = redis.opsForValue().get(idempotencyKey);
            if (winner == null || winner.isBlank()) throw new IllegalStateException("生成任务暂时无法创建");
            return snapshot(winner, request.userId(), request.conversationId());
        }

        String metaKey = metaKey(runId);
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("userId", String.valueOf(request.userId()));
        meta.put("conversationId", String.valueOf(request.conversationId()));
        meta.put("status", "RUNNING");
        meta.put("lastSequence", "0");
        meta.put("createdAt", LocalDateTime.now().toString());
        meta.put("updatedAt", LocalDateTime.now().toString());
        meta.put("model", request.useReasoning() ? "PRO" : "FLASH");
        redis.opsForHash().putAll(metaKey, meta);
        expire(runId);

        aiExecutor.execute(() -> run(request, runId));
        return new RunSnapshot(runId, "RUNNING", 0);
    }

    public void run(StartRequest request, String runId) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(request.authentication());
        SecurityContextHolder.setContext(context);
        StringBuilder reply = new StringBuilder();
        try {
            chatService.scheduleConversationTitle(request.conversationId(), request.message());
            ChatService.ChatStreamContext result = chatService.chat(
                    request.conversationId(), request.message(), request.references(), "",
                    request.useReasoning(), request.referencePurpose(), request.resolvedReferences());
            if (!writeEvent(runId, event("references", Map.of("items", parseRagReferences(result.ragContext()))))) {
                throw new IllegalStateException("保存聊天引用事件失败");
            }
            result.stream().doOnNext(chunk -> {
                if ("CANCELLED".equals(status(runId))) throw new CancellationException("生成任务已取消");
                if (chunk == null || chunk.isBlank()) return;
                if (chunk.startsWith("[[TOOL_EVENT]]")) {
                    if (!writeEvent(runId, parseJsonEvent(chunk.substring("[[TOOL_EVENT]]".length())))) {
                        throw new IllegalStateException("保存聊天工具事件失败");
                    }
                    return;
                }
                reply.append(chunk);
                if (!writeEvent(runId, event("chunk", Map.of("content", chunk)))) {
                    throw new IllegalStateException("保存聊天片段事件失败");
                }
            }).blockLast();

            if (!transitionStatus(runId, "RUNNING", "FINALIZING")) {
                return;
            }
            appendAssistantMessage(request, runId, reply.toString());
            if (!writeEvent(runId, event("done", Map.of()))) {
                throw new IllegalStateException("保存聊天完成事件失败");
            }
            setStatus(runId, "SUCCEEDED");
            scheduleMemoryExtraction(request, runId, reply.toString());
        } catch (CancellationException e) {
            log.info("聊天生成任务已取消 runId={} userId={} conversationId={}", runId,
                    request.userId(), request.conversationId());
            setStatus(runId, "CANCELLED");
        } catch (Exception e) {
            log.warn("聊天生成任务失败 runId={} userId={} conversationId={} reason={}",
                    runId, request.userId(), request.conversationId(), e.getMessage());
            boolean markedFailed = transitionStatus(runId, "FINALIZING", "FAILED")
                    || transitionStatus(runId, "RUNNING", "FAILED");
            if (markedFailed) {
                writeEvent(runId, event("error", Map.of("message", "AI 服务暂时无法完成本次回答")));
            }
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    /**
     * 画像增量提取不能阻塞聊天完成事件。聊天回答已经落库并通知前端完成后，
     * 再使用独立 AI 任务执行画像更新；失败只记录日志，不影响本轮聊天状态。
     */
    private void scheduleMemoryExtraction(StartRequest request, String runId, String reply) {
        List<String> evidence = request.resolvedReferences() == null
                ? (request.references() == null ? List.of() : List.copyOf(request.references()))
                : request.resolvedReferences().stream().map(UserReference::content).toList();
        aiExecutor.execute(() -> {
            long startedAt = System.nanoTime();
            try {
                memoryExtractionService.extractAndSyncMemoryFromChat(
                        request.userId(), request.conversationId(), request.message(), evidence, reply);
                log.info("聊天画像异步更新完成 runId={} userId={} conversationId={} durationMs={}", runId,
                        request.userId(), request.conversationId(), elapsedMillis(startedAt));
            } catch (Exception e) {
                log.warn("聊天结果已保存，但画像异步更新失败 runId={} userId={} conversationId={} durationMs={} reason={}",
                        runId, request.userId(), request.conversationId(), elapsedMillis(startedAt), e.getMessage());
            }
        });
    }

    public RunSnapshot snapshot(String runId, long userId, long conversationId) {
        assertOwner(runId, userId, conversationId);
        String status = stringValue(redis.opsForHash().get(metaKey(runId), "status"), "FAILED");
        long lastSequence = longValue(redis.opsForHash().get(metaKey(runId), "lastSequence"));
        return new RunSnapshot(runId, status, lastSequence);
    }

    public Flux<String> stream(String runId, long userId, long conversationId, long afterSequence) {
        assertOwner(runId, userId, conversationId);
        AtomicLong cursor = new AtomicLong(Math.max(0, afterSequence));
        return Flux.interval(Duration.ZERO, POLL_INTERVAL)
                .publishOn(Schedulers.boundedElastic())
                .concatMap(tick -> {
                    List<String> events = eventsAfter(runId, cursor.get());
                    if (!events.isEmpty()) {
                        cursor.set(sequenceOf(events.get(events.size() - 1), cursor.get()));
                    }
                    return Flux.fromIterable(events);
                })
                .takeUntil(event -> isTerminal(event))
                .timeout(STREAM_TIMEOUT)
                .doFinally(signal -> log.debug("聊天事件订阅结束 runId={} signal={}", runId, signal));
    }

    public void cancel(String runId, long userId, long conversationId) {
        assertOwner(runId, userId, conversationId);
        if (transitionStatus(runId, "RUNNING", "CANCELLED")) {
            if (!writeEvent(runId, event("error", Map.of("message", "本次回答已取消")))) {
                log.warn("保存取消事件失败 runId={} conversationId={}", runId, conversationId);
            }
        }
    }

    private List<String> eventsAfter(String runId, long afterSequence) {
        List<String> values = redis.opsForList().range(eventsKey(runId), 0, -1);
        if (values == null || values.isEmpty()) return List.of();
        return values.stream().filter(value -> sequenceOf(value, 0) > afterSequence).toList();
    }

    private void appendAssistantMessage(StartRequest request, String runId, String content) {
        if (content == null || content.isBlank()) return;
        String key = "chat:msgs:" + request.conversationId();
        try {
            Object raw = redis.opsForValue().get(key);
            List<Map<String, Object>> messages = raw == null || raw.toString().isBlank()
                    ? new ArrayList<>() : objectMapper.readValue(raw.toString(), new TypeReference<>() {});
            boolean alreadySaved = messages.stream().anyMatch(message ->
                    (runId + ":assistant").equals(String.valueOf(message.get("id"))));
            if (!alreadySaved) {
                Map<String, Object> assistant = new LinkedHashMap<>();
                assistant.put("id", runId + ":assistant");
                assistant.put("role", "ai");
                assistant.put("content", content);
                assistant.put("createdAt", LocalDateTime.now().toString());
                messages.add(assistant);
                redis.opsForValue().set(key, objectMapper.writeValueAsString(messages), CHAT_HISTORY_TTL);
            }
        } catch (Exception e) {
            log.warn("保存生成结果失败 runId={} conversationId={} reason={}", runId,
                    request.conversationId(), e.getMessage());
            throw new IllegalStateException("保存生成结果失败", e);
        }
    }

    private boolean writeEvent(String runId, String json) {
        String key = metaKey(runId);
        Long sequence = redis.opsForHash().increment(key, "lastSequence", 1);
        try {
            Map<String, Object> payload = objectMapper.readValue(json, new TypeReference<>() {});
            payload.put("seq", sequence == null ? 0 : sequence);
            redis.opsForList().rightPush(eventsKey(runId), objectMapper.writeValueAsString(payload));
            expire(runId);
            return true;
        } catch (Exception e) {
            log.warn("保存聊天生成事件失败 runId={} reason={}", runId, e.getMessage());
            return false;
        }
    }

    private String event(String type, Map<String, Object> values) {
        Map<String, Object> payload = new LinkedHashMap<>(values);
        payload.put("type", type);
        try { return objectMapper.writeValueAsString(payload); }
        catch (Exception e) { throw new IllegalStateException("生成事件序列化失败", e); }
    }

    private String parseJsonEvent(String raw) {
        try {
            objectMapper.readTree(raw);
            return raw;
        } catch (Exception ignored) {
            return event("tool_references", Map.of("items", List.of()));
        }
    }

    private List<Map<String, String>> parseRagReferences(String ragContext) {
        List<Map<String, String>> items = new ArrayList<>();
        if (ragContext == null || ragContext.isBlank()) return items;
        Pattern pattern = Pattern.compile(
                "<item\\s+source_type=\"([^\"]+)\"\\s+source_id=\"([^\"]*)\"(?:\\s+event_time=\"([^\"]*)\")?[^>]*>(.*?)</item>",
                Pattern.DOTALL);
        Matcher matcher = pattern.matcher(ragContext);
        while (matcher.find()) {
            String type = "USER_DIARY".equals(matcher.group(1)) ? "diary" : matcher.group(1);
            String snippet = matcher.group(4).replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
            Map<String, String> item = new LinkedHashMap<>();
            item.put("type", type);
            item.put("diaryId", "diary".equals(type) ? matcher.group(2) : "");
            item.put("date", matcher.group(3) == null ? "" : matcher.group(3));
            item.put("snippet", snippet.length() > 120 ? snippet.substring(0, 120) + "…" : snippet);
            items.add(item);
        }
        return items;
    }

    private boolean isTerminal(String json) {
        try {
            String type = objectMapper.readTree(json).path("type").asText();
            return "done".equals(type) || "error".equals(type);
        } catch (Exception ignored) { return false; }
    }

    private long sequenceOf(String json, long fallback) {
        try { return objectMapper.readTree(json).path("seq").asLong(fallback); }
        catch (Exception ignored) { return fallback; }
    }

    private void setStatus(String runId, String status) {
        redis.opsForHash().put(metaKey(runId), "status", status);
        redis.opsForHash().put(metaKey(runId), "updatedAt", LocalDateTime.now().toString());
        expire(runId);
    }

    private boolean transitionStatus(String runId, String expected, String next) {
        Long result = redis.execute(TRANSITION_SCRIPT, List.of(metaKey(runId)), expected, next,
                LocalDateTime.now().toString(), String.valueOf(RUN_TTL.toSeconds()));
        return Long.valueOf(1L).equals(result);
    }

    private String status(String runId) {
        return stringValue(redis.opsForHash().get(metaKey(runId), "status"), "FAILED");
    }

    private void assertOwner(String runId, long userId, Long conversationId) {
        String owner = stringValue(redis.opsForHash().get(metaKey(runId), "userId"), null);
        String runConversationId = stringValue(redis.opsForHash().get(metaKey(runId), "conversationId"), null);
        if (owner == null || !owner.equals(String.valueOf(userId))
                || (conversationId != null && !conversationId.toString().equals(runConversationId))) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "生成任务不存在");
        }
    }

    private void expire(String runId) {
        redis.expire(metaKey(runId), RUN_TTL);
        redis.expire(eventsKey(runId), RUN_TTL);
    }

    private String metaKey(String runId) { return RUN_PREFIX + runId + ":meta"; }
    private String eventsKey(String runId) { return RUN_PREFIX + runId + ":events"; }
    private String stringValue(Object value, String fallback) { return value == null ? fallback : String.valueOf(value); }
    private long longValue(Object value) { try { return Long.parseLong(String.valueOf(value)); } catch (Exception e) { return 0; } }
    private long elapsedMillis(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
