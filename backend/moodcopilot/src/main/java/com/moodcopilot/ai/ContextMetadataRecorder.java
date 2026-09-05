package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** Best-effort context provenance record. It never blocks the model request. */
@Service
public class ContextMetadataRecorder {
    private static final Logger log = LoggerFactory.getLogger(ContextMetadataRecorder.class);
    private static final Duration TTL = Duration.ofDays(30);
    private static final java.util.Set<String> ALLOWED_EXTRA_KEYS = java.util.Set.of(
            "personaVersion", "conversationPersonaVersion", "effectivePersonaHash", "taskType",
            "requestedModel", "actualModel", "useReasoning");

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public ContextMetadataRecorder(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /** Compatibility entry point for non-chat context producers. */
    @Async("aiExecutor")
    public void record(ContextEnvelope envelope) {
        write(envelope, Map.of());
    }

    @Async("aiExecutor")
    public void record(ContextEnvelope envelope, Map<String, ?> extras) {
        write(envelope, extras);
    }

    /**
     * Records a model invocation which does not already have a planned context
     * envelope (for example, the legacy memory extraction fallback). Only
     * provenance metadata is stored; prompt contents are never persisted here.
     */
    @Async("aiExecutor")
    public void recordModelInvocation(Long userId, Long conversationId, ContextPurpose purpose,
            EffectivePersona persona, TaskContext taskContext, String requestedModel, String actualModel) {
        if (userId == null || userId <= 0) return;
        ContextEnvelope envelope = new ContextEnvelope(
                UUID.randomUUID().toString(), conversationId, userId,
                purpose == null ? ContextPurpose.CHAT : purpose, Instant.now(), "2",
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("personaVersion", persona == null || persona.globalVersion() == null ? 0 : persona.globalVersion());
        extras.put("conversationPersonaVersion",
                persona == null || persona.conversationVersion() == null ? 0 : persona.conversationVersion());
        extras.put("effectivePersonaHash", persona == null ? "default" : persona.effectivePersonaHash());
        extras.put("taskType", taskContext == null ? "GENERAL" : taskContext.taskType());
        extras.put("requestedModel", requestedModel == null ? "unknown" : requestedModel);
        extras.put("actualModel", actualModel == null ? "unknown" : actualModel);
        extras.put("useReasoning", isReasoningModel(actualModel));
        write(envelope, extras);
    }

    private boolean isReasoningModel(String model) {
        if (model == null) return false;
        String normalized = model.trim().toLowerCase(java.util.Locale.ROOT);
        return "pro".equals(normalized) || normalized.contains("-pro")
                || normalized.contains("_pro") || normalized.contains("reasoning");
    }

    private void write(ContextEnvelope envelope, Map<String, ?> extras) {
        if (envelope == null || redis == null) return;
        try {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("contextId", envelope.contextId());
            metadata.put("conversationId", envelope.conversationId());
            metadata.put("userId", envelope.userId());
            metadata.put("contextPurpose", envelope.contextPurpose().name());
            metadata.put("generatedAt", envelope.generatedAt().toString());
            metadata.put("plannerVersion", envelope.plannerVersion());
            metadata.put("personaVersion", 0);
            metadata.put("conversationPersonaVersion", 0);
            metadata.put("effectivePersonaHash", "default");
            metadata.put("taskType", "GENERAL");
            metadata.put("requestedModel", "unknown");
            metadata.put("actualModel", "unknown");
            metadata.put("useReasoning", false);
            metadata.put("sourceIds", sourceIds(envelope));
            metadata.put("retrievalModes", retrievalModes(envelope));
            metadata.put("retrievalPolicyVersion", "2");
            metadata.put("promptTemplateVersion", "conversation-context-v2");
            metadata.put("memorySnapshotVersion", memorySnapshotVersion(envelope));
            // Keep envelope identity and provenance immutable. Callers may add only
            // the explicitly defined audit fields, never replace userId/sourceIds.
            if (extras != null) {
                extras.forEach((key, value) -> {
                    if (key != null && ALLOWED_EXTRA_KEYS.contains(key) && value != null) {
                        metadata.put(key, value);
                    }
                });
            }
            String key = "rag:context:" + envelope.userId() + ":" + envelope.contextId();
            redis.opsForValue().set(key, objectMapper.writeValueAsString(metadata), TTL);
        } catch (Exception e) {
            log.debug("上下文元数据保存失败 contextId={} userId={} errorType={}",
                    envelope.contextId(), envelope.userId(), e.getClass().getSimpleName());
        }
    }

    private List<String> sourceIds(ContextEnvelope envelope) {
        return java.util.stream.Stream.of(envelope.coreMemory(), envelope.shortTermState(),
                envelope.userReferences().stream().map(reference -> new ContextItem(reference.content(), reference.source(),
                        reference.relevanceScore(), reference.priority(), reference.conflict())).toList(),
                envelope.retrievedContext(), envelope.timelineContext(), envelope.toolResults())
                .flatMap(List::stream)
                .map(ContextItem::source)
                .filter(java.util.Objects::nonNull)
                .map(ContextSource::sourceId)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    private List<String> retrievalModes(ContextEnvelope envelope) {
        return java.util.stream.Stream.of(envelope.retrievedContext(), envelope.timelineContext())
                .flatMap(List::stream)
                .map(ContextItem::source)
                .filter(java.util.Objects::nonNull)
                .map(ContextSource::derivedFrom)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    /**
     * A stable, non-content snapshot identifier. Generated time is deliberately
     * excluded so identical planned sources produce the same audit version.
     */
    private String memorySnapshotVersion(ContextEnvelope envelope) {
        String sourceSnapshot = java.util.stream.Stream.of(envelope.coreMemory(), envelope.shortTermState(),
                envelope.userReferences().stream().map(reference -> new ContextItem(reference.content(), reference.source(),
                        reference.relevanceScore(), reference.priority(), reference.conflict())).toList(),
                envelope.retrievedContext(), envelope.timelineContext(), envelope.toolResults())
                .flatMap(List::stream)
                .map(ContextItem::source)
                .filter(java.util.Objects::nonNull)
                .map(source -> String.join("|", safe(source.sourceType()), safe(source.sourceId()),
                        source.eventTime() == null ? "" : source.eventTime().toString(), safe(source.derivedFrom())))
                .sorted()
                .collect(java.util.stream.Collectors.joining("\n"));
        return sha256(sourceSnapshot);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
