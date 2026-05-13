package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.UserProfileMemoryEntity;
import com.moodcopilot.mapper.UserProfileMemoryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryExtractionServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient analysisChatClient;

    @Mock
    private UserProfileMemoryMapper userProfileMemoryMapper;

    @Test
    void extractAndSyncMemoryUpsertsReturnedAttributes() {
        MemoryExtractionService memoryExtractionService = new MemoryExtractionService(
                analysisChatClient,
                userProfileMemoryMapper,
                new ObjectMapper()
        );
        UserProfileMemoryEntity existing = new UserProfileMemoryEntity();
        existing.setId(8L);
        existing.setUserId(12L);
        existing.setAttributeKey("长期目标");
        existing.setAttributeValue("准备转岗");
        existing.setUpdateTime(LocalDateTime.now().minusDays(3));
        when(userProfileMemoryMapper.selectList(any())).thenReturn(List.of(existing));
        when(analysisChatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("""
                        {"attributes":[
                          {"attributeKey":"长期目标","attributeValue":"一年内读研"},
                          {"attributeKey":"关键人物","attributeValue":"妈妈"}
                        ]}
                        """);

        memoryExtractionService.extractAndSyncMemory(12L, "最近我在认真准备考研，也更常和妈妈聊未来。");

        assertEquals("一年内读研", existing.getAttributeValue());
        verify(userProfileMemoryMapper).updateById(existing);
        verify(userProfileMemoryMapper).insert(any(UserProfileMemoryEntity.class));
    }

    @Test
    void buildUserMemoryPromptReturnsReadableBackground() {
        MemoryExtractionService memoryExtractionService = new MemoryExtractionService(
                analysisChatClient,
                userProfileMemoryMapper,
                new ObjectMapper()
        );
        com.moodcopilot.entity.UserEntity user = new com.moodcopilot.entity.UserEntity();
        user.setId(3L);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(user, null));
        UserProfileMemoryEntity personality = new UserProfileMemoryEntity();
        personality.setAttributeKey("性格");
        personality.setAttributeValue("做决定前会反复权衡");
        UserProfileMemoryEntity goal = new UserProfileMemoryEntity();
        goal.setAttributeKey("长期目标");
        goal.setAttributeValue("希望读研");
        when(userProfileMemoryMapper.selectList(any())).thenReturn(List.of(personality, goal));

        try {
            String prompt = memoryExtractionService.buildUserMemoryPrompt();

            assertTrue(prompt.contains("性格：做决定前会反复权衡"));
            assertTrue(prompt.contains("长期目标：希望读研"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
