package com.moodcopilot.event;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.config.AiPromptProperties;
import com.moodcopilot.entity.UserLifeEventEntity;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.mapper.DiaryAnalysisMapper;
import com.moodcopilot.mapper.UserLifeEventMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class LifeEventServiceTest {

    @Test
    void updateStatusRejectsArchivedAndUnknownStates() {
        UserLifeEventMapper eventMapper = mock(UserLifeEventMapper.class);
        UserLifeEventEntity event = new UserLifeEventEntity();
        event.setId(11L);
        event.setUserId(7L);
        event.setStatus("PENDING");
        when(eventMapper.selectOne(any(Wrapper.class))).thenReturn(event);
        LifeEventService service = new LifeEventService(eventMapper, mock(DiaryMapper.class),
                mock(ChatClient.class), new ObjectMapper(), mock(AiPromptProperties.class));

        assertThrows(ResponseStatusException.class,
                () -> service.updateEventStatus(7L, 11L, "ARCHIVED", null));
        assertThrows(ResponseStatusException.class,
                () -> service.updateEventStatus(7L, 11L, "UNKNOWN", null));
    }

    @Test
    void rejectsInvalidScheduleAndAllowsManualEventWithoutDiaries() {
        UserLifeEventMapper eventMapper = mock(UserLifeEventMapper.class);
        LifeEventService service = new LifeEventService(eventMapper, mock(DiaryMapper.class),
                mock(DiaryAnalysisMapper.class), mock(ChatClient.class), new ObjectMapper(), mock(AiPromptProperties.class), null);

        assertThrows(ResponseStatusException.class, () -> service.createEvent(7L,
                new LifeEventService.LifeEventUpsertRequest("考试", "", "2026-09-03", "2026-09-02", null, null, List.of())));
        service.createEvent(7L, new LifeEventService.LifeEventUpsertRequest(
                "考试", "", "2026-09-03", null, "14:00", "16:00", List.of()));
        verify(eventMapper).insert(any(UserLifeEventEntity.class));
    }

    @Test
    void eventChatUsesSummaryBeforeDiaryExcerpt() {
        UserLifeEventMapper eventMapper = mock(UserLifeEventMapper.class);
        DiaryMapper diaryMapper = mock(DiaryMapper.class);
        DiaryAnalysisMapper analysisMapper = mock(DiaryAnalysisMapper.class);
        UserLifeEventEntity event = new UserLifeEventEntity();
        event.setId(12L);
        event.setUserId(7L);
        event.setTitle("复查");
        event.setTargetDate(java.time.LocalDate.of(2026, 9, 4));
        event.setDiaryIdsJson("[21]");
        when(eventMapper.selectOne(any(Wrapper.class))).thenReturn(event);
        com.moodcopilot.entity.DiaryEntity diary = new com.moodcopilot.entity.DiaryEntity();
        diary.setId(21L);
        diary.setAuthorUserId(7L);
        diary.setIsDeleted(false);
        diary.setContent("这是日记原文，应该只在没有摘要时作为回退内容");
        diary.setCreatedAt(LocalDateTime.of(2026, 9, 1, 10, 0));
        when(diaryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(diary));
        com.moodcopilot.entity.DiaryAnalysisEntity analysis = new com.moodcopilot.entity.DiaryAnalysisEntity();
        analysis.setDiaryId(21L);
        analysis.setSummary("AI 摘要：复查前有些紧张，但已经准备好了");
        when(analysisMapper.selectList(any(Wrapper.class))).thenReturn(List.of(analysis));

        LifeEventService service = new LifeEventService(eventMapper, diaryMapper, analysisMapper,
                mock(ChatClient.class), new ObjectMapper(), mock(AiPromptProperties.class), null);

        String context = service.buildEventContextForChat(7L, 12L);

        org.junit.jupiter.api.Assertions.assertTrue(context.contains("AI摘要：AI 摘要"));
        org.junit.jupiter.api.Assertions.assertFalse(context.contains("原文片段："));
    }
}
