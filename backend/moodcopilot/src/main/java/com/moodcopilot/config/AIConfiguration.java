package com.moodcopilot.config;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;
import io.netty.channel.ChannelOption;
import reactor.core.publisher.Mono;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class AIConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AIConfiguration.class);

    @Bean
    public Cache<String, List<com.moodcopilot.entity.dto.CustomChatMessage>> userChatMemories() {
        // 以 userId:conversationId 作为 key 的会话记忆容器。
        // 30 分钟无访问自动过期 + 最多 500 条，防止 2C4G 服务器 OOM。
        return Caffeine.newBuilder()
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .maximumSize(500)
                .build();
    }

    @Bean
    public ChatClient analysisChatClient(ChatClient.Builder builder,
            @Value("${spring.ai.analysis.max-tokens:32768}") int analysisMaxTokens) {
        // 分析模型客户端：专门用于日记分析、周/月报总结、长期画像提取。
        // 分析任务可能包含思考过程，使用独立输出预算；聊天客户端继续使用全局默认值。
        log.info("初始化分析模型客户端，maxTokens={}", analysisMaxTokens);
        return builder
                .defaultOptions(OpenAiChatOptions.builder().maxTokens(analysisMaxTokens).build())
                .build();
    }

    /**
     * 仅供 PersonaService 使用（画像编译的非常规路径）。
     * 聊天主链路已改走 ChatAgentLoop，不再经过这个客户端。
     * <p>
     * 这里刻意不设 defaultSystem：Spring AI 的 system(Consumer) 在显式传入非空文本时会
     * 整体覆盖 defaultSystem（见 DefaultChatClientRequestSpec#system 的字节码 —— 偏移 21-43
     * 是 systemText = hasText(spec.text()) ? spec.text() : this.systemText），
     * 而原先两个调用方都传非空 system，所以那段护栏从未生效。
     * 护栏的真实归属地是 ai.prompts.agentToolsPrompt，由 ChatService#buildContext 注入。
     */
    @Bean
    public ChatClient chatChatClient(ChatClient.Builder builder) {
        log.info("初始化聊天模型客户端：供画像编译使用（聊天主链路走 ChatAgentLoop）");
        return builder.build();
    }

    /**
     * 自定义 WebClient.Builder Bean，替代 Spring Boot 自动配置的 WebClient.Builder。
     * 在每个 WebClient 请求发出前，利用 Jackson 树模型精准清洗消息体：
     * 强制将 assistant + tool_calls 消息的 content/reasoning_content 补齐为空字符串，
     * 防止 Node.js 代理网关因 null.length 崩溃。
     */
    @Bean
    @Scope("prototype")
    public WebClient.Builder webClientBuilder(
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            @Value("${moodcopilot.ai.http-timeout-seconds:90}") int httpTimeoutSeconds) {
        log.info("注册 AI WebClient.Builder（含 content-null 拦截过滤器）");
        int timeoutSeconds = Math.max(1, httpTimeoutSeconds);
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .responseTimeout(Duration.ofSeconds(timeoutSeconds));
        WebClient.Builder builder = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter((request, next) -> next.exchange(request)
                        .timeout(Duration.ofSeconds(timeoutSeconds)));
        return builder.filter((request, next) -> {

            // 1. 声明数据捕获容器
            final class BodyCaptureMessage implements org.springframework.http.ReactiveHttpOutputMessage {
                private final org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                private org.reactivestreams.Publisher<? extends org.springframework.core.io.buffer.DataBuffer> bodyPublisher = reactor.core.publisher.Mono
                        .empty();

                @Override
                public org.springframework.http.HttpHeaders getHeaders() {
                    return this.headers;
                }

                @Override
                public org.springframework.core.io.buffer.DataBufferFactory bufferFactory() {
                    return org.springframework.core.io.buffer.DefaultDataBufferFactory.sharedInstance;
                }

                @Override
                public void beforeCommit(
                        java.util.function.Supplier<? extends reactor.core.publisher.Mono<Void>> action) {
                }

                @Override
                public boolean isCommitted() {
                    return false;
                }

                @Override
                public reactor.core.publisher.Mono<Void> setComplete() {
                    return reactor.core.publisher.Mono.empty();
                }

                @Override
                public reactor.core.publisher.Mono<Void> writeWith(
                        org.reactivestreams.Publisher<? extends org.springframework.core.io.buffer.DataBuffer> body) {
                    this.bodyPublisher = body;
                    return reactor.core.publisher.Mono.empty();
                }

                @Override
                public reactor.core.publisher.Mono<Void> writeAndFlushWith(
                        org.reactivestreams.Publisher<? extends org.reactivestreams.Publisher<? extends org.springframework.core.io.buffer.DataBuffer>> body) {
                    this.bodyPublisher = reactor.core.publisher.Flux.from(body).flatMap(p -> p);
                    return reactor.core.publisher.Mono.empty();
                }
            }

            BodyCaptureMessage captureMessage = new BodyCaptureMessage();

            // 2. 触发 Spring AI 默认的 POJO -> JSON 序列化
            @SuppressWarnings("unchecked")
            org.springframework.web.reactive.function.BodyInserter<Object, org.springframework.http.ReactiveHttpOutputMessage> rawInserter = (org.springframework.web.reactive.function.BodyInserter<Object, org.springframework.http.ReactiveHttpOutputMessage>) (Object) request
                    .body();

            return rawInserter
                    .insert(captureMessage, new org.springframework.web.reactive.function.BodyInserter.Context() {
                        @Override
                        public java.util.List<org.springframework.http.codec.HttpMessageWriter<?>> messageWriters() {
                            java.util.List<org.springframework.http.codec.HttpMessageWriter<?>> writers = new java.util.ArrayList<>();
                            writers.add(new org.springframework.http.codec.EncoderHttpMessageWriter<>(
                                    new org.springframework.http.codec.json.Jackson2JsonEncoder(objectMapper,
                                            org.springframework.http.MediaType.APPLICATION_JSON)));
                            writers.addAll(org.springframework.web.reactive.function.client.ExchangeStrategies
                                    .withDefaults().messageWriters());
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
                                            com.fasterxml.jackson.databind.node.ArrayNode messages = (com.fasterxml.jackson.databind.node.ArrayNode) root
                                                    .get("messages");

                                            for (com.fasterxml.jackson.databind.JsonNode msg : messages) {
                                                if (msg.isObject()) {
                                                    com.fasterxml.jackson.databind.node.ObjectNode msgObj = (com.fasterxml.jackson.databind.node.ObjectNode) msg;

                                                    String role = msgObj.path("role").asText();
                                                    // Node 代理网关在 null.length 上会崩，所以带工具调用的 assistant
                                                    // 消息必须有这两个字段。但只在缺失时补空 —— 无条件覆写会抹掉
                                                    // 模型在发起工具调用之前已经说出口的 content。
                                                    if ("assistant".equals(role) && msgObj.has("tool_calls")) {
                                                        if (!msgObj.hasNonNull("content")) {
                                                            msgObj.put("content", "");
                                                        }
                                                        if (!msgObj.hasNonNull("reasoning_content")) {
                                                            msgObj.put("reasoning_content", "");
                                                        }
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
                                    org.springframework.web.reactive.function.client.ClientRequest finalRequest = org.springframework.web.reactive.function.client.ClientRequest
                                            .from(request)
                                            .headers(headers -> {
                                                headers.setContentType(
                                                        org.springframework.http.MediaType.APPLICATION_JSON);
                                                headers.setContentLength(modifiedBytes.length);
                                            })
                                            .body(org.springframework.web.reactive.function.BodyInserters
                                                    .fromValue(modifiedBytes))
                                            .build();

                                    return next.exchange(finalRequest);
                                });
                    }));
        });
    }

    @Primary
    @Bean(name = "aiExecutor")
    public Executor aiExecutor() {
        // 使用支持虚拟线程的 Executor，极大提高高并发下 AI 大模型请求（I/O 密集型）的吞吐量
        org.springframework.core.task.SimpleAsyncTaskExecutor executor = new org.springframework.core.task.SimpleAsyncTaskExecutor("AI-Task-");
        executor.setVirtualThreads(true);
        executor.setTaskDecorator(task -> () -> {
            long startedAt = System.currentTimeMillis();
            try {
                task.run();
            } finally {
                long durationMs = System.currentTimeMillis() - startedAt;
                if (durationMs > 30_000) {
                    log.warn("AI 异步任务耗时较长（>30s），durationMs={}，thread={}", durationMs, Thread.currentThread().getName());
                }
            }
        });
        log.info("AI 异步线程池已初始化为虚拟线程模式");
        return executor;
    }
}
