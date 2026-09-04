package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.UserMemoryCandidateEntity;
import com.moodcopilot.entity.UserProfileMemoryEntity;
import com.moodcopilot.mapper.UserMemoryCandidateMapper;
import com.moodcopilot.mapper.UserMemoryEvidenceMapper;
import com.moodcopilot.mapper.UserMemoryRejectionMapper;
import com.moodcopilot.mapper.UserProfileMemoryMapper;
import com.moodcopilot.notification.NotificationService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MemoryOrchestratorTest {

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
                        "preference", "inferred", .99, "用户可能更喜欢安静交流", null, null)),
                "chat_candidate", null, 22L, "用户提到最近更喜欢安静交流", null);

        verify(candidateMapper).insert(any(UserMemoryCandidateEntity.class));
        verifyNoInteractions(notificationService);
    }

    @Test
    void explicitEvidenceMustBeGroundedInUserText() {
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

        verify(candidateMapper).insert(any(UserMemoryCandidateEntity.class));
        verify(memoryMapper, never()).insert(any(UserProfileMemoryEntity.class));
    }

    @Test
    void fabricatedExplicitExcerptCannotBecomeFormalAfterEvidenceFallback() {
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

        verify(candidateMapper).insert(any(UserMemoryCandidateEntity.class));
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
                        false, "preference", "inferred", .93, "用户表达希望加入QG工作室", null, null)),
                "diary_inferred", 2015L, null, "我爱吃面包", null);

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
}
