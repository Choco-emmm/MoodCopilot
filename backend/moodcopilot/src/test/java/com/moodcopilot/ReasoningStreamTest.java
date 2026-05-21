package com.moodcopilot;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Flux;

@SpringBootTest(properties = {
    "spring.ai.openai.api-key=${MOODCOPILOT_VISION_API_KEY:sk-xxx}", // fallback if not set
    "spring.ai.openai.base-url=https://api.deepseek.com"
})
@ActiveProfiles("test")
public class ReasoningStreamTest {

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Test
    public void testReasoningModelStream() {
        System.out.println("====== STARTING REASONING STREAM TEST ======");
        try {
            ChatClient chatClient = chatClientBuilder.build();
            Flux<ChatResponse> stream = chatClient.prompt()
                    .user("你好，请简要分析一下天空为什么是蓝色的。")
                    .stream()
                    .chatResponse();
            
            stream.doOnNext(response -> {
                if (response.getResult() != null && response.getResult().getOutput() != null) {
                    System.out.print(response.getResult().getOutput().getText());
                }
            }).blockLast();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("\n====== END REASONING STREAM TEST ======");
    }
}
