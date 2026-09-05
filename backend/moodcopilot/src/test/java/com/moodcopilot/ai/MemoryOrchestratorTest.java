package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.UserMemoryCandidateEntity;
import com.moodcopilot.entity.UserMemoryEvidenceEntity;
import com.moodcopilot.entity.UserProfileMemoryEntity;
import com.moodcopilot.mapper.UserMemoryCandidateMapper;
import com.moodcopilot.mapper.UserMemoryEvidenceMapper;
import com.moodcopilot.mapper.UserMemoryRejectionMapper;
import com.moodcopilot.mapper.UserProfileMemoryMapper;
import com.moodcopilot.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

class MemoryOrchestratorTest {

    @BeforeAll
    static void initializeLambdaMetadataForMockedMapperPaths() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "memory-orchestrator-test");
        TableInfoHelper.initTableInfo(assistant, UserMemoryCandidateEntity.class);
        TableInfoHelper.initTableInfo(assistant, UserMemoryEvidenceEntity.class);
        TableInfoHelper.initTableInfo(assistant, UserProfileMemoryEntity.class);
    }

    @Test
    void rejectedFingerprintBlocksOnlyMatchingMemoryType() {
        UserProfileMemoryMapper memoryMapper = mock(UserProfileMemoryMapper.class);
        UserMemoryCandidateMapper candidateMapper = mock(UserMemoryCandidateMapper.class);
        UserMemoryEvidenceMapper evidenceMapper = mock(UserMemoryEvidenceMapper.class);
        UserMemoryRejectionMapper rejectionMapper = mock(UserMemoryRejectionMapper.class);
        when(rejectionMapper.selectCount(any())).thenReturn(1L);
        MemoryOrchestrator orchestrator = new MemoryOrchestrator(memoryMapper, candidateMapper, evidenceMapper,
                rejectionMapper, mock(RagMemoryService.class), new ObjectMapper());

        orchestrator.processExtractedMemories(7L,
                List.of(new MemoryExtractionService.MemoryAttribute("运动习惯", "喜欢跑步", false,
                        "habit", "inferred", .95, "用户曾明确提到跑步", null, null)),
                "diary_inferred", 12L, null, "日记证据", null);

        verify(candidateMapper, never()).insert(any(UserMemoryCandidateEntity.class));
    }

    @Test
    void inferredCandidateDoesNotNotifyUntilItIsFormalized() {
        UserProfileMemoryMapper memoryMapper = mock(UserProfileMemoryMapper.class);
        UserMemoryCandidateMapper candidateMapper = mock(UserMemoryCandidateMapper.class);
        UserMemoryEvidenceMapper evidenceMapper = mock(UserMemoryEvidenceMapper.class);
        UserMemoryRejectionMapper rejectionMapper = mock(UserMemoryRejectionMapper.class);
        NotificationService notificationService = mock(NotificationService.class);
        when(rejectionMapper.selectCount(any())).thenReturn(0L);
        when(candidateMapper.selectOne(any())).thenReturn(null);
        when(evidenceMapper.selectCount(any())).thenReturn(0L);
        when(evidenceMapper.selectList(any())).thenReturn(List.of());
        when(memoryMapper.selectList(any())).thenReturn(List.of());
        MemoryOrchestrator orchestrator = new MemoryOrchestrator(memoryMapper, candidateMapper, evidenceMapper,
                rejectionMapper, mock(RagMemoryService.class), new ObjectMapper(), notificationService);

        orchestrator.processExtractedMemories(7L,
                List.of(new MemoryExtractionService.MemoryAttribute("社交偏好", "偏好安静交流", false,
                        "preference", "inferred", .99, "最近更喜欢安静交流", null, null)),
                "chat_candidate", null, 22L, "用户提到最近更喜欢安静交流", null);

        verify(candidateMapper).insert(any(UserMemoryCandidateEntity.class));
        verifyNoInteractions(notificationService);
    }

    @Test
    void validEvidenceExcerptIsStoredInsteadOfTheWholeDiary() {
        UserProfileMemoryMapper memoryMapper = mock(UserProfileMemoryMapper.class);
        UserMemoryCandidateMapper candidateMapper = mock(UserMemoryCandidateMapper.class);
        UserMemoryEvidenceMapper evidenceMapper = mock(UserMemoryEvidenceMapper.class);
        UserMemoryRejectionMapper rejectionMapper = mock(UserMemoryRejectionMapper.class);
        when(rejectionMapper.selectCount(any())).thenReturn(0L);
        when(candidateMapper.selectList(any())).thenReturn(List.of());
        when(evidenceMapper.selectCount(any())).thenReturn(0L);
        when(evidenceMapper.selectList(any())).thenReturn(List.of());
        when(memoryMapper.selectList(any())).thenReturn(List.of());
        MemoryOrchestrator orchestrator = new MemoryOrchestrator(memoryMapper, candidateMapper, evidenceMapper,
                rejectionMapper, mock(RagMemoryService.class), new ObjectMapper());

        String diary = "今天有点累。最近更喜欢安静交流。还想吃苹果、香蕉、葡萄。";
        orchestrator.processExtractedMemories(7L,
                List.of(new MemoryExtractionService.MemoryAttribute("社交偏好", "偏好安静交流", false,
                        "preference", "inferred", .99, "最近更喜欢安静交流", null, null)),
                "diary_inferred", 2020L, null, diary, null);

        var evidenceCaptor = forClass(UserMemoryEvidenceEntity.class);
        verify(evidenceMapper).insert(evidenceCaptor.capture());
        assertEquals("最近更喜欢安静交流", evidenceCaptor.getValue().getEvidenceText());
    }

    @Test
    void unmatchedEvidenceDoesNotCreateCandidate() {
        UserProfileMemoryMapper memoryMapper = mock(UserProfileMemoryMapper.class);
        UserMemoryCandidateMapper candidateMapper = mock(UserMemoryCandidateMapper.class);
        UserMemoryEvidenceMapper evidenceMapper = mock(UserMemoryEvidenceMapper.class);
        UserMemoryRejectionMapper rejectionMapper = mock(UserMemoryRejectionMapper.class);
        when(rejectionMapper.selectCount(any())).thenReturn(0L);
        when(candidateMapper.selectOne(any())).thenReturn(null);
        when(evidenceMapper.selectCount(any())).thenReturn(0L);
        when(evidenceMapper.selectList(any())).thenReturn(List.of());
        when(memoryMapper.selectList(any())).thenReturn(List.of());
        MemoryOrchestrator orchestrator = new MemoryOrchestrator(memoryMapper, candidateMapper, evidenceMapper,
                rejectionMapper, mock(RagMemoryService.class), new ObjectMapper());

        orchestrator.processExtractedMemories(7L,
                List.of(new MemoryExtractionService.MemoryAttribute("社交偏好", "偏好独处", false,
                        "preference", "explicit", .99, "用户偏好独处", null, null)),
                "chat_candidate", null, 22L, "用户没有明确表达这个结论", null);

        verify(candidateMapper, never()).insert(any(UserMemoryCandidateEntity.class));
        verify(memoryMapper, never()).insert(any(UserProfileMemoryEntity.class));
    }

    @Test
    void fabricatedExcerptDoesNotFallbackToTheWholeDiary() {
        UserProfileMemoryMapper memoryMapper = mock(UserProfileMemoryMapper.class);
        UserMemoryCandidateMapper candidateMapper = mock(UserMemoryCandidateMapper.class);
        UserMemoryEvidenceMapper evidenceMapper = mock(UserMemoryEvidenceMapper.class);
        UserMemoryRejectionMapper rejectionMapper = mock(UserMemoryRejectionMapper.class);
        when(rejectionMapper.selectCount(any())).thenReturn(0L);
        when(candidateMapper.selectOne(any())).thenReturn(null);
        when(evidenceMapper.selectCount(any())).thenReturn(0L);
        when(evidenceMapper.selectList(any())).thenReturn(List.of());
        when(memoryMapper.selectList(any())).thenReturn(List.of());
        MemoryOrchestrator orchestrator = new MemoryOrchestrator(memoryMapper, candidateMapper, evidenceMapper,
                rejectionMapper, mock(RagMemoryService.class), new ObjectMapper());

        orchestrator.processExtractedMemories(7L,
                List.of(new MemoryExtractionService.MemoryAttribute("社交偏好", "偏好独处", false,
                        "preference", "explicit", .99, "模型编造的证据", null, null)),
                "diary_inferred", 12L, null, "我喜欢安静的地方", null);

        verify(candidateMapper, never()).insert(any(UserMemoryCandidateEntity.class));
        verify(memoryMapper, never()).insert(any(UserProfileMemoryEntity.class));
    }

    @Test
    void oneExplicitTechnicalSelfDeclarationRemainsACandidateUntilIndependentEvidenceAccumulates() {
        UserProfileMemoryMapper memoryMapper = mock(UserProfileMemoryMapper.class);
        UserMemoryCandidateMapper candidateMapper = mock(UserMemoryCandidateMapper.class);
        UserMemoryEvidenceMapper evidenceMapper = mock(UserMemoryEvidenceMapper.class);
        UserMemoryRejectionMapper rejectionMapper = mock(UserMemoryRejectionMapper.class);
        when(rejectionMapper.selectCount(any())).thenReturn(0L);
        when(candidateMapper.selectList(any())).thenReturn(List.of());
        when(evidenceMapper.selectCount(any())).thenReturn(0L);
        when(evidenceMapper.selectList(any())).thenReturn(List.of());
        when(memoryMapper.selectList(any())).thenReturn(List.of());
        MemoryOrchestrator orchestrator = new MemoryOrchestrator(memoryMapper, candidateMapper, evidenceMapper,
                rejectionMapper, mock(RagMemoryService.class), new ObjectMapper());

        orchestrator.processExtractedMemories(7L,
                List.of(new MemoryExtractionService.MemoryAttribute("技术背景", "我是 Java 后端开发工程师", false,
                        "preference", "explicit", .99, "我是 Java 后端开发工程师", null, null)),
                "chat_candidate", null, 22L, "我是 Java 后端开发工程师", null);

        verify(candidateMapper).insert(any(UserMemoryCandidateEntity.class));
        verify(memoryMapper, never()).insert(any(UserProfileMemoryEntity.class));
    }

    @Test
    void promotionClaimsPendingCandidateBeforeCreatingFormalMemory() {
        UserProfileMemoryMapper memoryMapper = mock(UserProfileMemoryMapper.class);
        UserMemoryCandidateMapper candidateMapper = mock(UserMemoryCandidateMapper.class);
        UserMemoryEvidenceMapper evidenceMapper = mock(UserMemoryEvidenceMapper.class);
        UserMemoryRejectionMapper rejectionMapper = mock(UserMemoryRejectionMapper.class);
        UserMemoryCandidateEntity candidate = candidate(8L, "PENDING", .95, "喜欢通过写日记整理思绪");
        UserMemoryEvidenceEntity first = evidence(candidate.getId(), java.time.LocalDate.of(2026, 9, 1));
        UserMemoryEvidenceEntity second = evidence(candidate.getId(), java.time.LocalDate.of(2026, 9, 2));
        when(rejectionMapper.selectCount(any())).thenReturn(0L);
        // First lookup finds the pending branch; subsequent approved-branch
        // lookups intentionally return no winner.
        when(candidateMapper.selectList(any())).thenReturn(List.of(candidate), List.of(), List.of());
        when(candidateMapper.update(any(), any())).thenReturn(1);
        when(evidenceMapper.selectCount(any())).thenReturn(0L);
        when(evidenceMapper.selectList(any())).thenReturn(List.of(first, second));
        when(memoryMapper.selectOne(any())).thenReturn(null);
        when(memoryMapper.selectList(any())).thenReturn(List.of());
        MemoryOrchestrator orchestrator = new MemoryOrchestrator(memoryMapper, candidateMapper, evidenceMapper,
                rejectionMapper, mock(RagMemoryService.class), new ObjectMapper());

        orchestrator.processExtractedMemories(1006L,
                List.of(new MemoryExtractionService.MemoryAttribute("思考方式", "喜欢通过写日记整理思绪", false,
                        "preference", "inferred", .99, "我喜欢通过写日记整理思绪", null, null)),
                "diary_inferred", 2015L, null, "我喜欢通过写日记整理思绪", java.time.LocalDate.of(2026, 9, 2));

        var ordered = inOrder(candidateMapper, memoryMapper);
        ordered.verify(candidateMapper).update(any(), any());
        ordered.verify(memoryMapper).insert(any(UserProfileMemoryEntity.class));
    }

    @Test
    void stalePromotionClaimCannotCreateASecondFormalMemory() {
        UserProfileMemoryMapper memoryMapper = mock(UserProfileMemoryMapper.class);
        UserMemoryCandidateMapper candidateMapper = mock(UserMemoryCandidateMapper.class);
        UserMemoryEvidenceMapper evidenceMapper = mock(UserMemoryEvidenceMapper.class);
        UserMemoryRejectionMapper rejectionMapper = mock(UserMemoryRejectionMapper.class);
        UserMemoryCandidateEntity candidate = candidate(9L, "PENDING", .95, "喜欢通过写日记整理思绪");
        when(rejectionMapper.selectCount(any())).thenReturn(0L);
        when(candidateMapper.selectList(any())).thenReturn(List.of(candidate), List.of(), List.of());
        when(candidateMapper.update(any(), any())).thenReturn(0);
        when(evidenceMapper.selectCount(any())).thenReturn(0L);
        when(evidenceMapper.selectList(any())).thenReturn(List.of(
                evidence(candidate.getId(), java.time.LocalDate.of(2026, 9, 1)),
                evidence(candidate.getId(), java.time.LocalDate.of(2026, 9, 2))));
        when(memoryMapper.selectList(any())).thenReturn(List.of());
        MemoryOrchestrator orchestrator = new MemoryOrchestrator(memoryMapper, candidateMapper, evidenceMapper,
                rejectionMapper, mock(RagMemoryService.class), new ObjectMapper());

        orchestrator.processExtractedMemories(1006L,
                List.of(new MemoryExtractionService.MemoryAttribute("思考方式", "喜欢通过写日记整理思绪", false,
                        "preference", "inferred", .99, "我喜欢通过写日记整理思绪", null, null)),
                "diary_inferred", 2015L, null, "我喜欢通过写日记整理思绪", java.time.LocalDate.of(2026, 9, 2));

        verify(memoryMapper, never()).insert(any(UserProfileMemoryEntity.class));
    }

    @Test
    void equivalentApprovedCandidateAbsorbsPendingCandidateWithoutDuplicatePromotion() {
        UserProfileMemoryMapper memoryMapper = mock(UserProfileMemoryMapper.class);
        UserMemoryCandidateMapper candidateMapper = mock(UserMemoryCandidateMapper.class);
        UserMemoryEvidenceMapper evidenceMapper = mock(UserMemoryEvidenceMapper.class);
        UserMemoryRejectionMapper rejectionMapper = mock(UserMemoryRejectionMapper.class);
        NotificationService notificationService = mock(NotificationService.class);

        UserMemoryCandidateEntity approved = candidate(1L, "APPROVED", .92,
                "对QG工作室表现出强烈向往，希望加入其中");
        UserMemoryCandidateEntity pending = candidate(3L, "PENDING", .90,
                "对qg工作室表现出强烈向往，希望加入其中");
        when(rejectionMapper.selectCount(any())).thenReturn(0L);
        when(candidateMapper.selectList(any())).thenReturn(List.of(approved, pending));
        when(evidenceMapper.selectList(any())).thenReturn(List.of());
        when(evidenceMapper.selectCount(any())).thenReturn(0L);
        when(memoryMapper.selectList(any())).thenReturn(List.of());

        MemoryOrchestrator orchestrator = new MemoryOrchestrator(memoryMapper, candidateMapper, evidenceMapper,
                rejectionMapper, mock(RagMemoryService.class), new ObjectMapper(), notificationService);

        orchestrator.processExtractedMemories(1006L,
                List.of(new MemoryExtractionService.MemoryAttribute("兴趣意向", "对qg工作室表现出强烈向往，希望加入其中",
                        false, "preference", "inferred", .93, "对qg工作室表现出强烈向往，希望加入其中", null, null)),
                "diary_inferred", 2015L, null, "对qg工作室表现出强烈向往，希望加入其中", null);

        assertEquals("MERGED", pending.getStatus());
        assertEquals(1L, pending.getMergedIntoId());
        verify(candidateMapper, never()).insert(any(UserMemoryCandidateEntity.class));
        verifyNoInteractions(notificationService);
    }

    private UserMemoryCandidateEntity candidate(Long id, String status, double confidence, String value) {
        UserMemoryCandidateEntity candidate = new UserMemoryCandidateEntity();
        candidate.setId(id);
        candidate.setUserId(1006L);
        candidate.setAttributeKey("兴趣意向");
        candidate.setNormalizedValue(value.toLowerCase());
        candidate.setAttributeValue(value);
        candidate.setMemoryType("preference");
        candidate.setSourceType("diary_inferred");
        candidate.setConfidence(confidence);
        candidate.setIsCore(false);
        candidate.setStatus(status);
        return candidate;
    }

    private UserMemoryEvidenceEntity evidence(Long candidateId, java.time.LocalDate date) {
        UserMemoryEvidenceEntity entity = new UserMemoryEvidenceEntity();
        entity.setCandidateId(candidateId);
        entity.setEvidenceDate(date);
        entity.setEvidenceQuality(1D);
        entity.setModelConfidence(.99D);
        entity.setSourceType("diary_inferred");
        entity.setEvidenceText("用户独立表达的证据");
        return entity;
    }
}
