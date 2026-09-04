package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Best-effort context provenance record. It never blocks the model request. */
@Service
public class ContextMetadataRecorder {
    private static final Logger log = LoggerFactory.getLogger(ContextMetadataRecorder.class);
    private static final Duration TTL = Duration.ofDays(30);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public ContextMetadataRecorder(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Async("aiExecutor")
    public void record(ContextEnvelope envelope) {
        if (envelope == null || redis == null) return;
        try {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("contextId", envelope.contextId());
            metadata.put("conversationId", envelope.conversationId());
            metadata.put("userId", envelope.userId());
            metadata.put("contextPurpose", envelope.contextPurpose().name());
            metadata.put("generatedAt", envelope.generatedAt().toString());
            metadata.put("plannerVersion", envelope.plannerVersion());
            metadata.put("sourceIds", sourceIds(envelope));
            metadata.put("retrievalModes", retrievalModes(envelope));
            metadata.put("retrievalPolicyVersion", "1");
            metadata.put("promptTemplateVersion", "conversation-context-v2");
            metadata.put("memorySnapshotVersion", envelope.generatedAt().toEpochMilli());
            String key = "rag:context:" + envelope.userId() + ":" + envelope.contextId();
            redis.opsForValue().set(key, objectMapper.writeValueAsString(metadata), TTL);
        } catch (Exception e) {
            log.debug("上下文元数据保存失败 contextId={} userId={} errorType={}",
                    envelope.contextId(), envelope.userId(), e.getClass().getSimpleName());
        }
    }

    private List<String> sourceIds(ContextEnvelope envelope) {
        return java.util.stream.Stream.of(envelope.coreMemory(), envelope.shortTermState(),
                        envelope.userReferences(), envelope.retrievedContext(), envelope.timelineContext(), envelope.toolResults())
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
}
