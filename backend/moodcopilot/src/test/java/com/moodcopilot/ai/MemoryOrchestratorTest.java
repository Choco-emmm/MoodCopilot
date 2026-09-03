package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.UserMemoryCandidateEntity;
import com.moodcopilot.mapper.UserMemoryCandidateMapper;
import com.moodcopilot.mapper.UserMemoryEvidenceMapper;
import com.moodcopilot.mapper.UserMemoryRejectionMapper;
import com.moodcopilot.mapper.UserProfileMemoryMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
}
