package com.moodcopilot;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import java.util.function.Function;

@SpringBootTest
@ActiveProfiles("test")
public class ReasoningTest {

    @Configuration
    static class TestConfig {
        @Bean
        public Function<MockRequest, String> mockWeatherFunction() {
            return request -> "Hangzhou is Cloudy 7~13°C today.";
        }
    }

    public record MockRequest(String location, String date) {}

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Test
    public void testReasoningModelWithTool() {
        System.out.println("====== STARTING REASONING TOOL TEST ======");
        try {
            ChatClient chatClient = chatClientBuilder.build();
            ChatResponse response = chatClient.prompt()
                    .user("What's the weather like in Hangzhou today? Let me know.")
                    .options(OpenAiChatOptions.builder().model("deepseek-reasoner").build())
                    .functions("mockWeatherFunction")
                    .call()
                    .chatResponse();
            
            System.out.println("Response: " + response.getResult().getOutput().getText());
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("====== END REASONING TOOL TEST ======");
    }
}
