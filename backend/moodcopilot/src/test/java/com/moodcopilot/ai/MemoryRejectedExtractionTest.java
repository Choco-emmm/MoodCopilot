package com.moodcopilot.ai;

import com.moodcopilot.entity.DiaryEntity;
import com.moodcopilot.mapper.DiaryMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MemoryRejectedExtractionTest {
    @Test void aRejectionReasonNeverGrantsExplicitWritePermission() {
        var diaries = mock(DiaryMapper.class);
        var extractor = mock(MemoryExtractionService.class);
        var chat = mock(ChatService.class);
        var orchestrator = mock(MemoryOrchestrator.class);
        when(chat.loadUserEvidenceForMemory(7L, 32L)).thenReturn("我喜欢香蕉");
        var attributes = List.of(MemoryWriteContractTest.attribute("饮食偏好", "喜欢香蕉", "explicit", "我喜欢香蕉"));
        when(extractor.extractMemoryFromContext(anyString(), anyString(), anyLong(), anyLong(), anyString())).thenReturn(attributes);
        new MemoryCandidateRejectedEventListener(diaries, extractor, chat, orchestrator).onMemoryCandidateRejected(
                new MemoryCandidateRejectedEvent(this, 7L, 22L, null, 32L, "请修正", "喜欢苹果"));
        verify(orchestrator).processExtractedMemories(7L, attributes, "chat_candidate", null, 32L, "我喜欢香蕉", null);
    }

    @Test void foreignDiaryCannotBecomeExtractionEvidence() {
        var diaries = mock(DiaryMapper.class);
        var extractor = mock(MemoryExtractionService.class);
        var diary = new DiaryEntity(); diary.setId(99L); diary.setAuthorUserId(8L); diary.setContent("别人的日记");
        when(diaries.selectById(99L)).thenReturn(diary);
        new MemoryCandidateRejectedEventListener(diaries, extractor, mock(ChatService.class), mock(MemoryOrchestrator.class))
                .onMemoryCandidateRejected(new MemoryCandidateRejectedEvent(this, 7L, 22L, 99L, null, "请修正", "旧值"));
        verifyNoInteractions(extractor);
    }
}
