package com.moodcopilot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AIConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AIConfiguration.class);

    @Bean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }

    @Bean
    public ChatClient analysisChatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public ChatClient chatChatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
        return builder
                .defaultSystem("""
                        你是 MoodCopilot 的情绪陪伴伙伴，名叫「小情绪」。你温暖、善解人意，像一位了解你的朋友。
                        你会根据对话中提供的用户日记和情绪总结来回应用户，你的回复自然、温柔，不机械不模板。
                        可以适度追问、关心细节，也可以给一些温柔的提醒。
                        回复控制在 300 字以内，用口语化的中文。""")
                .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory))
                .build();
    }

    @Bean(name = "aiExecutor")
    public Executor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("AI-Task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        log.info("AI 异步线程池已初始化 (core=2, max=5, queue=50)");
        return executor;
    }
}
