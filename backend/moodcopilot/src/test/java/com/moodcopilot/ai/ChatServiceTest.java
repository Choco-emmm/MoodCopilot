package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.ChatConversationEntity;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.mapper.ChatConversationMapper;
import com.moodcopilot.mapper.DiaryAnalysisMapper;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.security.RateLimitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private ChatClient chatClient;
    @Mock(answer = Answers.RETURNS_SELF) private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock private ChatClient.CallResponseSpec callResponseSpec;
    @Mock private ChatClient.StreamResponseSpec streamResponseSpec;
    @Mock private DiaryMapper diaryMapper;
    @Mock private DiaryAnalysisMapper diaryAnalysisMapper;
    @Mock private ChatConversationMapper conversationMapper;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private RateLimitService rateLimitService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void replyRegistersDiarySearchFunction() {
        loginAs(1L);
        ChatService service = service();
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("根据上周的记录，你主要是因为工作压力不开心。");
        when(conversationMapper.selectById(7L)).thenReturn(conversation(7L, 1L));
        when(diaryMapper.selectList(any())).thenReturn(List.of());

        String result = service.reply(7L, "我上周为什么不开心？", List.of(), "长期记忆：最近压力偏大");

        assertThat(result).contains("工作压力");
        verify(requestSpec).functions("diarySearchFunction");
        verify(rateLimitService).tryAcquire(1L, RateLimitService.AiApiType.CHAT);
    }

    @Test
    void chatRegistersDiarySearchFunction() {
        loginAs(1L);
        ChatService service = service();
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content()).thenReturn(Flux.just("先查一下上周的日记。"));
        when(conversationMapper.selectById(9L)).thenReturn(conversation(9L, 1L));
        when(diaryMapper.selectList(any())).thenReturn(List.of());

        List<String> chunks = service.chat(9L, "帮我回顾上周", List.of("ref"), "长期记忆：最近容易焦虑")
                .collectList()
                .block();

        assertThat(chunks).containsExactly("先查一下上周的日记。");
        verify(requestSpec).functions("diarySearchFunction");
    }

    private ChatService service() {
        return new ChatService(
                chatClient,
                diaryMapper,
                diaryAnalysisMapper,
                conversationMapper,
                new ConcurrentHashMap<>(Map.of("1:7", mock(ChatMemory.class), "1:9", mock(ChatMemory.class))),
                redisTemplate,
                new ObjectMapper(),
                rateLimitService
        );
    }

    private void loginAs(long userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setDisplayName("测试用户");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null)
        );
    }

    private ChatConversationEntity conversation(long id, long userId) {
        ChatConversationEntity entity = new ChatConversationEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setTitle("新对话");
        return entity;
    }
}
