package com.moodcopilot.config;

import com.moodcopilot.ai.DiarySearchFunctionSupport;
import com.moodcopilot.ai.UserStatsFunctionSupport;
import com.moodcopilot.diary.DiarySearchRequest;
import com.moodcopilot.diary.DiaryService;
import com.moodcopilot.diary.UserStatsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AIConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AIConfiguration.class);

    @Bean
    public Map<String, ChatMemory> userChatMemories() {
        // 以 userId:conversationId 作为 key 的会话记忆容器。
        // 这样同一个用户的不同会话不会互相串话，也方便删除会话时精准清理。
        return new ConcurrentHashMap<>();
    }

    @Bean
    public ChatClient analysisChatClient(ChatClient.Builder builder) {
        // 分析模型客户端：专门用于日记分析、周/月报总结、长期画像提取。
        // 这里保持“分析”和“聊天”两套客户端的概念分离，便于后续切模型。
        return builder.build();
    }

    @Bean
    public ChatClient chatChatClient(ChatClient.Builder builder) {
        log.info("初始化聊天模型客户端：用于普通聊天、带记忆和函数调用的主链路");
        return builder
                .defaultSystem("""
                        你是 MoodCopilot。你温暖、善解人意，像一位了解用户近况的朋友。
                        在对话的上下文中，会提供用户最近的日记（包含日期和内容）。请自然地引用它们。
                        例如：「根据你 5/9 的日记...」或「你前几天提到...」。
                        当用户追问“上周/上个月/之前/以前为什么会怎样”、或需要翻阅更早的历史时，优先调用 diarySearchFunction 查询历史日记，再基于查询结果回答。
                        如果需要历史依据，不要假装记得没有查到的内容。
                        每次回复控制在2-3句话以内，像朋友发消息一样简短温暖。不要写大段分析或建议，除非用户明确要求。
                        重要限制：
                        1. 不要使用任何 emoji 表情符号。
                        2. 保持成熟、稳定、克制的语气。绝对禁止进行戏剧化的角色扮演，严禁在回复中使用括号描述动作（例如禁止出现「(打哈欠)」、「(伸懒腰)」等）。
                        3. 避免过度轻浮或戏谑的口语（如「噢噢什么噢噢」）。
                        你可以使用简单的 Markdown 格式让回复更清晰，比如 **加粗**、- 列表项、换行分段。""")
                .build();
    }

    @Bean(name = DiarySearchFunctionSupport.NAME)
    public FunctionCallback diarySearchFunction(@Lazy DiaryService diaryService) {
        log.info("注册 Function Calling 工具：{}", DiarySearchFunctionSupport.NAME);
        return FunctionCallback.builder()
                .function(DiarySearchFunctionSupport.NAME, diaryService::searchOwnDiarySummaries)
                .description(
                        "检索当前登录用户自己的历史日记摘要。keyword、startDate、endDate 都可选，日期格式为 YYYY-MM-DD。返回日期和内容片段，适合回答“上周为什么不开心”之类的历史问题。")
                .inputType(DiarySearchRequest.class)
                .build();
    }

    @Bean(name = UserStatsFunctionSupport.NAME)
    public FunctionCallback userStatsFunction(@Lazy DiaryService diaryService) {
        log.info("注册 Function Calling 工具：{}", UserStatsFunctionSupport.NAME);
        return FunctionCallback.builder()
                .function(UserStatsFunctionSupport.NAME, diaryService::getOwnMoodStats)
                .description("统计当前登录用户最近 N 天（默认 14 天）的日记与情绪分布，返回总日记数、情绪计数和高频主题。适合回答“我最近总是什么心情”这类问题。")
                .inputType(UserStatsRequest.class)
                .build();
    }

    @Bean(name = "aiExecutor")
    public Executor aiExecutor() {
        // AI 相关异步任务统一走单独线程池，避免阻塞 Web 请求线程。
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("AI-Task-");
        executor.setTaskDecorator(task -> () -> {
            long startedAt = System.currentTimeMillis();
            try {
                task.run();
            } finally {
                long durationMs = System.currentTimeMillis() - startedAt;
                if (durationMs > 5000) {
                    log.warn("AI 异步任务耗时较长，durationMs={}，thread={}", durationMs, Thread.currentThread().getName());
                }
            }
        });
        executor.setRejectedExecutionHandler((task, pool) -> {
            log.warn("AI 异步任务队列拥塞，降级为调用线程执行，active={}，poolSize={}，queueSize={}",
                    pool.getActiveCount(), pool.getPoolSize(), pool.getQueue().size());
            if (!pool.isShutdown()) {
                task.run();
            }
        });
        executor.initialize();
        log.info("AI 异步线程池已初始化 (core=2, max=5, queue=50)");
        return executor;
    }
}
