package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.UserProfileMemoryEntity;
import com.moodcopilot.mapper.DiaryMapper;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

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

    @Test
    void structuredSearchResultKeepsVectorEmptyDistinctFromEmptyQuery() {
        assertEquals(RagSearchResult.Mode.VECTOR,
                RagSearchResult.vector(List.of()).mode());
        assertEquals(RagSearchResult.Mode.EMPTY,
                RagSearchResult.empty().mode());
        assertEquals(RagSearchResult.Mode.LEXICAL_FALLBACK,
                RagSearchResult.lexicalFallback(List.of()).mode());
    }

    @Test
    void unexpectedEmbeddingFailureFallsBackToUserScopedLexicalSearch() {
        RagMemoryService service = spy(new RagMemoryService(
                "http://embedding.test", "test-key", "test-model", 3,
                mock(org.springframework.data.redis.core.StringRedisTemplate.class),
                new ObjectMapper(), mock(DiaryMapper.class), "Asia/Shanghai"));
        doThrow(new IllegalStateException("embedding client unavailable"))
                .when(service).embed("用户最近的学习计划");

        RagSearchResult result = service.searchDetailed(1006L, "用户最近的学习计划", 5, null,
                RagMemoryService.SOURCE_DIARY);

        assertEquals(RagSearchResult.Mode.LEXICAL_FALLBACK, result.mode());
    }

    @Test
    void graphHitWithDiaryIdDoesNotRequireDiaryRowToRender() {
        DiaryMapper diaryMapper = mock(DiaryMapper.class);
        RagMemoryService service = new RagMemoryService(
                "http://embedding.test", "test-key", "test-model", 3,
                mock(org.springframework.data.redis.core.StringRedisTemplate.class),
                new ObjectMapper(), diaryMapper, "Asia/Shanghai");

        List<ContextItem> items = service.retrieveContextItemsFromHits(1006L,
                List.of(new RagMemoryService.RagHit("用户 偏好 独处", 0.2D, "graph:9", 2015L,
                        RagMemoryService.SOURCE_GRAPH)), RagSearchResult.Mode.LEXICAL_FALLBACK);

        assertEquals(1, items.size());
        assertEquals("SYSTEM_GRAPH_DERIVATION", items.get(0).source().sourceType());
        assertEquals("graph:9", items.get(0).source().sourceId());
    }

    @Test
    void parsesResp2AndResp3SearchDocumentsWithoutTreatingAttributesAsHitCount() {
        RagMemoryService service = new RagMemoryService(
                "http://embedding.test", "test-key", "test-model", 3,
                mock(org.springframework.data.redis.core.StringRedisTemplate.class),
                new ObjectMapper(), mock(DiaryMapper.class), "Asia/Shanghai");
        List<RagMemoryService.RagHit> resp2 = new ArrayList<>();
        service.parseResults(List.of(1, "rag:diary:2014", List.of(
                "content", "RESP2 正文", "_score", "0.2")), resp2);
        assertEquals(1, resp2.size());
        assertEquals(2014L, resp2.get(0).diaryId());

        List<RagMemoryService.RagHit> resp3 = new ArrayList<>();
        service.parseResults(List.of(Map.of("id", "rag:diary:2015",
                "extra_attributes", Map.of("content", "RESP3 正文", "_score", "0.3"))), resp3);
        assertEquals(1, resp3.size());
        assertEquals("RESP3 正文", resp3.get(0).content());
        assertEquals(0.3D, resp3.get(0).score());
    }

    @Test
    void cosineDistanceKeepsExactAndNearMatchesAndDropsOnlyDistantResults() {
        List<RagMemoryService.RagHit> qualityHits = RagMemoryService.filterQualityHits(List.of(
                new RagMemoryService.RagHit("远距离", 0.56D, "diary:1", 1L, RagMemoryService.SOURCE_DIARY),
                new RagMemoryService.RagHit("近距离", 0.009D, "diary:2", 2L, RagMemoryService.SOURCE_DIARY),
                new RagMemoryService.RagHit("完全相同", 0.0D, "diary:3", 3L, RagMemoryService.SOURCE_DIARY),
                new RagMemoryService.RagHit("无距离", null, "diary:4", 4L, RagMemoryService.SOURCE_DIARY)));

        assertEquals(List.of("完全相同", "近距离"), qualityHits.stream()
                .map(RagMemoryService.RagHit::content)
                .toList());
    }

    @Test
    void cosineDistanceUsesRelativeCutoffForNoise() {
        List<RagMemoryService.RagHit> qualityHits = RagMemoryService.filterQualityHits(List.of(
                new RagMemoryService.RagHit("最佳", 0.1D, "diary:1", 1L, RagMemoryService.SOURCE_DIARY),
                new RagMemoryService.RagHit("相关", 0.2D, "diary:2", 2L, RagMemoryService.SOURCE_DIARY),
                new RagMemoryService.RagHit("噪音", 0.45D, "diary:3", 3L, RagMemoryService.SOURCE_DIARY)));

        assertEquals(List.of("最佳", "相关"), qualityHits.stream()
                .map(RagMemoryService.RagHit::content)
                .toList());
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
