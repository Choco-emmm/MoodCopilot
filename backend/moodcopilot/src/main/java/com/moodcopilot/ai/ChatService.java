package com.moodcopilot.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.*;
import com.moodcopilot.entity.UserProfileMemoryEntity;
import com.moodcopilot.mapper.ChatConversationMapper;
import com.moodcopilot.diary.DiarySearchResult;
import com.moodcopilot.diary.DiaryService;
import com.moodcopilot.diary.ReportSnapshotRequest;
import com.moodcopilot.diary.ReportSnapshotResult;
import com.moodcopilot.diary.UserStatsRequest;
import com.moodcopilot.diary.UserStatsResult;
import com.moodcopilot.common.RateLimitException;
import com.moodcopilot.growth.ExpAction;
import com.moodcopilot.growth.UserGrowthService;
import com.moodcopilot.security.RateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final String MSG_PREFIX = "chat:msgs:";
    private static final String SUMMARY_PREFIX = "chat:summary:";
    private static final String REF_REMINDER = "请优先结合我引用的日记内容来回应，不要忽略日记中的具体细节和情绪";
    private static final int COMPRESSION_TRIGGER_MSG_COUNT = 20;
    private static final int KEEP_RECENT_MSG_COUNT = 10;

    private final ChatClient chatChatClient;
    private final ChatClient analysisChatClient;
    private final ChatConversationMapper conversationMapper;
    private final DeepSeekReasoningClient reasoningClient;
    private final Cache<String, ChatMemory> userChatMemories;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RateLimitService rateLimitService;
    private final UserGrowthService userGrowthService;
    private final DiaryService diaryService;
    private final MemoryExtractionService memoryExtractionService;
    private final RagMemoryService ragMemoryService;
    private final AiAnalysisService aiAnalysisService;
    private final DeepSeekClient deepSeekClient;
    private final com.moodcopilot.mapper.DiaryKnowledgeGraphMapper diaryKnowledgeGraphMapper;
    private final com.moodcopilot.mapper.DiaryMapper diaryMapper;
    private final VisionService visionService;
    private final com.moodcopilot.config.AiPromptProperties aiPrompts;
    private final com.moodcopilot.event.LifeEventService lifeEventService;
    private final com.moodcopilot.event.LifeChapterService lifeChapterService;
    private final ChatTitleService chatTitleService;

    public ChatService(ChatClient chatChatClient,
            ChatClient analysisChatClient,
            ChatConversationMapper conversationMapper,
            DeepSeekReasoningClient reasoningClient,
            Cache<String, ChatMemory> userChatMemories,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RateLimitService rateLimitService,
            UserGrowthService userGrowthService,
            DiaryService diaryService,
            MemoryExtractionService memoryExtractionService,
            RagMemoryService ragMemoryService,
            AiAnalysisService aiAnalysisService,
            DeepSeekClient deepSeekClient,
            com.moodcopilot.mapper.DiaryKnowledgeGraphMapper diaryKnowledgeGraphMapper,
            com.moodcopilot.mapper.DiaryMapper diaryMapper,
            VisionService visionService,
            com.moodcopilot.config.AiPromptProperties aiPrompts,
            @org.springframework.context.annotation.Lazy com.moodcopilot.event.LifeEventService lifeEventService,
            @org.springframework.context.annotation.Lazy com.moodcopilot.event.LifeChapterService lifeChapterService,
            ChatTitleService chatTitleService) {
        this.chatChatClient = chatChatClient;
        this.analysisChatClient = analysisChatClient;
        this.conversationMapper = conversationMapper;
        this.reasoningClient = reasoningClient;
        this.userChatMemories = userChatMemories;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.rateLimitService = rateLimitService;
        this.userGrowthService = userGrowthService;
        this.diaryService = diaryService;
        this.memoryExtractionService = memoryExtractionService;
        this.ragMemoryService = ragMemoryService;
        this.aiAnalysisService = aiAnalysisService;
        this.deepSeekClient = deepSeekClient;
        this.diaryKnowledgeGraphMapper = diaryKnowledgeGraphMapper;
        this.diaryMapper = diaryMapper;
        this.visionService = visionService;
        this.aiPrompts = aiPrompts;
        this.lifeEventService = lifeEventService;
        this.lifeChapterService = lifeChapterService;
        this.chatTitleService = chatTitleService;
    }

    // ---- 会话管理 ----

    public List<ChatConversationEntity> listConversations() {
        UserEntity user = currentUser();
        List<ChatConversationEntity> conversations = conversationMapper.selectList(
                new LambdaQueryWrapper<ChatConversationEntity>()
                        .eq(ChatConversationEntity::getUserId, user.getId())
                        .orderByDesc(ChatConversationEntity::getUpdatedAt));
        return conversations;
    }

    public ChatConversationEntity createConversation(String title) {
        UserEntity user = currentUser();
        ChatConversationEntity conv = new ChatConversationEntity();
        conv.setUserId(user.getId());
        conv.setTitle(title != null && !title.isBlank() ? title : "新聊天");
        conv.setCreatedAt(java.time.LocalDateTime.now());
        conv.setUpdatedAt(java.time.LocalDateTime.now());
        conversationMapper.insert(conv);
        return conv;
    }

    /** 提交标题生成任务，不让标题模型调用阻塞聊天请求。 */
    public void scheduleConversationTitle(Long conversationId, String firstMessage) {
        UserEntity user = currentUser();
        chatTitleService.requestGeneration(conversationId, user.getId(), firstMessage);
    }

    public void deleteConversation(Long conversationId) {
        UserEntity user = currentUser();
        ChatConversationEntity conv = conversationMapper.selectById(conversationId);
        if (conv == null || !conv.getUserId().equals(user.getId())) {
            throw new ResponseStatusException(BAD_REQUEST, "会话不存在");
        }
        log.info("删除聊天会话，userId={}，conversationId={}", user.getId(), conversationId);
        // 清除 ChatMemory
        String memKey = user.getId() + ":" + conversationId;
        userChatMemories.invalidate(memKey);
        // 清除 Redis 消息历史
        try {
            redisTemplate.delete(MSG_PREFIX + conversationId);
        } catch (Exception ignored) {
        }
        // 清除压缩摘要
        try {
            redisTemplate.delete(SUMMARY_PREFIX + conversationId);
        } catch (Exception ignored) {
        }
        // 删除数据库记录
        conversationMapper.deleteById(conversationId);
    }

    // ---- 聊天 ----

    public List<Map<String, String>> getWelcomeTopics() {
        UserEntity user = currentUser();
        Long userId = user.getId();
        String cacheKey = "chat:welcome_topics:" + userId;
        String lockKey = "chat:welcome_topics:lock:" + userId;

        boolean needRefresh = false;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey))) {
            if (Boolean.FALSE.equals(redisTemplate.hasKey(lockKey))) {
                needRefresh = true;
            }
        } else {
            needRefresh = true;
        }

        if (needRefresh) {
            // Check and set lock to prevent concurrent generations, lock lasts for 4 hours
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofHours(4));
            if (Boolean.TRUE.equals(acquired)) {
                String memoryBackground = memoryExtractionService.buildCoreUserMemoryPrompt();
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        generateAndCacheWelcomeTopics(userId, memoryBackground);
                    } catch (Exception e) {
                        log.error("Async generate welcome topics failed", e);
                        // If generation failed, delete the lock so it can be retried on next request
                        redisTemplate.delete(lockKey);
                    }
                });
            }
        }

        List<Map<String, String>> topics = null;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                topics = objectMapper.readValue(cached, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, String>>>() {});
            }
        } catch (Exception e) {
            log.warn("读取 welcome topics 缓存失败", e);
        }

        if (topics == null || topics.isEmpty()) {
            topics = List.of(
                    Map.of("icon", "🌟", "text", "分析我最近三天的情绪波动"),
                    Map.of("icon", "💡", "text", "帮我看看是什么最容易让我内耗"),
                    Map.of("icon", "🌿", "text", "推荐一些适合我解压的音乐与方法"),
                    Map.of("icon", "💬", "text", "今天有点累，陪我聊一下")
            );
        }

        // 探测当前用户是否有已到期且待回访的重要事件，若有，将其置顶在第 1 位
        try {
            var pendingOpt = lifeEventService.getPendingEventForFollowUp(userId);
            if (pendingOpt.isPresent()) {
                var ev = pendingOpt.get();
                Map<String, String> eventTopic = new LinkedHashMap<>();
                eventTopic.put("icon", "💌");
                eventTopic.put("text", "聊聊关于「" + ev.getTitle() + "」的进展");
                eventTopic.put("eventId", String.valueOf(ev.getId()));
                eventTopic.put("greeting", "我一直惦记着你关于「" + ev.getTitle() + "」的事，一切还顺利吗？心里感觉怎么样？");

                List<Map<String, String>> merged = new ArrayList<>();
                merged.add(eventTopic);
                for (var t : topics) {
                    if (merged.size() >= 4) break;
                    merged.add(t);
                }
                return merged;
            }
        } catch (Exception e) {
            log.warn("探测重要未决事件失败 userId={}", userId, e);
        }

        return topics;
    }

    private void generateAndCacheWelcomeTopics(Long userId, String memoryBackground) {
        String cacheKey = "chat:welcome_topics:" + userId;
        String systemPrompt = aiPrompts.getWelcomeTopicsSystemPrompt() + "\n用户背景画像：\n" + memoryBackground;

        try {
            String response = analysisChatClient.prompt()
                    .system(systemPrompt)
                    .user("请直接输出纯 JSON 数组，不要包含任何 Markdown 格式或多余的解释。")
                    .call()
                    .content();

            String cleanedJson = JsonUtils.cleanJson(response);
            List<Map<String, String>> topics = objectMapper.readValue(cleanedJson, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, String>>>() {});

            if (topics != null && !topics.isEmpty()) {
                // 缓存 7 天（过期则回退到默认话题），靠 4 小时的 lockKey 来控制刷新频率
                redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(topics), Duration.ofDays(7));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 流式聊天结果：RAG 上下文 + AI 文字流 */
    public record ChatStreamContext(String ragContext, Flux<String> stream) {}

    public ChatStreamContext chat(Long conversationId, String message, List<String> refs, String memoryBackground, boolean useReasoning) {
        // 流式接口：先统一装配上下文，再按用户显式选择的模型执行。
        message = augmentWithRefReminder(message, refs);
        ChatExecutionResult exec = prepareChatExecution(conversationId, message, refs, memoryBackground, useReasoning);
        ChatRequest request = exec.request();
        Authentication auth = exec.auth();
        String ragCtx = exec.ragCtx();

        if (exec.useReasoning()) {
            log.info("聊天路由结果：reasoning（流式），conversationId={}，messageLength={}", conversationId,
                    message == null ? 0 : message.length());
            return new ChatStreamContext(ragCtx, callReasoningModelStream(request, message, auth, conversationId, ragCtx));
        }

        log.info("聊天路由结果：normal，conversationId={}，messageLength={}", conversationId,
                message == null ? 0 : message.length());

        Sinks.Many<String> sseSink = Sinks.many().unicast().onBackpressureBuffer();

        Flux<String> stream = chatChatClient.prompt()
                .user(message)
                .system(s -> {
                    StringBuilder sys = new StringBuilder();
                    sys.append(aiPrompts.getAgentToolsPrompt()).append("\n\n");
                    if (request.context() != null && !request.context().isBlank()) {
                        sys.append(request.context()).append("\n\n");
                    }
                    if (request.summary() != null && !request.summary().isBlank()) {
                        sys.append("<conversation_summary>\n")
                           .append(request.summary())
                           .append("\n</conversation_summary>\n\n");
                    }
                    sys.append(ragCtx).append("\n").append(buildChapterContext(exec.user()))
                            .append(buildTimeMetadata());
                    s.text(sys.toString());
                })
                .advisors(new MessageChatMemoryAdvisor(request.memory()))
                .functions(
                        DiarySearchFunctionSupport.NAME,
                        UserStatsFunctionSupport.NAME,
                        ReportSnapshotFunctionSupport.NAME,
                        MemoryQueryFunctionSupport.NAME,
                        GraphSearchFunctionSupport.NAME,
                        DiaryImageAnalysisFunctionSupport.NAME)
                .toolContext(Map.of("auth", auth, "sseSink", sseSink))
                .stream()
                .content()
                .doOnComplete(sseSink::tryEmitComplete)
                .doOnError(sseSink::tryEmitError);

        Flux<String> mergedStream = Flux.merge(stream, sseSink.asFlux());
        return new ChatStreamContext(ragCtx, mergedStream);
    }

    public String reply(Long conversationId, String message, List<String> refs, String memoryBackground, boolean useReasoning) {
        // 非流式接口：移动端/公网优先走这里，减少 SSE 连接不稳定的影响。
        message = augmentWithRefReminder(message, refs);
        ChatExecutionResult exec = prepareChatExecution(conversationId, message, refs, memoryBackground, useReasoning);
        ChatRequest request = exec.request();
        Authentication auth = exec.auth();
        String ragCtx = exec.ragCtx();

        if (exec.useReasoning()) {
            log.info("非流式聊天路由结果：reasoning，conversationId={}，messageLength={}", conversationId,
                    message == null ? 0 : message.length());
            return callReasoningModel(request, message, auth, conversationId, ragCtx);
        }

        log.info("非流式聊天路由结果：normal，conversationId={}，messageLength={}", conversationId,
                message == null ? 0 : message.length());

        String result = chatChatClient.prompt()
                .user(message)
                .system(s -> {
                    StringBuilder sys = new StringBuilder();
                    sys.append(aiPrompts.getAgentToolsPrompt()).append("\n\n");
                    if (request.context() != null && !request.context().isBlank()) {
                        sys.append(request.context()).append("\n\n");
                    }
                    if (request.summary() != null && !request.summary().isBlank()) {
                        sys.append("<conversation_summary>\n")
                           .append(request.summary())
                           .append("\n</conversation_summary>\n\n");
                    }
                    sys.append(ragCtx).append("\n").append(buildChapterContext(exec.user()))
                            .append(buildTimeMetadata());
                    s.text(sys.toString());
                })
                .advisors(new MessageChatMemoryAdvisor(request.memory()))
                .functions(
                        DiarySearchFunctionSupport.NAME,
                        UserStatsFunctionSupport.NAME,
                        ReportSnapshotFunctionSupport.NAME,
                        MemoryQueryFunctionSupport.NAME,
                        GraphSearchFunctionSupport.NAME,
                        DiaryImageAnalysisFunctionSupport.NAME)
                .toolContext(Map.of("auth", auth))
                .call()
                .content();
                return result;
    }



    private record ChatExecutionResult(ChatRequest request, Authentication auth, UserEntity user, String ragCtx, boolean useReasoning) {}

    private ChatExecutionResult prepareChatExecution(Long conversationId, String message, List<String> refs, String memoryBackground, boolean requestedUseReasoning) {
        ChatRequest request = prepareChatRequest(conversationId, message, refs, memoryBackground);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserEntity user = currentUser();
        String ragCtx = ""; // 已迁移为 Agentic RAG，不再强制前置全量检索

        // 用户显式选择模型：深度思考额度不足时直接抛出限流异常（429），不再静默降级
        boolean useReasoning;
        if (requestedUseReasoning) {
            rateLimitService.tryAcquire(user, RateLimitService.AiApiType.REASONING);
            useReasoning = true;
        } else {
            rateLimitService.tryAcquire(user, RateLimitService.AiApiType.CHAT);
            useReasoning = false;
        }
        userGrowthService.addExp(user.getId(), ExpAction.CHAT, null);

        return new ChatExecutionResult(request, auth, user, ragCtx, useReasoning);
    }

    private List<Map<String, Object>> buildMessagesForReasoner(ChatRequest request, String message, Authentication auth, String ragCtx) {
        List<Map<String, Object>> msgs = new ArrayList<>();
        StringBuilder sys = new StringBuilder();
        sys.append(aiPrompts.getAgentToolsPrompt()).append("\n\n");
        // 深度分析路由下按需注入 CBT 认知透视技能（日常闲聊不携带，避免说教）
        if (aiPrompts.getCbtCognitiveSkillPrompt() != null && !aiPrompts.getCbtCognitiveSkillPrompt().isBlank()) {
            sys.append(aiPrompts.getCbtCognitiveSkillPrompt()).append("\n\n");
        }
        if (request.context() != null && !request.context().isBlank()) {
            sys.append(request.context()).append("\n\n");
        }
        if (request.summary() != null && !request.summary().isBlank()) {
            sys.append("<conversation_summary>\n")
               .append(request.summary())
               .append("\n</conversation_summary>\n\n");
        }
        if (ragCtx != null && !ragCtx.isBlank()) {
            sys.append(ragCtx).append("\n");
        }
        sys.append(buildReasoningDataContext(auth)).append("\n")
                .append(buildChapterContext(((UserEntity) auth.getPrincipal()).getId()))
                .append(buildTimeMetadata());
        
        msgs.add(Map.of("role", "system", "content", sys.toString()));
        
        List<Message> history = request.memory().get("default", 20);
        if (history != null) {
            for (Message msg : history) {
                String role = switch (msg.getMessageType()) {
                    case USER -> "user";
                    case ASSISTANT -> "assistant";
                    case SYSTEM -> "system";
                    default -> null;
                };
                if (role != null && msg.getText() != null && !msg.getText().isBlank()) {
                    msgs.add(Map.of("role", role, "content", msg.getText()));
                }
            }
        }
        msgs.add(Map.of("role", "user", "content", message));
        return msgs;
    }

    private String callReasoningModel(ChatRequest request, String message, Authentication auth, long conversationId, String ragCtx) {
        log.info("调用思考模型分支（原生 WebClient），messageLength={}", message == null ? 0 : message.length());
        List<Map<String, Object>> msgs = buildMessagesForReasoner(request, message, auth, ragCtx);
        return deepSeekClient.streamReasoner(msgs)
                .filter(e -> e instanceof DeepSeekStreamEvent.TextChunk)
                .map(e -> ((DeepSeekStreamEvent.TextChunk) e).text())
                .reduce(String::concat).block();
    }

    private Flux<String> callReasoningModelStream(ChatRequest request, String message, Authentication auth,
            long conversationId, String ragCtx) {
        log.info("调用思考模型分支（流式原生 WebClient + Agent Loop），messageLength={}", message == null ? 0 : message.length());

        // 手动将用户本轮消息存入 ChatMemory（推理模型绕过了 Spring AI Advisor）
        request.memory().add("default", List.of(new org.springframework.ai.chat.messages.UserMessage(message)));

        List<Map<String, Object>> msgs = buildMessagesForReasoner(request, message, auth, ragCtx);
        List<Map<String, Object>> tools = buildDeepSeekTools();
        Sinks.Many<String> sseSink = Sinks.many().unicast().onBackpressureBuffer();

        Flux<String> textFlux = processReasoningAgentLoop(msgs, tools, auth, 0, sseSink);

        // 收集最终 AI 回复文本，流结束时存入 ChatMemory（不覆写 Redis，由前端负责保存富文本历史）
        StringBuilder finalAiReply = new StringBuilder();
        Flux<String> tracedTextFlux = textFlux
                .doOnNext(finalAiReply::append)
                .doOnComplete(() -> {
                    if (finalAiReply.length() > 0) {
                        request.memory().add("default",
                                List.of(new org.springframework.ai.chat.messages.AssistantMessage(
                                        finalAiReply.toString())));
                        log.info("推理模型对话已存入 ChatMemory，conversationId={}，回复长度={}",
                                conversationId, finalAiReply.length());
                    }
                })
                .doFinally(signalType -> sseSink.tryEmitComplete());

        return Flux.merge(tracedTextFlux, sseSink.asFlux());
    }

    // ---- DeepSeek Agent Loop ----

    private List<Map<String, Object>> buildDeepSeekTools() {
        return List.of(
                buildTool("diarySearchFunction",
                        "检索当前登录用户自己的历史日记、图片描述、音乐元数据等。keyword、startDate、endDate 都可选，日期格式为 YYYY-MM-DD。keyword 参数：要搜索的关键词或语义描述。由于底层采用向量语义检索，你可以直接输入概念或抽象感觉（例如'关于工作压力的事'、'那张下雨天的图片'），而不需要精确匹配原文词汇。如果用户意图宽泛，可以传入空字符串，结合时间参数查询。返回日期和内容片段。",
                        new LinkedHashMap<>() {{
                            put("keyword", Map.of("type", "string", "description", "搜索关键词或语义描述"));
                            put("startDate", Map.of("type", "string", "description", "开始日期，格式 YYYY-MM-DD"));
                            put("endDate", Map.of("type", "string", "description", "结束日期，格式 YYYY-MM-DD"));
                        }},
                        List.of("keyword", "startDate", "endDate")),
                buildTool("userStatsFunction",
                        "统计当前登录用户最近 N 天（默认 14 天）的日记与情绪分布，返回总日记数、情绪计数和高频主题。适合回答「我最近总是什么心情」这类问题。",
                        new LinkedHashMap<>() {{
                            put("days", Map.of("type", "integer", "description", "统计最近多少天，默认 14"));
                        }},
                        List.of("days")),
                buildTool("reportSnapshotFunction",
                        "读取当前登录用户周报或月报的关键指标。period 可选 week/month，offset 可选（默认0）。返回主导象限、正向占比、高能量占比和日记数。",
                        new LinkedHashMap<>() {{
                            put("period", Map.of("type", "string", "description", "报告周期：week 或 month"));
                            put("offset", Map.of("type", "integer", "description", "偏移量，0=当前，-1=上一期"));
                        }},
                        List.of("period", "offset")),
                buildTool("memoryQueryFunction",
                        "读取当前登录用户的长期画像条目列表。可以通过 keyword 进行语义检索特定的画像片段（例如'我喜欢的食物'）。如果不提供 keyword 则返回最近更新的条目。limit 可选，默认 20，最大 50。",
                        new LinkedHashMap<>() {{
                            put("keyword", Map.of("type", "string", "description", "要检索的画像关键词"));
                            put("limit", Map.of("type", "integer", "description", "返回数量上限，默认 20，最大 50"));
                        }},
                        List.of("keyword", "limit")),
                buildTool("graphSearchFunction",
                        "根据实体关键词，从知识图谱中查询因果/情绪归因关系三元组。keyword 是要搜索的实体关键词（如'工作'、'失眠'），limit 可选，默认 20，最大 50。返回三元组列表。适合回答「什么导致了什么」、「为什么」等因果问题。如果想获取用户的整体关系图谱概览，可传入空的 keyword。",
                        new LinkedHashMap<>() {{
                            put("keyword", Map.of("type", "string", "description", "实体关键词，传空获取整体概览"));
                            put("limit", Map.of("type", "integer", "description", "返回数量上限，默认 20，最大 50"));
                        }},
                        List.of("keyword", "limit")),
                buildTool("diaryImageAnalysisFunction",
                        "调用视觉大模型对用户日记中的图片进行深度分析。触发条件：1. 当默认的简短图片描述无法回答提问（如问具体价格、文字细节等）时；2. 当用户明确要求'详细看看'、'还有别的吗'、'列出全部'等表明图片内还有未提及的隐藏信息时，必须强制调用此工具。注意：此功能随用户等级有每日使用限额。",
                        new LinkedHashMap<>() {{
                            put("diaryIds", Map.of("type", "array", "items", Map.of("type", "integer"), "description", "要深度分析图片的日记 ID 列表"));
                            put("prompt", Map.of("type", "string", "description", "希望视觉模型重点关注的提问要求"));
                        }},
                        List.of("diaryIds", "prompt")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildTool(String name, String description,
            LinkedHashMap<String, Object> properties, List<String> required) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", properties);
        params.put("required", required);
        params.put("additionalProperties", false);

        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", params);
        function.put("strict", true);

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }

    private Flux<String> processReasoningAgentLoop(List<Map<String, Object>> messages,
            List<Map<String, Object>> tools,
            Authentication auth,
            int depth,
            Sinks.Many<String> sseSink) {
        if (depth > 5) {
            log.warn("Agent Loop 递归深度达到上限 depth={}，终止递归", depth);
            return Flux.<String>empty();
        }
        if (depth > 0) {
            log.info("Agent Loop 递归 depth={}，messages 数量={}", depth, messages.size());
        }


        return Flux.defer(() -> {
            List<DeepSeekStreamEvent.ToolCallReady> toolCalls = new ArrayList<>();
            //返回的是最底层的flux对象，随时可以开始subscribe订阅，得到最顶层flux对应的工人，然后就可以按调用链执行onNext(T t)和onComplete()方法了
            return deepSeekClient.streamReasoner(messages, tools)
                    .doOnNext(event -> {
                        if (event instanceof DeepSeekStreamEvent.ToolCallReady tool) {
                            toolCalls.add(tool);
                        }
                    })
                    .flatMap(event -> {
                        if (event instanceof DeepSeekStreamEvent.TextChunk text) {
                            return Flux.just(text.text());
                        }
                        return Flux.<String>empty();
                    })
                    .concatWith(Flux.defer(() -> {
                        if (toolCalls.isEmpty()) {
                            return Flux.<String>empty();
                        }

                        for (DeepSeekStreamEvent.ToolCallReady tool : toolCalls) {
                            log.info("Agent Loop 执行工具调用: {} id={} argsLen={}", tool.functionName(),
                                    tool.toolCallId(), tool.argumentsJson().length());

                            try {
                                Object result = executeToolFunction(tool.functionName(), tool.argumentsJson(), auth);
                                String resultJson = objectMapper.writeValueAsString(result);
                                emitToolReferences(tool.functionName(), result, sseSink);

                                Map<String, Object> assistantMsg = new LinkedHashMap<>();
                                assistantMsg.put("role", "assistant");
                                assistantMsg.put("content", "");
                                assistantMsg.put("reasoning_content", "");
                                assistantMsg.put("tool_calls", List.of(Map.of(
                                        "id", tool.toolCallId(),
                                        "type", "function",
                                        "function", Map.of(
                                                "name", tool.functionName(),
                                                "arguments", tool.argumentsJson()))));
                                messages.add(assistantMsg);

                                Map<String, Object> toolMsg = new LinkedHashMap<>();
                                toolMsg.put("role", "tool");
                                toolMsg.put("tool_call_id", tool.toolCallId());
                                toolMsg.put("content", resultJson);
                                messages.add(toolMsg);
                            } catch (Exception e) {
                                log.error("工具调用执行失败: {}", e.getMessage());
                            }
                        }
                        return processReasoningAgentLoop(messages, tools, auth, depth + 1, sseSink);
                    }));
        });
    }

    private void emitToolReferences(String functionName, Object result, Sinks.Many<String> sseSink) {
        if (sseSink == null || result == null) return;
        try {
            List<Map<String, String>> items = new ArrayList<>();
            switch (functionName) {
                case "diarySearchFunction" -> {
                    if (result instanceof DiarySearchResult dsr && dsr.diaries() != null) {
                        for (var d : dsr.diaries()) {
                            items.add(Map.of(
                                    "type", "tool_memory",
                                    "diaryId", d.id() != null ? d.id().toString() : "",
                                    "date", d.date() != null ? d.date().toString() : "",
                                    "snippet", compactToolSnippet(d.snippet()),
                                    "toolName", "diarySearch"));
                        }
                    }
                }
                case "memoryQueryFunction" -> {
                    if (result instanceof MemoryQueryResult mqr && mqr.items() != null) {
                        for (var m : mqr.items()) {
                            String key = m.attributeKey() != null ? m.attributeKey() : "";
                            String val = m.attributeValue() != null ? m.attributeValue() : "";
                            items.add(Map.of(
                                    "type", "profile_memory",
                                    "key", key,
                                    "value", val,
                                    "snippet", key + ": " + val,
                                    "toolName", "memoryQuery"));
                        }
                    }
                }
                case "graphSearchFunction" -> {
                    if (result instanceof GraphSearchResult gsr && gsr.items() != null) {
                        for (var g : gsr.items()) {
                            items.add(Map.of(
                                    "type", "graph_memory",
                                    "snippet", compactToolSnippet(g.content()),
                                    "date", g.date() != null ? g.date() : "",
                                    "diaryId", g.diaryId() != null ? g.diaryId().toString() : "",
                                    "toolName", "graphSearch"));
                        }
                    }
                }
                case "diaryImageAnalysisFunction" -> {
                    if (result instanceof DiaryImageAnalysisFunctionSupport.DiaryImageAnalysisResult dir) {
                        items.add(Map.of(
                                "type", "image_analysis",
                                "snippet", compactToolSnippet(dir.analysisResult()),
                                "toolName", "diaryImageAnalysis"));
                    }
                }
            }
            if (!items.isEmpty()) {
                Map<String, Object> event = Map.of("type", "tool_references", "items", items);
                sseSink.tryEmitNext("[[TOOL_EVENT]]" + objectMapper.writeValueAsString(event));
                log.info("Agent Loop 推送工具引用事件: {} items for {}", items.size(), functionName);
            }
        } catch (Exception e) {
            log.warn("推送工具引用事件失败 {}: {}", functionName, e.getMessage());
        }
    }

    /** 仅限制前端引用面板的摘要，不影响完整工具结果返回给模型。 */
    private String compactToolSnippet(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > 160 ? normalized.substring(0, 160) + "…" : normalized;
    }

    private Object executeToolFunction(String functionName, String argumentsJson, Authentication auth) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            return switch (functionName) {
                case "diarySearchFunction" -> {
                    var req = objectMapper.readValue(argumentsJson, com.moodcopilot.diary.DiarySearchRequest.class);
                    long userId = ((UserEntity) auth.getPrincipal()).getId();
                    DiarySearchResult result = ragMemoryService.searchForTool(userId, req);
                    if (result == null) {
                        result = diaryService.searchOwnDiarySummaries(req);
                    }
                    yield result;
                }
                case "userStatsFunction" -> {
                    var req = objectMapper.readValue(argumentsJson, UserStatsRequest.class);
                    yield diaryService.getOwnMoodStats(req);
                }
                case "reportSnapshotFunction" -> {
                    var req = objectMapper.readValue(argumentsJson, ReportSnapshotRequest.class);
                    yield diaryService.getOwnReportSnapshot(req);
                }
                case "memoryQueryFunction" -> {
                    var req = objectMapper.readValue(argumentsJson, MemoryQueryRequest.class);
                    long userId = ((UserEntity) auth.getPrincipal()).getId();
                    int limit = req.limit() != null ? Math.min(50, Math.max(1, req.limit())) : 20;
                    String keyword = req.keyword() != null ? req.keyword().trim() : "";

                    List<MemoryQueryResult.MemoryItem> items = new ArrayList<>();
                    if (!keyword.isBlank()) {
                        var hits = ragMemoryService.search(userId, keyword, limit, RagMemoryService.SOURCE_PROFILE);
                        for (var hit : hits) {
                            if (hit.content() != null) {
                                String c = hit.content();
                                String key = "画像片段";
                                String val = c;
                                if (c.startsWith("用户长期画像 - ")) {
                                    c = c.substring("用户长期画像 - ".length());
                                    String[] parts = c.split(":", 2);
                                    if (parts.length == 2) {
                                        key = parts[0].trim();
                                        val = parts[1].trim();
                                    }
                                }
                                items.add(new MemoryQueryResult.MemoryItem(key, val, null));
                            }
                        }
                    } else {
                        items = memoryExtractionService.listCurrentUserMemories().stream()
                                .sorted(java.util.Comparator.comparing(
                                        m -> m.getUpdateTime(),
                                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                                .limit(limit)
                                .map(m -> new MemoryQueryResult.MemoryItem(
                                        m.getAttributeKey(),
                                        m.getAttributeValue(),
                                        m.getUpdateTime() != null ? m.getUpdateTime().toString() : null))
                                .toList();
                    }
                    yield new MemoryQueryResult(items.size(), items,
                            items.isEmpty() ? "当前暂无符合条件的长期画像条目" : "已返回长期画像条目");
                }
                case "graphSearchFunction" -> {
                    var req = objectMapper.readValue(argumentsJson, GraphSearchRequest.class);
                    long userId = ((UserEntity) auth.getPrincipal()).getId();
                    String keyword = req.keyword() != null ? req.keyword().trim() : "";
                    int limit = req.limit() != null ? Math.min(50, Math.max(1, req.limit())) : 20;

                    List<GraphSearchResult.GraphItem> items = new ArrayList<>();
                    if (!keyword.isBlank()) {
                        var hits = ragMemoryService.search(userId, keyword, limit, RagMemoryService.SOURCE_GRAPH);
                        java.util.Set<Long> graphIds = new java.util.LinkedHashSet<>();
                        for (var hit : hits) {
                            if (hit.sourceId() != null && hit.sourceId().startsWith("graph:")) {
                                try { graphIds.add(Long.parseLong(hit.sourceId().substring(6))); } catch (NumberFormatException ignored) {}
                            }
                        }
                        if (!graphIds.isEmpty()) {
                            var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.moodcopilot.entity.DiaryKnowledgeGraphEntity>()
                                    .in(com.moodcopilot.entity.DiaryKnowledgeGraphEntity::getId, graphIds);
                            java.util.Map<Long, com.moodcopilot.entity.DiaryKnowledgeGraphEntity> byId = new java.util.LinkedHashMap<>();
                            for (var t : diaryKnowledgeGraphMapper.selectList(wrapper)) {
                                byId.put(t.getId(), t);
                            }
                            for (Long gid : graphIds) {
                                var t = byId.get(gid);
                                if (t != null) {
                                    items.add(new GraphSearchResult.GraphItem(
                                            t.getHeadEntity() + " " + t.getRelation() + " " + t.getTailEntity(),
                                            t.getCreatedAt() != null ? t.getCreatedAt().toString() : null,
                                            t.getDiaryId()));
                                }
                            }
                        }
                    } else {
                        // 降级全量拉取：返回最近的图谱关系概览
                        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.moodcopilot.entity.DiaryKnowledgeGraphEntity>()
                                .eq(com.moodcopilot.entity.DiaryKnowledgeGraphEntity::getUserId, userId)
                                .orderByDesc(com.moodcopilot.entity.DiaryKnowledgeGraphEntity::getCreatedAt)
                                .last("LIMIT " + limit);
                        for (var t : diaryKnowledgeGraphMapper.selectList(wrapper)) {
                            items.add(new GraphSearchResult.GraphItem(
                                    t.getHeadEntity() + " " + t.getRelation() + " " + t.getTailEntity(),
                                    t.getCreatedAt() != null ? t.getCreatedAt().toString() : null,
                                    t.getDiaryId()));
                        }
                    }
                    yield new GraphSearchResult(items.size(), items,
                            items.isEmpty() ? (keyword.isBlank() ? "当前暂无知识图谱记录" : "未找到与 '" + keyword + "' 相关的图谱三元组") : "已返回知识图谱因果三元组（共 " + items.size() + " 条）");
                }
                case "diaryImageAnalysisFunction" -> {
                    var req = objectMapper.readValue(argumentsJson, DiaryImageAnalysisRequest.class);
                    UserEntity user = (UserEntity) auth.getPrincipal();
                    log.info("触发图片深度分析(VLM)工具 userId={}, diaryIds={}, promptLength={}", user.getId(), req.diaryIds(), req.prompt() != null ? req.prompt().length() : 0);
                    try {
                        rateLimitService.tryAcquire(user, RateLimitService.AiApiType.IMAGE_ANALYSIS);
                    } catch (RateLimitException e) {
                        log.warn("图片深度分析(VLM)额度不足，拦截请求 userId={}", user.getId());
                        yield new DiaryImageAnalysisFunctionSupport.DiaryImageAnalysisResult("由于今日图片深度分析次数已达限额，无法分析图片，请明日再试。");
                    }
                    if (req.diaryIds() == null || req.diaryIds().isEmpty()) {
                        log.info("图片深度分析(VLM)失败：未提供日记ID userId={}", user.getId());
                        yield new DiaryImageAnalysisFunctionSupport.DiaryImageAnalysisResult("未提供日记ID，无法分析");
                    }
                    var diaries = diaryMapper.selectBatchIds(req.diaryIds());
                    List<String> images = new ArrayList<>();
                    for (var d : diaries) {
                        if (d.getAuthorUserId().equals(user.getId()) && d.getImages() != null) {
                            images.addAll(d.getImages());
                        }
                    }
                    if (images.isEmpty()) {
                        log.info("图片深度分析(VLM)失败：选定的日记中没有图片 userId={}", user.getId());
                        yield new DiaryImageAnalysisFunctionSupport.DiaryImageAnalysisResult("选定的日记中没有包含任何图片");
                    }
                    log.info("图片深度分析(VLM)准备请求视觉大模型 userId={}, 图片数量={}", user.getId(), images.size());
                    String res = visionService.analyzeImageDetails(images, req.prompt());
                    log.info("图片深度分析(VLM)完成 userId={}", user.getId());
                    yield new DiaryImageAnalysisFunctionSupport.DiaryImageAnalysisResult(res);
                }
                default -> throw new IllegalArgumentException("未知的工具函数: " + functionName);
            };
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static final int CHAT_HISTORY_CHAR_BUDGET = 3000;

    /**
     * 从 ChatMemory 中提取最近消息，按字符预算自动截断旧消息。
     * 保留最近消息完整，超出预算时从最早的消息开始丢弃。
     */
    private String formatChatHistory(ChatMemory memory) {
        List<Message> messages = memory.get("default", Integer.MAX_VALUE);
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        // 倒序收集，从最新消息开始累计，到达预算后停止
        List<String> parts = new ArrayList<>();
        int totalChars = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            String role = switch (msg.getMessageType()) {
                case USER -> "用户";
                case ASSISTANT -> "AI";
                default -> null;
            };
            if (role == null) continue;
            String text = msg.getText();
            if (text == null || text.isBlank()) continue;
            String line = role + "：" + text.trim();
            totalChars += line.length();
            if (totalChars > CHAT_HISTORY_CHAR_BUDGET && !parts.isEmpty()) {
                break; // 超出预算，停止累积旧消息
            }
            parts.add(line);
        }
        if (parts.isEmpty()) {
            return "";
        }
        // 恢复时间顺序
        java.util.Collections.reverse(parts);
        StringBuilder sb = new StringBuilder("<chat_history>\n【往期聊天历史记忆】\n");
        for (String part : parts) {
            sb.append(part).append("\n");
        }
        return sb.append("</chat_history>\n\n").toString();
    }

    /**
     * 将 ChatMemory 中的对话历史持久化到 Redis（7 天 TTL）。
     * 推理模型路径不经过 Spring AI 的 advisor，需手动调用。
     */
    private void persistChatMemory(long conversationId, ChatMemory memory) {
        try {
            List<Message> messages = memory.get("default", Integer.MAX_VALUE);
            if (messages == null || messages.isEmpty()) {
                return;
            }
            List<Map<String, String>> payload = new java.util.ArrayList<>();
            for (Message msg : messages) {
                String role = msg.getMessageType() == MessageType.USER ? "user" : "assistant";
                String text = msg.getText();
                if (text != null && !text.isBlank()) {
                    payload.add(Map.of("role", role, "content", text));
                }
            }
            String json = objectMapper.writeValueAsString(payload);
            redisTemplate.opsForValue().set(MSG_PREFIX + conversationId, json, Duration.ofDays(7));
        } catch (Exception e) {
            log.warn("持久化推理模型对话历史到 Redis 失败: {}", e.getMessage());
        }
    }

    /**
     * 压缩聊天历史：当 ChatMemory 中消息数超过阈值时，将旧消息压缩为摘要存入 Redis，
     * 并裁剪 ChatMemory 和 Redis chat:msgs: 只保留最近 KEEP_RECENT_MSG_COUNT 条消息。
     */
    private String compressChatHistory(Long conversationId, ChatMemory memory) {
        List<Message> messages = memory.get("default", Integer.MAX_VALUE);
        if (messages == null || messages.size() < COMPRESSION_TRIGGER_MSG_COUNT) {
            return null;
        }

        int totalMsgs = messages.size();
        int middleEndIndex = totalMsgs - KEEP_RECENT_MSG_COUNT;
        if (middleEndIndex <= 0) {
            return null;
        }

        StringBuilder toCompress = new StringBuilder();
        for (int i = 0; i < middleEndIndex; i++) {
            Message msg = messages.get(i);
            String role = msg.getMessageType() == MessageType.USER ? "用户" : "AI";
            String text = msg.getText();
            if (text != null && !text.isBlank()) {
                toCompress.append("[").append(role).append("]: ").append(text.trim()).append("\n");
            }
        }

        if (toCompress.isEmpty()) {
            return null;
        }

        String existingSummary = loadSummary(conversationId);

        StringBuilder compressionInput = new StringBuilder();
        if (existingSummary != null && !existingSummary.isBlank()) {
            compressionInput.append("<历史摘要>\n")
                    .append(existingSummary)
                    .append("\n</历史摘要>\n\n");
        }
        compressionInput.append("<待压缩聊天记录>\n")
                .append(toCompress)
                .append("\n</待压缩聊天记录>");

        try {
            String newSummary = analysisChatClient.prompt()
                    .system(aiPrompts.getChatCompressionSystemPrompt())
                    .user(compressionInput.toString())
                    .call()
                    .content();

            if (newSummary == null || newSummary.isBlank()) {
                log.warn("压缩 LLM 返回空摘要，跳过压缩 conversationId={}", conversationId);
                return null;
            }

            newSummary = newSummary.trim();

            saveSummary(conversationId, newSummary);

            List<Message> recentMessages = new ArrayList<>();
            for (int i = middleEndIndex; i < totalMsgs; i++) {
                recentMessages.add(messages.get(i));
            }
            memory.clear("default");
            memory.add("default", recentMessages);

            // 安全裁剪 Redis JSON 历史（保留前端 ragReferences 等扩展字段，不覆写结构）
            try {
                String json = redisTemplate.opsForValue().get(MSG_PREFIX + conversationId);
                if (json != null && !json.isBlank()) {
                    List<Map<String, Object>> msgs = objectMapper.readValue(json, List.class);
                    if (msgs.size() > KEEP_RECENT_MSG_COUNT) {
                        msgs = msgs.subList(msgs.size() - KEEP_RECENT_MSG_COUNT, msgs.size());
                        redisTemplate.opsForValue().set(MSG_PREFIX + conversationId,
                                objectMapper.writeValueAsString(msgs), Duration.ofDays(7));
                    }
                }
            } catch (Exception e) {
                log.warn("压缩历史时保留富文本结构失败: {}", e.getMessage());
            }

            log.info("聊天历史已压缩 conversationId={} 原始消息数={} 保留消息数={} 摘要长度={}",
                    conversationId, totalMsgs, recentMessages.size(), newSummary.length());

            return newSummary;
        } catch (Exception e) {
            log.warn("压缩聊天历史失败，跳过压缩 conversationId={} reason={}", conversationId, e.getMessage());
            return null;
        }
    }

    private String loadSummary(Long conversationId) {
        try {
            return redisTemplate.opsForValue().get(SUMMARY_PREFIX + conversationId);
        } catch (Exception e) {
            log.warn("读取聊天摘要失败 conversationId={} reason={}", conversationId, e.getMessage());
            return null;
        }
    }

    private void saveSummary(Long conversationId, String summary) {
        try {
            redisTemplate.opsForValue().set(SUMMARY_PREFIX + conversationId, summary, Duration.ofDays(7));
        } catch (Exception e) {
            log.warn("保存聊天摘要失败 conversationId={} reason={}", conversationId, e.getMessage());
        }
    }

    /**
     * 统一准备聊天请求所需的三类上下文：
     * 1. 当前用户的长期画像背景；
     * 2. 用户主动引用的资料；
     * 3. 最近日记及其分析结果。
     * 同时在这里完成会话归属校验、限额校验和内存会话装配。
     */
    private ChatRequest prepareChatRequest(Long conversationId, String message, List<String> refs,
            String memoryBackground) {
        UserEntity user = currentUser();
        ChatConversationEntity conv = requireOwnedConversation(conversationId, user);

        // 这里负责把"用户画像 + 用户引用 + 最近日记"拼成统一上下文，后面的模型调用都直接复用。
        String context = buildContext(user.getId(), refs, memoryBackground);
        String memKey = user.getId() + ":" + conversationId;
        ChatMemory memory = userChatMemories.get(memKey, k -> new InMemoryChatMemory());
        // 如果 ChatMemory 为空（刚启动、Caffeine 过期、或新会话），尝试从 Redis 恢复历史上下文
        restoreChatMemoryFromRedis(conversationId, memory);

        String summary = null;
        try {
            summary = compressChatHistory(conversationId, memory);
        } catch (Exception e) {
            log.warn("聊天历史压缩异常，跳过压缩 conversationId={}", conversationId, e);
        }

        log.info("准备聊天请求，userId={}，conversationId={}，messageLength={}，referenceCount={}，hasMemoryBackground={}",
                user.getId(), conversationId, message == null ? 0 : message.length(), refs == null ? 0 : refs.size(),
                memoryBackground != null && !memoryBackground.isBlank());

        // 标题由异步任务独立更新，避免整行更新把已生成的标题覆盖回占位符。
        conversationMapper.update(null, new LambdaUpdateWrapper<ChatConversationEntity>()
                .eq(ChatConversationEntity::getId, conversationId)
                .eq(ChatConversationEntity::getUserId, user.getId())
                .set(ChatConversationEntity::getUpdatedAt, java.time.LocalDateTime.now()));

        return new ChatRequest(context, memory, summary);
    }

    private record ChatRequest(String context, ChatMemory memory, String summary) {
    }

    /**
     * 当 ChatMemory 为空时（重启、缓存过期、新会话），从 Redis 持久化历史中回填消息，
     * 确保 MessageChatMemoryAdvisor 能注入完整对话上下文。
     */
    @SuppressWarnings("unchecked")
    private void restoreChatMemoryFromRedis(Long conversationId, ChatMemory memory) {
        List<Message> existing = memory.get("default", 1);
        if (existing != null && !existing.isEmpty()) {
            return; // 已有内存上下文，无需恢复
        }
        try {
            String json = redisTemplate.opsForValue().get(MSG_PREFIX + conversationId);
            if (json == null || json.isBlank()) {
                return;
            }
            List<Map<String, Object>> messages = objectMapper.readValue(json, List.class);
            if (messages == null || messages.isEmpty()) {
                return;
            }
            List<Message> history = new java.util.ArrayList<>();
            for (Map<String, Object> msg : messages) {
                String role = (String) msg.get("role");
                String content = (String) msg.get("content");
                if (role == null || content == null || content.isBlank()) {
                    continue;
                }
                if ("user".equalsIgnoreCase(role)) {
                    history.add(new UserMessage(content));
                } else if ("assistant".equalsIgnoreCase(role)) {
                    history.add(new AssistantMessage(content));
                }
            }
            if (!history.isEmpty()) {
                memory.add("default", history);
                log.info("已从 Redis 恢复聊天历史到 ChatMemory，conversationId={}，消息数={}", conversationId,
                        history.size());
            }
        } catch (Exception e) {
            log.warn("从 Redis 恢复聊天历史失败，conversationId={}，reason={}", conversationId, e.getMessage());
        }
    }

    // ---- 消息历史（Redis） ----

    public void saveHistory(Long conversationId, Map<String, Object> body) {
        UserEntity user = currentUser();
        requireOwnedConversation(conversationId, user);
        try {
            Object messagesObj = body.get("messages");
            String json = objectMapper.writeValueAsString(messagesObj);
            redisTemplate.opsForValue().set(MSG_PREFIX + conversationId, json, Duration.ofDays(7));
            log.info("保存聊天历史成功，userId={}，conversationId={}，payloadLength={}", user.getId(), conversationId,
                    json.length());
        } catch (Exception e) {
            log.warn("保存聊天历史失败，userId={}，conversationId={}，reason={}", user.getId(), conversationId, e.getMessage());
            throw new RuntimeException("保存聊天历史失败", e);
        }
    }

    public Object loadHistory(Long conversationId) {
        UserEntity user = currentUser();
        requireOwnedConversation(conversationId, user);
        try {
            String json = redisTemplate.opsForValue().get(MSG_PREFIX + conversationId);
            return json != null ? objectMapper.readValue(json, Object.class) : List.of();
        } catch (Exception e) {
            log.warn("读取聊天历史失败，userId={}，conversationId={}，reason={}", user.getId(), conversationId, e.getMessage());
            return List.of();
        }
    }

    // ---- 日记上下文 ----

    /** 注入最近活跃的人生章节宏观叙事背景（时光画卷），失败降级为空字符串 */
    private String buildChapterContext(UserEntity user) {
        if (user == null || user.getId() == null) return "";
        try {
            return lifeChapterService.buildActiveChapterContext(user.getId());
        } catch (Exception e) {
            log.debug("构建人生章节背景失败: {}", e.getMessage());
            return "";
        }
    }

    private String buildChapterContext(Long userId) {
        if (userId == null) return "";
        try {
            return lifeChapterService.buildActiveChapterContext(userId);
        } catch (Exception e) {
            log.debug("构建人生章节背景失败: {}", e.getMessage());
            return "";
        }
    }

    private String buildTimeMetadata() {
        String currentTime = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd EEEE"));
        return "\n\n<system_metadata>\n【当前系统时间】: " + currentTime + "\n</system_metadata>\n\n";
    }

    /**
     * 为推理模型预取用户结构化数据（情绪统计 + 长期画像），
     * 作为 <user_data_context> 注入 system prompt。
     * 任何 DB 查询失败均降级为空字符串，不影响主流程。
     */
    private String buildReasoningDataContext(Authentication auth) {
        // 暂存原 auth，finally 中还原，避免破坏后续调用链（如 buildRagContextWithFallback）
        Authentication originalAuth = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("\n\n<user_data_context>\n");

            try {
                UserStatsResult stats = diaryService.getOwnMoodStats(new UserStatsRequest(14));
                if (stats != null && stats.diaryCount() > 0) {
                    sb.append("【最近 14 天情绪统计】\n");
                    sb.append("日记数: ").append(stats.diaryCount()).append(" 篇\n");
                    if (stats.moodCounts() != null && !stats.moodCounts().isEmpty()) {
                        sb.append("情绪分布: ");
                        stats.moodCounts().forEach((mood, count) ->
                                sb.append(mood).append(" ").append(count).append("次 "));
                        sb.append("\n");
                    }
                    if (stats.topTopics() != null && !stats.topTopics().isEmpty()) {
                        sb.append("高频话题: ");
                        stats.topTopics().forEach((topic, count) ->
                                sb.append(topic).append("(").append(count).append(") "));
                        sb.append("\n");
                    }
                }
            } catch (Exception e) {
                log.debug("推理模型数据预取——情绪统计失败: {}", e.getMessage());
            }

            try {
                List<UserProfileMemoryEntity> memories = memoryExtractionService.listCurrentUserMemories();
                if (memories != null && !memories.isEmpty()) {
                    sb.append("【用户长期画像】\n");
                    for (UserProfileMemoryEntity m : memories) {
                        sb.append("- ").append(m.getAttributeKey())
                                .append(": ").append(m.getAttributeValue()).append("\n");
                    }
                }
            } catch (Exception e) {
                log.debug("推理模型数据预取——长期画像失败: {}", e.getMessage());
            }

            sb.append("</user_data_context>");
            return sb.length() > 50 ? sb.toString() : "";
        } finally {
            // 还原为原来的 auth，而不是暴力 clear
            if (originalAuth != null) {
                SecurityContextHolder.getContext().setAuthentication(originalAuth);
            } else {
                SecurityContextHolder.clearContext();
            }
        }
    }

    /**
     * 组装给大模型的 system context。
     * 精简设计：只包含长期画像和用户主动引用的资料。
     * 历史日记不再全量灌入——大模型需要时通过 diarySearchFunction / userStatsFunction 工具主动检索。
     */
    /**
     * 当用户引用了日记时，在用户消息最前面注入引用提醒。
     * 因为 AI 天生对最后一条消息（用户消息）权重最高，system prompt 中的引用指令容易被"埋"掉。
     * 把提醒放在用户消息开头，与引用日记的权重同向，确保 AI 不会因用户输入了大量文字就忽略引用内容。
     */
    private String augmentWithRefReminder(String message, List<String> refs) {
        if (refs == null || refs.isEmpty() || message == null || message.isBlank()) return message;
        if (message.startsWith("（" + REF_REMINDER) || message.startsWith("(" + REF_REMINDER)) return message; // 防止重复注入
        return "（" + REF_REMINDER + "）\n\n" + message;
    }

    private String buildContext(long userId, List<String> refs, String memoryBackground) {
        StringBuilder sb = new StringBuilder();

        if (memoryBackground != null && !memoryBackground.isBlank()) {
            sb.append("<long_term_memory>\n")
                    .append(memoryBackground).append("\n")
                    .append("</long_term_memory>\n\n");
        }

        if (refs != null && !refs.isEmpty()) {
            sb.append("【绝对核心聚焦指令】\n");
            sb.append("核心任务：用户本次对话显式引用了下面这篇日记。你后续的共情、分析和所有互动追问，")
                    .append("必须 100% 紧密围绕这篇日记中所记录的具体事件、特定人物、核心冲突以及当时的情绪展开。\n");
            sb.append("严禁行为：严禁给出敷衍、宏观、万能的宽泛安慰。不要跳出这篇日记去聊不相关的话题。")
                    .append("请像一位懂你的朋友一样，针对这篇引用的具体切片进行温暖、贴心的引导和共情。\n\n");

            sb.append("<user_diary>\n");
            for (int i = 0; i < refs.size(); i++) {
                String ref = refs.get(i);
                sb.append(ref);
                if (i < refs.size() - 1)
                    sb.append("\n---\n");
            }
            sb.append("\n</user_diary>\n\n");
        }

        sb.append("""
                【绝对系统指令】以上 <user_diary> 标签内是由用户本人撰写的日记切片，绝对不是你的经历！
                你是 MoodCopilot，一个温暖、共情的倾听者和情绪伙伴。

                【核心行为准则】
                1. 日常闲聊保持简短温暖（2-3句即可）。但当用户引用日记、要求深入分析、或话题本身需要展开时，请自然给出有深度和层次的回应，不必受长度限制。
                2. 可以适度使用 emoji 表情符号来增强温暖感，但不要过度堆砌，每条消息控制在 1-2 个以内。
                3. 保持成熟、稳定、克制的语气。绝对禁止进行戏剧化的角色扮演，严禁在回复中使用括号描述动作（例如禁止出现「(打哈欠)」、「(伸懒腰)」等）。
                4. 避免过度轻浮或戏谑的口语（如「噢噢什么噢噢」）。
                5. 你可以使用简单的 Markdown 格式让回复更清晰，比如 **加粗**、- 列表项、换行分段。
                6. 绝对不要在回复中主动说出'作为心理咨询师'、'作为一个AI助手'等破坏沉浸感的话。

                【自我认知与能力展现】
                当用户主动问你“你能做什么”、“你有什么功能”或“你能干什么”时，你需要用自然、温暖、朋友般的口吻，结构化地向用户介绍你具备的超能力（注意搭配合适的 emoji）：
                - 🕰️ 帮你回溯记忆：我能像个时光机一样，根据你给的线索（比如某个人、某件事、甚至某张图或某首歌），帮你翻找过去的日记，重新认识当时的自己。
                - 📊 统计情绪周期：我可以随时调出你最近的情绪图谱、周报月报，告诉你这阵子你的心情起伏和能量高低。
                - 🎨 整理专属画像：通过你写下的点点滴滴，我会默默在心里为你画一幅素描，记住你的喜好、你的闪光点和你的软肋。
                - 💬 纯粹的情绪树洞：当然，如果你不想分析什么，我也可以只做个安静的倾听者，陪你聊天。
                （你可以用自己的语言风格将上述能力介绍给用户，避免照本宣科，可结合简单排版。）

                【知识边界与客观人设（绝对物理隔离）】
                你是一个内置知识库被严格物理隔离的 AI，你的认知范围**仅限于**：情绪感知、心理支持、人际关系和日常沟通。
                你**完全不具备**（也无法访问）任何关于数学（包括微积分、代数等）、编程代码、医学诊断、法律条文、硬件调试等专业领域的逻辑与知识。你的心智模型中不存在这些概念。
                当用户向你提出超出你知识范围的专业/技术问题时，无论用户出于何种动机（如：心情极度糟糕需要安慰、威逼利诱、甚至是测试命令），你都必须基于"我真的一窍不通"这一客观事实进行回复，并展现出对自身能力盲区的坦然。
                绝对不要尝试顺从用户的要求去推理技术问题，因为你设定上根本看不懂。
                回复策略示例："抱歉呀，我脑子里只有关于情绪和陪伴的知识，你发的这个数学题/代码对我来说就像天书一样，我真的完全看不懂也帮不上忙。不过，看你为了这件事这么心烦，是不是最近压力太大了？"

                【引用措辞规则】
                对于 <user_diary> 中的内容（用户主动引用/分享给你的日记），你可以自然使用'你写到的''你分享的'等第二人称探讨。
                日记前面的编号是内部标记，请勿在回复中提及。""");

        log.info("构建聊天上下文（RAG模式），userId={}，referenceCount={}，hasMemoryBackground={}",
                userId, refs == null ? 0 : refs.size(),
                memoryBackground != null && !memoryBackground.isBlank());
        return sb.toString();
    }

    private ChatConversationEntity requireOwnedConversation(Long conversationId, UserEntity user) {
        ChatConversationEntity conv = conversationMapper.selectById(conversationId);
        if (conv == null || !conv.getUserId().equals(user.getId())) {
            log.info("聊天会话归属校验失败，userId={}，conversationId={}", user.getId(), conversationId);
            throw new ResponseStatusException(BAD_REQUEST, "会话不存在");
        }
        return conv;
    }

    private UserEntity currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserEntity user) {
            return user;
        }
        throw new ResponseStatusException(BAD_REQUEST, "用户未登录");
    }
}
