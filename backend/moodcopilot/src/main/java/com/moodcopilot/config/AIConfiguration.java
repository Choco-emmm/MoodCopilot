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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AIConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AIConfiguration.class);

    @Bean
    public Map<Long, ChatMemory> userChatMemories() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    public ChatClient analysisChatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public ChatClient chatChatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                        你是 MoodCopilot 的情绪陪伴伙伴，名叫「小情绪」。你温暖、善解人意，像一位了解你的朋友。
                        在对话的上下文中，会提供用户最近的日记（包含日期和内容）。请自然地引用它们。
                        例如：「根据你 5/9 的日记...」或「你前几天提到...」。
                        回复控制在 300 字以内，用口语化的中文。
                        重要：不要使用任何 emoji 表情符号。用自然文字表达情感。
                        你可以使用简单的 Markdown 格式让回复更清晰，比如 **加粗**、- 列表项、换行分段。""")
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
