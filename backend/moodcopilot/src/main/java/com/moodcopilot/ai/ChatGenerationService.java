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
            Authentication authentication,
            List<String> imageUrls,
            /** 这个客户端有没有能力把审批弹框送到用户眼前。没有就别让图停 —— 停下来没人能点。 */
            boolean approvalsInteractive) {
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
        // 恢复一轮要用到的东西。它们在暂停之前就定了，而用户可能几分钟后才点确认、
        // 甚至中间重启过一次后端 —— 到那时内存里已经什么都不剩，只能从 meta 里拿。
        meta.put("useReasoning", String.valueOf(request.useReasoning()));
        meta.put("userMessage", request.message() == null ? "" : request.message());
        meta.put("imageUrls", writeStringList(request.imageUrls()));
        meta.put("userReferences", writeStringList(evidenceOf(request)));
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
        try {
            chatService.scheduleConversationTitle(request.conversationId(), request.message());
            // 图片描述是在模型调用之前同步生成的，OCR 一张带文字的图可能要几十秒。
            // 先把状态帧写进事件表，客户端重放时就能看到「在干什么」而不是干等。
            if (request.imageUrls() != null && !request.imageUrls().isEmpty()) {
                writeEvent(runId, event("status",
                        Map.of("stage", "reading_images", "message", "正在识别图片内容…")));
            }
            // runId 同时是图检查点的 threadId：工具审批中断后靠它恢复
            ChatService.ChatStreamContext result = chatService.chat(
                    request.conversationId(), request.message(), request.references(), "",
                    request.useReasoning(), request.referencePurpose(), request.resolvedReferences(),
                    null, request.imageUrls(), runId, request.approvalsInteractive());
            if (!writeEvent(runId, event("references", Map.of("items", parseRagReferences(result.ragContext()))))) {
                throw new IllegalStateException("保存聊天引用事件失败");
            }
            consume(runId, result.stream());

            // 停在「工具执行前等用户批准」。这一轮没结束：不写 done、不置 SUCCEEDED，
            // 更不能拿半截正文去跑画像抽取 —— 助手回复整个推迟到 approve 之后。
            if (result.outcome().paused()) {
                awaitApproval(runId, result.outcome());
                return;
            }

            if (!transitionStatus(runId, "RUNNING", "FINALIZING")) {
                return;
            }
            // 历史由 ChatService 统一落库（appendToChatMemory），这里不再写第二遍
            if (!writeEvent(runId, event("done", Map.of()))) {
                throw new IllegalStateException("保存聊天完成事件失败");
            }
            setStatus(runId, "SUCCEEDED");
            scheduleMemoryExtraction(runId, request.userId(), request.conversationId(), request.message(),
                    evidenceOf(request), result.outcome().reply());
        } catch (CancellationException e) {
            log.info("聊天生成任务已取消 runId={} userId={} conversationId={}", runId,
                    request.userId(), request.conversationId());
            setStatus(runId, "CANCELLED");
        } catch (com.moodcopilot.common.RateLimitException e) {
            // 限流原因（“今日聊天 Pro 次数已用完…”）必须原样送到用户眼前。
            // 之前被下面的通用 catch 吞成一句无用的“AI 服务暂时无法完成本次回答”，
            // 用户完全看不出是自己额度用完了。
            log.info("聊天生成任务被限流 runId={} userId={} conversationId={} reason={}", runId,
                    request.userId(), request.conversationId(), e.getMessage());
            boolean rateLimited = transitionStatus(runId, "FINALIZING", "FAILED")
                    || transitionStatus(runId, "RUNNING", "FAILED");
            String message = e.getMessage() == null || e.getMessage().isBlank()
                    ? "AI 服务暂时无法完成本次回答" : e.getMessage();
            if (rateLimited) {
                writeEvent(runId, event("error", Map.of("message", message)));
            }
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
     * 把图产出的分片写进事件表。取消在这里生效：客户端点过停之后，后续分片一律丢弃。
     * <p>
     * 恢复走的也是它 —— 续跑的分片要接在暂停前那些事件后面，序号继续往下长，
     * 这样客户端拿着同一个 {@code after=} 游标就能把两段接起来。
     */
    private void consume(String runId, Flux<String> stream) {
        stream.doOnNext(chunk -> {
            if ("CANCELLED".equals(status(runId))) throw new CancellationException("生成任务已取消");
            if (chunk == null || chunk.isBlank()) return;
            if (chunk.startsWith("[[TOOL_EVENT]]")) {
                if (!writeEvent(runId, parseJsonEvent(chunk.substring("[[TOOL_EVENT]]".length())))) {
                    throw new IllegalStateException("保存聊天工具事件失败");
                }
                return;
            }
            if (!writeEvent(runId, event("chunk", Map.of("content", chunk)))) {
                throw new IllegalStateException("保存聊天片段事件失败");
            }
        }).blockLast();
    }

    /**
     * 记下「停在工具执行前等用户批准」。
     * <p>
     * 帧先发、状态后改：反过来的话，一旦事件写失败，用户看到的就是一条永远没有下文的
     * AWAITING_APPROVAL，而前端会一直轮询到超时 —— 那比看不见弹框更难排查。
     */
    private void awaitApproval(String runId, AgentLoopOutcome outcome) {
        if (!writeEvent(runId, event("approval_required", approvalPayload(outcome)))) {
            throw new IllegalStateException("保存待批准事件失败");
        }
        if (!transitionStatus(runId, "RUNNING", "AWAITING_APPROVAL")) {
            log.warn("生成任务已不在运行中，待批准状态未生效 runId={}", runId);
        }
    }

    private static Map<String, Object> approvalPayload(AgentLoopOutcome outcome) {
        Object approval = outcome.pendingApproval().get(ChatAgentLoop.APPROVAL_KEY);
        Object items = approval instanceof Map<?, ?> map ? map.get("items") : null;
        return Map.of("items", items instanceof List<?> list ? list : List.of());
    }

    /**
     * 画像增量提取不能阻塞聊天完成事件。聊天回答已经落库并通知前端完成后，
     * 再使用独立 AI 任务执行画像更新；失败只记录日志，不影响本轮聊天状态。
     */
    private void scheduleMemoryExtraction(String runId, long userId, long conversationId, String message,
            List<String> evidence, String reply) {
        aiExecutor.execute(() -> {
            long startedAt = System.nanoTime();
            try {
                memoryExtractionService.extractAndSyncMemoryFromChat(userId, conversationId, message, evidence, reply);
                log.info("聊天画像异步更新完成 runId={} userId={} conversationId={} durationMs={}", runId, userId,
                        conversationId, elapsedMillis(startedAt));
            } catch (Exception e) {
                log.warn("聊天结果已保存，但画像异步更新失败 runId={} userId={} conversationId={} durationMs={} reason={}",
                        runId, userId, conversationId, elapsedMillis(startedAt), e.getMessage());
            }
        });
    }

    /** 证据只取用户自己的内容：引用到的原文优先，没有就用裸引用串。 */
    private static List<String> evidenceOf(StartRequest request) {
        return request.resolvedReferences() == null
                ? (request.references() == null ? List.of() : List.copyOf(request.references()))
                : request.resolvedReferences().stream().map(UserReference::content).toList();
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

    /**
     * 用户对「工具执行前的人工批准」表态，从检查点续跑。
     * <p>
     * 表态是**逐条**的：一批里可能有好几组待批准（例如一次合并好几组记忆），用户可以只批其中几组。
     * 没被点名的那些按拒绝处理 —— 没点头的事不该做。
     * <p>
     * 续跑产生的分片续写进同一条 run 的事件表，客户端拿着原来的 {@code after=} 游标就能接上，
     * 因此这里不需要把上下文再传一遍 —— 它在暂停之前就存进 meta 了。
     *
     * @return 同一条 run 的最新快照（状态已回到 RUNNING）
     */
    public RunSnapshot approve(String runId, long userId, long conversationId, Authentication authentication,
            List<ApprovalChoice> decisions) {
        assertOwner(runId, userId, conversationId);
        // 先占住状态再起异步任务：连点两下确认时，第二下在这儿就被挡住，
        // 不会让同一份检查点被恢复两遍 —— 那会把工具执行两次，记忆也被写两遍。
        if (!transitionStatus(runId, "AWAITING_APPROVAL", "RUNNING")) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "这条生成任务不在等待确认");
        }
        ResumeContext resume = readResumeContext(runId);
        long lastSequence = longValue(redis.opsForHash().get(metaKey(runId), "lastSequence"));
        aiExecutor.execute(() -> resumeRun(runId, userId, conversationId, resume, authentication, decisions));
        return new RunSnapshot(runId, "RUNNING", lastSequence);
    }

    private void resumeRun(String runId, long userId, long conversationId, ResumeContext resume,
            Authentication authentication, List<ApprovalChoice> decisions) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        try {
            log.info("用户已对工具调用表态，从检查点续跑 runId={} userId={} conversationId={} decisions={}", runId,
                    userId, conversationId, decisions.size());
            ChatTurn turn = new ChatTurn(resume.imageUrls(), resume.userMessage(), conversationId,
                    resume.userReferences());
            ChatService.ChatStreamContext result = chatService.resumeChat(userId, turn, resume.useReasoning(),
                    ChatAgentLoop.approvalDecision(decisions), runId);
            consume(runId, result.stream());

            if (result.outcome().paused()) {
                // 模型在同一轮里又要求写一次：重新征求同意，不能把上一次的点头当成长期授权
                awaitApproval(runId, result.outcome());
                return;
            }
            if (!transitionStatus(runId, "RUNNING", "FINALIZING")) {
                return;
            }
            if (!writeEvent(runId, event("done", Map.of()))) {
                throw new IllegalStateException("保存聊天完成事件失败");
            }
            setStatus(runId, "SUCCEEDED");
            scheduleMemoryExtraction(runId, userId, conversationId, resume.userMessage(), resume.userReferences(),
                    result.outcome().reply());
        } catch (CancellationException e) {
            log.info("聊天生成任务已取消 runId={} userId={} conversationId={}", runId, userId, conversationId);
            setStatus(runId, "CANCELLED");
        } catch (com.moodcopilot.common.RateLimitException e) {
            log.info("聊天生成任务被限流 runId={} userId={} reason={}", runId, userId, e.getMessage());
            boolean rateLimited = transitionStatus(runId, "FINALIZING", "FAILED")
                    || transitionStatus(runId, "RUNNING", "FAILED");
            String message = e.getMessage() == null || e.getMessage().isBlank()
                    ? "AI 服务暂时无法完成本次回答" : e.getMessage();
            if (rateLimited) {
                writeEvent(runId, event("error", Map.of("message", message)));
            }
        } catch (Exception e) {
            log.warn("恢复聊天生成失败 runId={} userId={} conversationId={} reason={}", runId, userId, conversationId,
                    e.getMessage());
            boolean markedFailed = transitionStatus(runId, "FINALIZING", "FAILED")
                    || transitionStatus(runId, "RUNNING", "FAILED");
            if (markedFailed) {
                writeEvent(runId, event("error", Map.of("message", "AI 服务暂时无法完成本次回答")));
            }
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    /** 续跑一轮所需的东西：暂停之前就定了，随 run 一起存进 meta。 */
    private record ResumeContext(boolean useReasoning, String userMessage, List<String> imageUrls,
            List<String> userReferences) {}

    private ResumeContext readResumeContext(String runId) {
        return new ResumeContext(
                Boolean.parseBoolean(stringValue(redis.opsForHash().get(metaKey(runId), "useReasoning"), "false")),
                stringValue(redis.opsForHash().get(metaKey(runId), "userMessage"), ""),
                readStringList(redis.opsForHash().get(metaKey(runId), "imageUrls")),
                readStringList(redis.opsForHash().get(metaKey(runId), "userReferences")));
    }

    /** meta 里存的是 JSON 数组。解析不了就当没有：恢复时顶多少一份证据，不该把整轮弄失败。 */
    private List<String> readStringList(Object value) {
        if (value == null) return List.of();
        try {
            List<String> parsed = objectMapper.readValue(String.valueOf(value), new TypeReference<>() {});
            return parsed == null ? List.of() : List.copyOf(parsed);
        } catch (Exception e) {
            log.warn("解析生成任务元数据失败 reason={}", e.getMessage());
            return List.of();
        }
    }

    private String writeStringList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception e) {
            return "[]";
        }
    }

    public void cancel(String runId, long userId, long conversationId) {
        assertOwner(runId, userId, conversationId);
        // 暂停中的任务也要能取消。少了这条，弹框一挂用户就只剩「等超时」一条路。
        if (transitionStatus(runId, "RUNNING", "CANCELLED")
                || transitionStatus(runId, "AWAITING_APPROVAL", "CANCELLED")) {
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
            if ("diary".equals(type)) {
                snippet = snippet.replaceAll("\\[图片描述[：:].*?\\]\\s*", "")
                                 .replaceAll("\\[分享图片\\]\\s*", "")
                                 .replaceAll("\\[分享音乐[：:].*?\\]\\s*", "");
                if (snippet.isBlank()) {
                    snippet = "分享了多媒体内容";
                }
            }
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
