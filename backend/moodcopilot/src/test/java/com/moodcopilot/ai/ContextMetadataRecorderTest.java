package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContextMetadataRecorderTest {

    @Test
    @SuppressWarnings("unchecked")
    void recordsStableSourceSnapshotWithoutPersistingContextContent() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        ObjectMapper mapper = new ObjectMapper();
        ContextMetadataRecorder recorder = new ContextMetadataRecorder(redis, mapper);
        ContextSource source = new ContextSource("FORMAL_MEMORY", "42", "user", "structured_memory",
                Instant.parse("2026-09-05T00:00:00Z"), null, ContextSource.TrustLevel.AUTHORITATIVE, 7L);
        ContextItem item = new ContextItem("绝不应保存进审计记录的私密内容", source, 1D, 50, false);
        ContextEnvelope first = new ContextEnvelope("ctx-1", 11L, 7L, ContextPurpose.CHAT,
                Instant.now().truncatedTo(ChronoUnit.SECONDS), "2", List.of(item), List.of(), List.of(),
                List.of(), List.of(), List.of());
        ContextEnvelope second = new ContextEnvelope("ctx-2", 11L, 7L, ContextPurpose.CHAT,
                Instant.now().plusSeconds(10).truncatedTo(ChronoUnit.SECONDS), "2", List.of(item), List.of(), List.of(),
                List.of(), List.of(), List.of());

        recorder.record(first);
        recorder.record(second);

        var payload = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(values, times(2)).set(anyString(), payload.capture(), any());
        List<String> writes = payload.getAllValues();
        Map<String, Object> firstMetadata = mapper.readValue(writes.get(0), Map.class);
        Map<String, Object> secondMetadata = mapper.readValue(writes.get(1), Map.class);
        assertEquals(firstMetadata.get("memorySnapshotVersion"), secondMetadata.get("memorySnapshotVersion"));
        assertFalse(writes.get(0).contains("绝不应保存进审计记录的私密内容"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordsRequestedAndActualModelWithoutPromptContent() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        ObjectMapper mapper = new ObjectMapper();
        ContextMetadataRecorder recorder = new ContextMetadataRecorder(redis, mapper);
        EffectivePersona persona = new EffectivePersona("coding_partner", List.of("direct"), List.of("CODE_FIRST"),
                List.of(), 3, 2, true, "persona-hash");
        ContextEnvelope envelope = new ContextEnvelope("ctx-model", 11L, 7L, ContextPurpose.CHAT,
                Instant.now(), "2", List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        recorder.record(envelope, Map.of(
                "personaVersion", persona.globalVersion(),
                "conversationPersonaVersion", persona.conversationVersion(),
                "effectivePersonaHash", persona.effectivePersonaHash(),
                "taskType", "CODING",
                "requestedModel", "PRO",
                "actualModel", "PRO",
                "useReasoning", true));

        var payload = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(values).set(anyString(), payload.capture(), any());
        Map<String, Object> metadata = mapper.readValue(payload.getValue(), Map.class);
        assertEquals("PRO", metadata.get("requestedModel"));
        assertEquals("PRO", metadata.get("actualModel"));
        assertEquals("CODING", metadata.get("taskType"));
        assertEquals("persona-hash", metadata.get("effectivePersonaHash"));
    }

    @Test
    void metadataWriteFailureDoesNotEscapeToTheModelCaller() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new IllegalStateException("redis unavailable"));
        ContextMetadataRecorder recorder = new ContextMetadataRecorder(redis, new ObjectMapper());
        ContextEnvelope envelope = new ContextEnvelope("ctx-failure", null, 7L, ContextPurpose.CHAT,
                Instant.now(), "2", List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        assertDoesNotThrow(() -> recorder.record(envelope));
    }
}
