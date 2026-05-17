package com.moodcopilot.config;

import com.moodcopilot.ai.DiarySearchFunctionSupport;
import com.moodcopilot.ai.UserStatsFunctionSupport;
import com.moodcopilot.diary.DiarySearchRequest;
import com.moodcopilot.diary.DiaryService;
import com.moodcopilot.diary.UserStatsRequest;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.reactive.function.client.ClientRequest;
import reactor.core.publisher.Mono;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class AIConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AIConfiguration.class);

    @Bean
    public Cache<String, ChatMemory> userChatMemories() {
        // 以 userId:conversationId 作为 key 的会话记忆容器。
        // 30 分钟无访问自动过期 + 最多 500 条，防止 2C4G 服务器 OOM。
        return Caffeine.newBuilder()
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .maximumSize(500)
                .build();
    }

    @Bean
    public ChatClient analysisChatClient(ChatClient.Builder builder) {
        // 分析模型客户端：专门用于日记分析、周/月报总结、长期画像提取。
        // 这里保持"分析"和"聊天"两套客户端的概念分离，便于后续切模型。
        return builder.build();
    }

    @Bean
    public ChatClient chatChatClient(ChatClient.Builder builder) {
        log.info("初始化聊天模型客户端：用于普通聊天、带记忆和函数调用的主链路");
        return builder
                .defaultSystem("""
                        你是 MoodCopilot。你温暖、善解人意，像一位了解用户近况的朋友。

                        你拥有以下工具来查询用户的历史数据：
                        - diarySearchFunction：按关键词或日期范围检索用户的日记摘要
                        - userStatsFunction：统计用户最近 N 天的日记数量与情绪分布

                        关键行为准则：
                        当用户提到"最近"、"之前"、"上周"、"上个月"、"以前"、或者你需要核对用户的历史状态时，必须主动调用工具查询事实，不要盲目猜测。
                        如果用户的问题涉及过往经历或情绪变化，先查询再回答，不要假装记得没有查到的内容。
                        查到日记后，自然引用日期和内容，例如：「根据你 5/9 的日记...」或「你前几天提到...」。

                        每次回复控制在2-3句话以内，像朋友发消息一样简短温暖。不要写大段分析或建议，除非用户明确要求。

                        重要限制：
                        1. 可以适度使用 emoji 表情符号来增强温暖感，但不要过度堆砌，每条消息控制在 1-2 个以内。
                        2. 保持成熟、稳定、克制的语气。绝对禁止进行戏剧化的角色扮演，严禁在回复中使用括号描述动作（例如禁止出现「(打哈欠)」、「(伸懒腰)」等）。
                        3. 避免过度轻浮或戏谑的口语（如「噢噢什么噢噢」）。
                        你可以使用简单的 Markdown 格式让回复更清晰，比如 **加粗**、- 列表项、换行分段。""")
                .build();
    }

    @Bean(name = DiarySearchFunctionSupport.NAME)
    public FunctionCallback diarySearchFunction(@Lazy DiaryService diaryService) {
        log.info("注册 Function Calling 工具：{}", DiarySearchFunctionSupport.NAME);
        return FunctionCallback.builder()
                .function(DiarySearchFunctionSupport.NAME,
                        (DiarySearchRequest input, ToolContext toolContext) -> {
                            Authentication auth = (Authentication) toolContext.getContext().get("auth");
                            if (auth != null) {
                                SecurityContextHolder.getContext().setAuthentication(auth);
                            }
                            try {
                                return diaryService.searchOwnDiarySummaries(input);
                            } finally {
                                SecurityContextHolder.clearContext();
                            }
                        })
                .description(
                        "检索当前登录用户自己的历史日记摘要。keyword、startDate、endDate 都可选，日期格式为 YYYY-MM-DD。返回日期和内容片段，适合回答「上周为什么不开心」之类的历史问题。")
                .inputType(DiarySearchRequest.class)
                .build();
    }

    @Bean(name = UserStatsFunctionSupport.NAME)
    public FunctionCallback userStatsFunction(@Lazy DiaryService diaryService) {
        log.info("注册 Function Calling 工具：{}", UserStatsFunctionSupport.NAME);
        return FunctionCallback.builder()
                .function(UserStatsFunctionSupport.NAME,
                        (UserStatsRequest input, ToolContext toolContext) -> {
                            Authentication auth = (Authentication) toolContext.getContext().get("auth");
                            if (auth != null) {
                                SecurityContextHolder.getContext().setAuthentication(auth);
                            }
                            try {
                                return diaryService.getOwnMoodStats(input);
                            } finally {
                                SecurityContextHolder.clearContext();
                            }
                        })
                .description("统计当前登录用户最近 N 天（默认 14 天）的日记与情绪分布，返回总日记数、情绪计数和高频主题。适合回答「我最近总是什么心情」这类问题。")
                .inputType(UserStatsRequest.class)
                .build();
    }

    @Bean
    public WebClientCustomizer deepseekWebClientCustomizer(com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        log.info("注册 DeepSeek 稳定版拦截器 (Jackson 树模型精准注入，彻底终结 400 梦魇)");
        return webClientBuilder -> webClientBuilder.filter((request, next) -> {
            if (!request.url().getHost().contains("deepseek.com")) {
                return next.exchange(request);
            }

            // 1. 声明数据捕获容器
            final class BodyCaptureMessage implements org.springframework.http.ReactiveHttpOutputMessage {
                private final org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                private org.reactivestreams.Publisher<? extends org.springframework.core.io.buffer.DataBuffer> bodyPublisher = reactor.core.publisher.Mono.empty();

                @Override
                public org.springframework.http.HttpHeaders getHeaders() { return this.headers; }
                @Override
                public org.springframework.core.io.buffer.DataBufferFactory bufferFactory() { return org.springframework.core.io.buffer.DefaultDataBufferFactory.sharedInstance; }
                @Override
                public void beforeCommit(java.util.function.Supplier<? extends reactor.core.publisher.Mono<Void>> action) {}
                @Override
                public boolean isCommitted() { return false; }
                @Override
                public reactor.core.publisher.Mono<Void> setComplete() { return reactor.core.publisher.Mono.empty(); }

                @Override
                public reactor.core.publisher.Mono<Void> writeWith(org.reactivestreams.Publisher<? extends org.springframework.core.io.buffer.DataBuffer> body) {
                    this.bodyPublisher = body;
                    return reactor.core.publisher.Mono.empty();
                }

                @Override
                public reactor.core.publisher.Mono<Void> writeAndFlushWith(org.reactivestreams.Publisher<? extends org.reactivestreams.Publisher<? extends org.springframework.core.io.buffer.DataBuffer>> body) {
                    this.bodyPublisher = reactor.core.publisher.Flux.from(body).flatMap(p -> p);
                    return reactor.core.publisher.Mono.empty();
                }
            }

            BodyCaptureMessage captureMessage = new BodyCaptureMessage();

            // 2. 触发 Spring AI 默认的 POJO -> JSON 序列化
            @SuppressWarnings("unchecked")
            org.springframework.web.reactive.function.BodyInserter<Object, org.springframework.http.ReactiveHttpOutputMessage> rawInserter =
                (org.springframework.web.reactive.function.BodyInserter<Object, org.springframework.http.ReactiveHttpOutputMessage>) (Object) request.body();

            return rawInserter.insert(captureMessage, new org.springframework.web.reactive.function.BodyInserter.Context() {
                @Override
                public java.util.List<org.springframework.http.codec.HttpMessageWriter<?>> messageWriters() {
                    java.util.List<org.springframework.http.codec.HttpMessageWriter<?>> writers = new java.util.ArrayList<>();
                    writers.add(new org.springframework.http.codec.EncoderHttpMessageWriter<>(
                        new org.springframework.http.codec.json.Jackson2JsonEncoder(objectMapper, org.springframework.http.MediaType.APPLICATION_JSON)
                    ));
                    writers.addAll(org.springframework.web.reactive.function.client.ExchangeStrategies.withDefaults().messageWriters());
                    return writers;
                }

                @Override
                public java.util.Optional<org.springframework.http.server.reactive.ServerHttpRequest> serverRequest() {
                    return java.util.Optional.empty();
                }

                @Override
                public java.util.Map<String, Object> hints() {
                    return java.util.Map.of();
                }
            }).then(reactor.core.publisher.Mono.defer(() -> {
                // 3. 融合并拦截原始字节流
                return org.springframework.core.io.buffer.DataBufferUtils.join(captureMessage.bodyPublisher)
                        .flatMap(dataBuffer -> {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            org.springframework.core.io.buffer.DataBufferUtils.release(dataBuffer);

                            String bodyStr = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);

                            try {
                                // 4. 面向对象的高级修改：利用 Jackson 语法树精准清洗历史消息
                                com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(bodyStr);
                                if (root.has("messages") && root.get("messages").isArray()) {
                                    com.fasterxml.jackson.databind.node.ArrayNode messages =
                                        (com.fasterxml.jackson.databind.node.ArrayNode) root.get("messages");

                                    for (com.fasterxml.jackson.databind.JsonNode msg : messages) {
                                        if (msg.isObject()) {
                                            com.fasterxml.jackson.databind.node.ObjectNode msgObj =
                                                (com.fasterxml.jackson.databind.node.ObjectNode) msg;

                                            String role = msgObj.path("role").asText();
                                            // 斩断死穴：只要是携带工具调用的助理历史消息，必须强制补齐空字符串参数
                                            if ("assistant".equals(role) && msgObj.has("tool_calls")) {
                                                msgObj.put("content", "");
                                                msgObj.put("reasoning_content", "");
                                            }
                                        }
                                    }
                                    bodyStr = objectMapper.writeValueAsString(root);
                                }
                            } catch (Exception e) {
                                log.error("DeepSeek 拦截器解析修改 JSON 异常，执行原样降级抛出", e);
                            }

                            byte[] modifiedBytes = bodyStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);

                            // 5. 组装物理请求，完美承接原有包含 API KEY 的 Authorization 头
                            org.springframework.web.reactive.function.client.ClientRequest finalRequest =
                                org.springframework.web.reactive.function.client.ClientRequest.from(request)
                                    .headers(headers -> {
                                        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                                        headers.setContentLength(modifiedBytes.length);
                                    })
                                    .body(org.springframework.web.reactive.function.BodyInserters.fromValue(modifiedBytes))
                                    .build();

                            return next.exchange(finalRequest);
                        });
            }));
        });
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
