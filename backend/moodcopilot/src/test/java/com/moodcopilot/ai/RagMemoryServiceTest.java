package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.UserProfileMemoryEntity;
import com.moodcopilot.mapper.DiaryMapper;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RagMemoryServiceTest {

    @Test
    void sameProfileFingerprintSkipsEmbeddingAndChangedContentReindexes() {
        RagMemoryService service = new RagMemoryService(
                "http://embedding.test", "test-key", "test-model", 3,
                mock(org.springframework.data.redis.core.StringRedisTemplate.class),
                new ObjectMapper(), mock(DiaryMapper.class), "Asia/Shanghai");
        UserProfileMemoryEntity memory = memory(17L, "社交偏好", "偏好安静交流");

        String fingerprint = service.profileFingerprint(memory);

        assertFalse(RagMemoryService.needsProfileReindex(fingerprint, fingerprint));
        assertTrue(RagMemoryService.needsProfileReindex(fingerprint, null));

        memory.setAttributeValue("偏好低频交流");
        assertNotEquals(fingerprint, service.profileFingerprint(memory));
        assertTrue(RagMemoryService.needsProfileReindex(service.profileFingerprint(memory), fingerprint));
    }

    @Test
    void profileFingerprintIncludesMemoryIdAndValidity() {
        RagMemoryService service = new RagMemoryService(
                "http://embedding.test", "test-key", "test-model", 3,
                mock(org.springframework.data.redis.core.StringRedisTemplate.class),
                new ObjectMapper(), mock(DiaryMapper.class), "Asia/Shanghai");
        UserProfileMemoryEntity first = memory(17L, "兴趣", "喜欢爵士");
        UserProfileMemoryEntity second = memory(18L, "兴趣", "喜欢爵士");

        assertNotEquals(service.profileFingerprint(first), service.profileFingerprint(second));
        second.setValidUntil(LocalDate.of(2026, 12, 31));
        assertNotEquals(service.profileFingerprint(first), service.profileFingerprint(second));
    }

    @Test
    void profileKeysAreIndependentPerMemoryId() {
        RagMemoryService service = new RagMemoryService(
                "http://embedding.test", "test-key", "test-model", 3,
                mock(org.springframework.data.redis.core.StringRedisTemplate.class),
                new ObjectMapper(), mock(DiaryMapper.class), "Asia/Shanghai");

        assertEquals("rag:profile:1006:17", service.profileKey(1006L, 17L));
        assertEquals("rag:profile:1006:18", service.profileKey(1006L, 18L));
        assertNotEquals(service.profileKey(1006L, 17L), service.profileKey(1006L, 18L));
    }

    @Test
    void rabbitProfileIndexEntryPointWaitsForCompletion() throws NoSuchMethodException {
        var syncMethod = RagMemoryService.class.getMethod("indexUserProfile", long.class, List.class);
        var asyncMethod = RagMemoryService.class.getMethod("indexUserProfileAsync", long.class, List.class);

        assertNull(syncMethod.getAnnotation(Async.class));
        assertTrue(asyncMethod.isAnnotationPresent(Async.class));
    }

    private UserProfileMemoryEntity memory(Long id, String key, String value) {
        UserProfileMemoryEntity memory = new UserProfileMemoryEntity();
        memory.setId(id);
        memory.setUserId(1006L);
        memory.setAttributeKey(key);
        memory.setAttributeValue(value);
        memory.setMemoryType("preference");
        memory.setIsCore(false);
        memory.setStatus("active");
        memory.setUpdatedAt(LocalDateTime.of(2026, 9, 4, 12, 30));
        return memory;
    }
}
