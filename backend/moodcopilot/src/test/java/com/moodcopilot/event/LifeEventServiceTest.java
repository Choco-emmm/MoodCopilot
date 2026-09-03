package com.moodcopilot.event;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.config.AiPromptProperties;
import com.moodcopilot.entity.UserLifeEventEntity;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.mapper.UserLifeEventMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
}
