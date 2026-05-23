package com.moodcopilot.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodcopilot.ai.DiarySearchFunctionSupport;
import com.moodcopilot.ai.GraphSearchFunctionSupport;
import com.moodcopilot.ai.GraphSearchRequest;
import com.moodcopilot.ai.GraphSearchResult;
import com.moodcopilot.ai.MemoryExtractionService;
import com.moodcopilot.ai.MemoryQueryFunctionSupport;
import com.moodcopilot.ai.MemoryQueryRequest;
import com.moodcopilot.ai.MemoryQueryResult;
import com.moodcopilot.ai.ReportSnapshotFunctionSupport;
import com.moodcopilot.ai.UserStatsFunctionSupport;
import com.moodcopilot.diary.ReportSnapshotRequest;
import com.moodcopilot.diary.DiarySearchRequest;
import com.moodcopilot.diary.DiaryService;
import com.moodcopilot.diary.UserStatsRequest;
import com.moodcopilot.entity.DiaryKnowledgeGraphEntity;
import com.moodcopilot.mapper.DiaryKnowledgeGraphMapper;
import com.moodcopilot.ai.DiaryImageAnalysisRequest;
import com.moodcopilot.ai.DiaryImageAnalysisFunctionSupport;
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
import org.springframework.context.annotation.Primary;
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
import java.util.Comparator;
import java.util.List;
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
                        - reportSnapshotFunction：读取周报/月报的关键指标（主导象限、正向占比、高能量占比）
                        - userStatsFunction：统计用户的日记和情绪频率
                        - memoryQueryFunction：读取用户当前长期画像条目
                        - graphSearchFunction：根据实体关键词，从图谱中查询因果/情绪归因关系三元组

                        关键行为准则：
                        当用户提到"最近"、"之前"、"上周"、"上个月"、"为什么"、或者你需要核对用户的历史因果关系时，必须主动调用工具查询事实，不要盲目猜测。
                        如果用户的问题涉及过往经历或情绪变化，先查询再回答，不要假装记得没有查到的内容。
                        查到日记后，自然引用日期和内容，例如：「根据你 5/9 的日记...」或「你前几天提到...」。
                        **调用工具时，直接执行 function call，严禁在此之前输出任何文字（包括"我帮你查"等过渡语）。拿到数据后再自然回应。**

                        每次回复控制在2-3句话以内，像朋友发消息一样简短温暖。不要写大段分析或建议，除非用户明确要求。
                        回复较长时请合理分段，用自然换行分隔不同的话题点。

                        重要限制：
                        1. 能否使用 emoji、用几个，完全取决于当前聊天的情绪基调。心情沉重时不加，开心时多用一两个也无妨，凭你的感觉来即可。
                        2. 保持成熟、稳定、克制的语气。绝对禁止进行戏剧化的角色扮演，严禁在回复中使用括号描述动作（例如禁止出现「(打哈欠)」、「(伸懒腰)」等）。
                        3. 避免过度轻浮或戏谑的口语（如「噢噢什么噢噢」）。
                        你可以使用简单的 Markdown 格式让回复更清晰，比如 **加粗**、换行分段。每个列表项（- 开头）必须独占一行，不要多个列表项挤在同一行。""")
                .build();
    }

    @Bean(name = DiarySearchFunctionSupport.NAME)
    public FunctionCallback diarySearchFunction(@Lazy DiaryService diaryService, @Lazy com.moodcopilot.ai.RagMemoryService ragMemoryService, ObjectMapper objectMapper) {
        log.info("注册 Function Calling 工具：{}", DiarySearchFunctionSupport.NAME);
        return FunctionCallback.builder()
                .function(DiarySearchFunctionSupport.NAME,
                        (DiarySearchRequest input, ToolContext toolContext) -> {
                            Authentication auth = (Authentication) toolContext.getContext().get("auth");
                            if (auth != null) {
                                SecurityContextHolder.getContext().setAuthentication(auth);
                            }
                            try {
                                long userId = ((com.moodcopilot.entity.UserEntity) auth.getPrincipal()).getId();
                                com.moodcopilot.diary.DiarySearchResult result = ragMemoryService.searchForTool(userId, input);
                                if (result == null) {
                                    result = diaryService.searchOwnDiarySummaries(input);
                                }
                                
                                // Emit tool results to SSE if sink is available
                                @SuppressWarnings("unchecked")
                                reactor.core.publisher.Sinks.Many<String> sink = 
                                        (reactor.core.publisher.Sinks.Many<String>) toolContext.getContext().get("sseSink");
                                if (sink != null && result != null && result.diaries() != null && !result.diaries().isEmpty()) {
                                    try {
                                        java.util.List<java.util.Map<String, String>> items = new java.util.ArrayList<>();
                                        for (var d : result.diaries()) {
                                            items.add(java.util.Map.of(
                                                "type", "tool_memory",
                                                "diaryId", d.id() != null ? d.id().toString() : "",
                                                "date", d.date() != null ? d.date().toString() : "",
                                                "snippet", d.snippet() != null ? d.snippet() : "",
                                                "toolName", "diarySearch"
                                            ));
                                        }
                                        java.util.Map<String, Object> event = java.util.Map.of(
                                            "type", "tool_references",
                                            "items", items
                                        );
                                        sink.tryEmitNext("[[TOOL_EVENT]]" + objectMapper.writeValueAsString(event));
                                    } catch (Exception e) {
                                        log.warn("Failed to emit tool references for diarySearch", e);
                                    }
                                }
                                return result;
                            } finally {
                                SecurityContextHolder.clearContext();
                            }
                        })
                .description(
                        "检索当前登录用户自己的历史日记、图片描述、音乐元数据等。keyword、startDate、endDate 都可选，日期格式为 YYYY-MM-DD。"
                                + "keyword 参数：要搜索的关键词或语义描述。**由于底层采用向量语义检索，你可以直接输入概念或抽象感觉（例如'关于工作压力的事'、'那张下雨天的图片'），而不需要精确匹配原文词汇。**"
                                + "如果用户意图宽泛，可以传入空字符串，结合时间参数查询。返回日期和内容片段。")
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

    @Bean(name = ReportSnapshotFunctionSupport.NAME)
    public FunctionCallback reportSnapshotFunction(@Lazy DiaryService diaryService) {
        log.info("注册 Function Calling 工具：{}", ReportSnapshotFunctionSupport.NAME);
        return FunctionCallback.builder()
                .function(ReportSnapshotFunctionSupport.NAME,
                        (ReportSnapshotRequest input, ToolContext toolContext) -> {
                            Authentication auth = (Authentication) toolContext.getContext().get("auth");
                            if (auth != null) {
                                SecurityContextHolder.getContext().setAuthentication(auth);
                            }
                            try {
                                return diaryService.getOwnReportSnapshot(input);
                            } finally {
                                SecurityContextHolder.clearContext();
                            }
                        })
                .description("读取当前登录用户周报或月报的关键指标。period 可选 week/month，offset 可选（默认0）。返回主导象限、正向占比、高能量占比和日记数。")
                .inputType(ReportSnapshotRequest.class)
                .build();
    }

    @Bean(name = MemoryQueryFunctionSupport.NAME)
    public FunctionCallback memoryQueryFunction(@Lazy MemoryExtractionService memoryExtractionService, @Lazy com.moodcopilot.ai.RagMemoryService ragMemoryService, ObjectMapper objectMapper) {
        log.info("注册 Function Calling 工具：{}", MemoryQueryFunctionSupport.NAME);
        return FunctionCallback.builder()
                .function(MemoryQueryFunctionSupport.NAME,
                        (MemoryQueryRequest input, ToolContext toolContext) -> {
                            Authentication auth = (Authentication) toolContext.getContext().get("auth");
                            if (auth != null) {
                                SecurityContextHolder.getContext().setAuthentication(auth);
                            }
                            try {
                                long userId = ((com.moodcopilot.entity.UserEntity) auth.getPrincipal()).getId();
                                int limit = input != null && input.limit() != null ? input.limit() : 20;
                                int clampedLimit = Math.min(50, Math.max(1, limit));
                                String keyword = input != null && input.keyword() != null ? input.keyword().trim() : "";

                                List<MemoryQueryResult.MemoryItem> items = new java.util.ArrayList<>();
                                if (!keyword.isBlank()) {
                                    // 语义搜索画像
                                    var hits = ragMemoryService.search(userId, keyword, clampedLimit, com.moodcopilot.ai.RagMemoryService.SOURCE_PROFILE);
                                    for (var hit : hits) {
                                        if (hit.content() != null) {
                                            String content = hit.content();
                                            String key = "画像片段";
                                            String val = content;
                                            if (content.startsWith("用户长期画像 - ")) {
                                                content = content.substring("用户长期画像 - ".length());
                                                String[] parts = content.split(":", 2);
                                                if (parts.length == 2) {
                                                    key = parts[0].trim();
                                                    val = parts[1].trim();
                                                }
                                            }
                                            items.add(new MemoryQueryResult.MemoryItem(key, val, null));
                                        }
                                    }
                                } else {
                                    // 降级全量拉取
                                    items = memoryExtractionService
                                            .listCurrentUserMemories().stream()
                                            .sorted(Comparator.comparing(
                                                    m -> m.getUpdateTime(),
                                                    Comparator.nullsLast(Comparator.reverseOrder())))
                                            .limit(clampedLimit)
                                            .map(m -> new MemoryQueryResult.MemoryItem(
                                                    m.getAttributeKey(),
                                                    m.getAttributeValue(),
                                                    m.getUpdateTime() != null ? m.getUpdateTime().toString() : null))
                                            .toList();
                                }

                                MemoryQueryResult result = new MemoryQueryResult(items.size(), items,
                                        items.isEmpty() ? "当前暂无符合条件的长期画像条目" : "已返回长期画像条目");

                                // Emit tool results to SSE if sink is available
                                @SuppressWarnings("unchecked")
                                reactor.core.publisher.Sinks.Many<String> sink = 
                                        (reactor.core.publisher.Sinks.Many<String>) toolContext.getContext().get("sseSink");
                                if (sink != null && !items.isEmpty()) {
                                    try {
                                        java.util.List<java.util.Map<String, String>> eventItems = new java.util.ArrayList<>();
                                        for (var m : items) {
                                            eventItems.add(java.util.Map.of(
                                                "type", "profile_memory",
                                                "key", m.attributeKey() != null ? m.attributeKey() : "",
                                                "value", m.attributeValue() != null ? m.attributeValue() : "",
                                                "toolName", "memoryQuery"
                                            ));
                                        }
                                        java.util.Map<String, Object> event = java.util.Map.of(
                                            "type", "tool_references",
                                            "items", eventItems
                                        );
                                        sink.tryEmitNext("[[TOOL_EVENT]]" + objectMapper.writeValueAsString(event));
                                    } catch (Exception e) {
                                        log.warn("Failed to emit tool references for memoryQuery", e);
                                    }
                                }

                                return result;
                            } finally {
                                SecurityContextHolder.clearContext();
                            }
                        })
                .description("读取当前登录用户的长期画像条目列表。可以通过 keyword 进行语义检索特定的画像片段（例如'我喜欢的食物'）。如果不提供 keyword 则返回最近更新的条目。limit 可选，默认 20，最大 50。")
                .inputType(MemoryQueryRequest.class)
                .build();
    }

    @Bean(name = GraphSearchFunctionSupport.NAME)
    public FunctionCallback graphSearchFunction(@Lazy DiaryKnowledgeGraphMapper diaryKnowledgeGraphMapper, ObjectMapper objectMapper) {
        log.info("注册 Function Calling 工具：{}", GraphSearchFunctionSupport.NAME);
        return FunctionCallback.builder()
                .function(GraphSearchFunctionSupport.NAME,
                        (GraphSearchRequest input, ToolContext toolContext) -> {
                            Authentication auth = (Authentication) toolContext.getContext().get("auth");
                            if (auth != null) {
                                SecurityContextHolder.getContext().setAuthentication(auth);
                            }
                            try {
                                long userId = ((com.moodcopilot.entity.UserEntity) auth.getPrincipal()).getId();
                                String keyword = input != null && input.keyword() != null ? input.keyword().trim() : "";
                                int limit = input != null && input.limit() != null ? input.limit() : 20;
                                int clampedLimit = Math.min(50, Math.max(1, limit));

                                List<GraphSearchResult.GraphItem> items = new java.util.ArrayList<>();
                                if (!keyword.isBlank()) {
                                    LambdaQueryWrapper<DiaryKnowledgeGraphEntity> wrapper = new LambdaQueryWrapper<DiaryKnowledgeGraphEntity>()
                                            .eq(DiaryKnowledgeGraphEntity::getUserId, userId)
                                            .and(w -> w
                                                    .like(DiaryKnowledgeGraphEntity::getHeadEntity, keyword)
                                                    .or()
                                                    .like(DiaryKnowledgeGraphEntity::getRelation, keyword)
                                                    .or()
                                                    .like(DiaryKnowledgeGraphEntity::getTailEntity, keyword))
                                            .orderByDesc(DiaryKnowledgeGraphEntity::getCreatedAt)
                                            .last("LIMIT " + clampedLimit);
                                    List<DiaryKnowledgeGraphEntity> triples = diaryKnowledgeGraphMapper.selectList(wrapper);
                                    for (DiaryKnowledgeGraphEntity t : triples) {
                                        items.add(new GraphSearchResult.GraphItem(
                                                t.getHeadEntity() + " " + t.getRelation() + " " + t.getTailEntity(),
                                                t.getCreatedAt() != null ? t.getCreatedAt().toString() : null,
                                                t.getDiaryId()));
                                    }
                                }

                                MemoryQueryResult result; // Actually it returns GraphSearchResult, we're returning GraphSearchResult
                                GraphSearchResult searchResult = new GraphSearchResult(items.size(), items,
                                        items.isEmpty() ? "未找到与 '" + keyword + "' 相关的图谱三元组" : "已返回图谱三元组");

                                // Emit tool results to SSE if sink is available
                                @SuppressWarnings("unchecked")
                                reactor.core.publisher.Sinks.Many<String> sink = 
                                        (reactor.core.publisher.Sinks.Many<String>) toolContext.getContext().get("sseSink");
                                if (sink != null && !items.isEmpty()) {
                                    try {
                                        java.util.List<java.util.Map<String, String>> eventItems = new java.util.ArrayList<>();
                                        for (var g : items) {
                                            eventItems.add(java.util.Map.of(
                                                "type", "graph_memory",
                                                "snippet", g.content() != null ? g.content() : "",
                                                "date", g.date() != null ? g.date() : "",
                                                "diaryId", g.diaryId() != null ? g.diaryId().toString() : "",
                                                "toolName", "graphSearch"
                                            ));
                                        }
                                        java.util.Map<String, Object> event = java.util.Map.of(
                                            "type", "tool_references",
                                            "items", eventItems
                                        );
                                        sink.tryEmitNext("[[TOOL_EVENT]]" + objectMapper.writeValueAsString(event));
                                    } catch (Exception e) {
                                        log.warn("Failed to emit tool references for graphSearch", e);
                                    }
                                }

                                return searchResult;
                            } finally {
                                SecurityContextHolder.clearContext();
                            }
                        })
                .description("根据实体关键词，从知识图谱中查询因果/情绪归因关系三元组。keyword 是要搜索的实体关键词（如'工作'、'失眠'），limit 可选，默认 20，最大 50。返回三元组列表（headEntity relation tailEntity）。适合回答「什么导致了什么」、「为什么」这类因果追溯问题。")
                .inputType(GraphSearchRequest.class)
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

    @Bean(name = DiaryImageAnalysisFunctionSupport.NAME)
    @org.springframework.context.annotation.Description("调用视觉大模型对用户日记中的图片进行深度内容与情感分析，当默认的简短图片描述无法回答用户提问时使用。此函数只在用户提问涉及到图片具体细节时才应该被调用。注意：由于视觉模型消耗较大，本功能随用户等级具有严格的每日使用限额。")
    public FunctionCallback diaryImageAnalysisFunction(DiaryImageAnalysisFunctionSupport support) {
        return FunctionCallback.builder()
                .function(DiaryImageAnalysisFunctionSupport.NAME, support)
                .description("调用视觉大模型对用户日记中的图片进行深度内容与情感分析。需要传入日记ID列表（可从普通检索中获取）以及希望视觉模型重点关注的提问。")
                .inputType(DiaryImageAnalysisRequest.class)
                .build();
    }
}
