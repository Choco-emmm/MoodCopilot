package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.mapper.UserMapper;
import com.moodcopilot.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ChatController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChatControllerMockMvcTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ChatService chatService;

    @MockBean
    private MemoryExtractionService memoryExtractionService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UserMapper userMapper;

    @Test
    void chatReturnsStream() throws Exception {
        when(memoryExtractionService.buildUserMemoryPrompt()).thenReturn("长期记忆：重视稳定关系");
        when(chatService.chat(eq(7L), eq("hello"), eq(List.of("ref")), eq("长期记忆：重视稳定关系")))
                .thenReturn(Flux.just("hello"));

        mockMvc.perform(post("/api/chat/conversations/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "message", "hello",
                                "references", List.of("ref")
                        ))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hello")));
    }

    @Test
    void replyReturnsPlainApiResponseForMobileFallback() throws Exception {
        when(memoryExtractionService.buildUserMemoryPrompt()).thenReturn("长期记忆：长期目标是读研");
        when(chatService.reply(eq(7L), eq("hello"), eq(List.of("ref")), eq("长期记忆：长期目标是读研")))
                .thenReturn("hello");

        mockMvc.perform(post("/api/chat/conversations/7/reply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "message", "hello",
                                "references", List.of("ref")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("hello"));
    }
}
