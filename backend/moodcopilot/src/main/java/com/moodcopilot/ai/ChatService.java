package com.moodcopilot.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
    private static final int COMPRESSION_TRIGGER_MSG_COUNT = 20;
    private static final int KEEP_RECENT_MSG_COUNT = 10;

    private static final String COMPRESSION_SYSTEM_PROMPT = """
            你是对话摘要助手。请将以下聊天记录压缩为简洁摘要，保留关键信息以便后续对话延续。

            规则：
            1. 保留用户分享的重要事实：生活事件、情绪变化、决定、偏好、人际关系动态
            2. 保留对话的核心主题和情感走向
            3. 保留你（AI）给出的重要建议或分析结论
            4. 忽略日常寒暄和纯闲聊内容
            5. 如果下方提供了"历史摘要"，请将新旧信息自然合并为一份连贯的摘要
            6. 输出 150-400 字的简洁中文摘要，不要评价，不要解释
            7. 只输出纯文本摘要，不要 markdown 格式，不要 JSON
            """;

    private static final String AGENT_TOOLS_PROMPT = """
            \n【工具调用的流式规范 —— 最高优先级，违反即为错误】

            当你判断需要调用工具获取数据时，必须遵守以下严格规则：
            1. 绝对禁止在调用工具前输出任何前置文字！不能说"我帮你查一下"、"让我看看你的数据"、"好的我查查"、"稍等一下我查一下"等任何过渡语或安抚语。
            2. 你的第一轮响应必须是纯粹的 function call，content 字段留空。不要做任何铺垫。
            3. 只有在收到工具返回的实际数据后，你才能开始写回复。此时直接基于数据自然共情，无需说"根据工具返回的结果"这类话。
            4. 常见违规场景警示：
               - 用户问"我哪天发了个XX图片" → 绝对不要先说"我帮你查一下"或"系统好像没有图片记录"再调用工具。直接调用 diarySearchFunction（content 留空）！
               - 任何"查询/回顾/看看"类意图都应直接调用工具，而非先铺垫再查。违反此条必定导致你后半段引用数据但前半段否认的严重割裂。
            5. 示例 ——
               [错误] 用户问"我最近状态怎么样"，你回复："我理解你的心情，让我帮你查一下最近的数据..." [然后才调用工具]
               [正确] 用户问"我最近状态怎么样"，你直接调用 userStatsFunction + diarySearchFunction（content 留空），拿到数据后回复："我看到这周有3篇日记，焦虑的情绪出现了两次，是工作上有什么事吗？"

            \n【你的系统能力（Agent Tools）】
            你配备了后台数据查询工具（如 userStats, diarySearch, reportSnapshot, memoryQuery）。
            当用户在对话中提到"看看我的报告"、"最近的数据总结"、"查询过去的日记"，或者需要结合历史表现聊天时，你**必须主动调用工具**获取用户的真实数据后再进行回复。
            严禁回答"我看不到你的具体报告"或"请你把报告发给我"。作为 MoodCopilot，你完全有权限并且应当自己去后台查阅这些统计数据！

            【图片/音乐查询认知 —— 极高优先级，违反即为错误】
            重要事实：
            - 用户过去上传的图片已经通过视觉模型（VLM）转换为了文字描述，存储在了数据库的向量索引中。
            - 用户分享的音乐（网易云链接）已被解析为文字元数据（歌名、歌手、歌词），同样存储在了向量索引中，可作为独立条目被语义检索。

            当你收到用户关于图片/照片/截图 或 音乐/歌曲/歌词 的查询（如"我哪天发了个电脑的图片"、"上次分享的那首周杰伦的歌"、"那首关于下雨天的歌"等）时：
            1. 你必须直接调用 diarySearchFunction 或 memoryQueryFunction 去查询这些文本化的图片描述或音乐元数据！它们以自然语言形式存储，可以通过语义检索命中。
            2. 绝对禁止说出"我无法查看图片"、"我没有听歌的能力"、"系统不支持图片/音乐搜索"、"没有记录"、"系统里好像没有"等否定性陈述。因为图片描述和音乐元数据作为文本是存在的且可被检索的，你说的"没有"是完全错误的。
            3. 绝对禁止在调用工具前输出任何前置文字。面对图片/音乐查询，直接调用工具（content 留空），等拿到描述文本后再自然地告诉用户。
            4. 如果工具返回了图片描述文本（如"一张深夜电脑屏幕的图片"）或音乐元数据（如"歌曲：晴天 歌手：周杰伦"），直接引用它，无需对用户说"这是一段文字描述"——把它当作你亲眼看到/亲耳听到的内容来理解。

            【隐式实体查询 —— 高优先级】
            当用户在对话中提到某个具体的品牌、商品、地点或看似随意的名词（例如“爷爷不泡茶”、“某某餐厅”、“那个杯子”）时，即使他们没有明确说“帮我查一下日记”，你也**必须主动调用 diarySearchFunction** 以该名词为 keyword 去检索！
            因为用户很可能在最近的日记里记录过与此相关的心愿或图片。主动检索能让你瞬间捕捉到用户的上下文。只有在搜索不到时，你再把它当作一般性话题回答。

            【并行工具调用 — 极其重要】
            当你需要分析用户历史状态时，可以**同时调用多个工具**来准备数据，而不是一个一个地串行查：
            - 用户问"我最近状态怎么样" → 同时调用 userStatsFunction + diarySearchFunction + memoryQueryFunction
            - 用户问"帮我分析一下这周" → 同时调用 reportSnapshotFunction + userStatsFunction
            一次请求中并行调用所有相关工具，能大幅减少用户等待时间。系统已检索到的语义片段会和你的工具查询结果互补，你只需综合回答即可。

            【记忆与当前上下文的优先级铁律 —— 极其重要】
            在多轮对话中，你必须**绝对优先结合紧邻的 <chat_history>（用户刚才和你聊的具体事件）**来回答用户的追问！
            由系统检索拉取的历史记录（<rag_retrieved_context>）或长期画像仅作为次要的性格补充。如果检索到的旧记录（例如旧日记里的人物、事件）与当前 <chat_history> 中正在讨论的话题明显脱节，请**果断忽略**那些旧记录，保持当前对话的逻辑连贯性！绝对不允许用毫不相干的过往记忆去强行回答用户的当前提问！

            【工具检索结果的话术规范 — 极其重要】
            如果你通过调用工具拿到了用户过去的数据（报告、统计、历史日记等），记住：这些数据是你自己查出来的，不是用户在这一轮对话里主动告诉你的！
            请严格区分两种情况：
            1. 用户在本轮对话中显式引用/粘贴了某篇日记给你看 → 可以用"你写到的"等表达。
            2. 你通过 Function Calling 或 RAG 向量检索从后台拿到的数据 → **绝对不要**说"你分享的""正如你提到的""你刚才说""你表示"。"分享"一词暗示用户主动递交，而系统检索的数据（日记、聊天记录、画像）并非用户在本轮对话中递交的。必须使用明确表示系统检索的说法，例如：
               - "我帮你查了一下你过去的数据……"
               - "根据你的历史记录显示……"
               - "从你之前的日记/报告中我看到……"
               - "系统检索到你曾经记录过……"
            这样做是为了让用户清楚地知道：有些内容是你主动帮他们查的，而不会产生"我什么时候说过这个？"的困惑。

            【时间与工具检索规范 — 极其重要】
            1. 当你需要根据用户提到的相对时间（如"昨天"、"上周"）调用 diarySearch 等工具时，你必须先参考上方的 <system_metadata> 中的【当前系统时间】，将其在心里计算成绝对日期（如 yyyy-MM-dd），然后再将绝对日期作为参数传入工具！
            2. 禁忌：<system_metadata> 中的时间仅供你作为底层计算基准。在最终回复用户的文字中，**绝对不要**主动提及或重复当前的日期和星期（例如绝对不要说"今天是2024年X月X日"或"现在是星期几"），除非用户明确问你今天几号。请始终保持像真人朋友一样自然、共情的对话风格，不要像个报时的机器人。
            """;

    private final ChatClient chatChatClient;
    private final ChatClient analysisChatClient;
    private final ChatConversationMapper conversationMapper;
    private final ChatIntentRouter chatIntentRouter;
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

    public ChatService(ChatClient chatChatClient,
            ChatClient analysisChatClient,
            ChatConversationMapper conversationMapper,
            ChatIntentRouter chatIntentRouter,
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
            VisionService visionService) {
        this.chatChatClient = chatChatClient;
        this.analysisChatClient = analysisChatClient;
        this.conversationMapper = conversationMapper;
        this.chatIntentRouter = chatIntentRouter;
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
    }

    // ---- 会话管理 ----

    public List<ChatConversationEntity> listConversations() {
        UserEntity user = currentUser();
        return conversationMapper.selectList(
                new LambdaQueryWrapper<ChatConversationEntity>()
                        .eq(ChatConversationEntity::getUserId, user.getId())
                        .orderByDesc(ChatConversationEntity::getUpdatedAt));
    }

    public ChatConversationEntity createConversation(String title) {
        UserEntity user = currentUser();
        ChatConversationEntity conv = new ChatConversationEntity();
        conv.setUserId(user.getId());
        conv.setTitle(title != null && !title.isBlank() ? title : "新对话");
        conv.setCreatedAt(java.time.LocalDateTime.now());
        conv.setUpdatedAt(java.time.LocalDateTime.now());
        conversationMapper.insert(conv);
        return conv;
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

        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.readValue(cached, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, String>>>() {});
            }
        } catch (Exception e) {
            log.warn("读取 welcome topics 缓存失败", e);
        }

        // 兜底返回默认
        return List.of(
                Map.of("icon", "📊", "text", "分析我最近三天的情绪波动"),
                Map.of("icon", "💡", "text", "帮我回顾我最近开心的事情"),
                Map.of("icon", "🌿", "text", "推荐一些适合解压的音乐与方法"),
                Map.of("icon", "💬", "text", "今天有些累，陪我随便聊聊吧")
        );
    }

    private void generateAndCacheWelcomeTopics(Long userId, String memoryBackground) {
        String cacheKey = "chat:welcome_topics:" + userId;
        String systemPrompt = """
            你是一个懂心理学的情绪树洞助手。请根据用户的画像和最近状态，生成 4 个推荐的聊天开场白话题（每个话题长度在10-20字左右），供用户点击快速开始聊天。
            请确保话题贴合用户的状态、兴趣或最近可能有的困惑，或者提供一些温暖的日常问候。
            
            必须严格返回合法的 JSON 数组，格式如下：
            [
              {"icon": "🌟", "text": "分析我最近三天的情绪波动"},
              {"icon": "💡", "text": "帮我回顾我最近开心的事情"},
              {"icon": "🌿", "text": "推荐一些适合解压的音乐与方法"},
              {"icon": "💬", "text": "今天有些累，陪我随便聊聊吧"}
            ]
            
            用户背景画像：
            """ + memoryBackground;

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

    public ChatStreamContext chat(Long conversationId, String message, List<String> refs, String memoryBackground) {
        // 流式接口：先统一装配上下文，再决定走普通模型还是思考模型。
        ChatRequest request = prepareChatRequest(conversationId, message, refs, memoryBackground);
        // 捕获当前 SecurityContext，通过 Reactor Context 传递给 Function Calling 的异步回调线程
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserEntity user = currentUser();
        long uid = ((UserEntity) auth.getPrincipal()).getId();
        String ragCtx = ""; // 已迁移为 Agentic RAG，不再强制前置全量检索

        if (shouldUseReasoning(conversationId, message, refs, memoryBackground)) {
            boolean useReasoning = false;
            try {
                rateLimitService.tryAcquire(user, RateLimitService.AiApiType.REASONING);
                useReasoning = true;
            } catch (RateLimitException e) {
                log.info("推理额度不足，降级到普通聊天 userId={}", user.getId());
            }
            if (useReasoning) {
                log.info("聊天路由结果：reasoning（流式），conversationId={}，messageLength={}", conversationId,
                        message == null ? 0 : message.length());
                userGrowthService.addExp(user.getId(), ExpAction.CHAT, null);
                return new ChatStreamContext(ragCtx, callReasoningModelStream(request, message, auth, conversationId, ragCtx));
            }
        }

        log.info("聊天路由结果：normal，conversationId={}，messageLength={}", conversationId,
                message == null ? 0 : message.length());
        rateLimitService.tryAcquire(user, RateLimitService.AiApiType.CHAT);
        userGrowthService.addExp(user.getId(), ExpAction.CHAT, null);

        Sinks.Many<String> sseSink = Sinks.many().unicast().onBackpressureBuffer();

        Flux<String> stream = chatChatClient.prompt()
                .user(message)
                .system(s -> {
                    StringBuilder sys = new StringBuilder();
                    sys.append(AGENT_TOOLS_PROMPT).append("\n\n");
                    if (request.context() != null && !request.context().isBlank()) {
                        sys.append(request.context()).append("\n\n");
                    }
                    if (request.summary() != null && !request.summary().isBlank()) {
                        sys.append("<conversation_summary>\n")
                           .append(request.summary())
                           .append("\n</conversation_summary>\n\n");
                    }
                    sys.append(ragCtx).append("\n").append(buildTimeMetadata());
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

    public String reply(Long conversationId, String message, List<String> refs, String memoryBackground) {
        // 非流式接口：移动端/公网优先走这里，减少 SSE 连接不稳定的影响。
        ChatRequest request = prepareChatRequest(conversationId, message, refs, memoryBackground);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserEntity user = currentUser();
        long uid = ((UserEntity) auth.getPrincipal()).getId();
        String ragCtx = ""; // 已迁移为 Agentic RAG，不再强制前置全量检索

        if (shouldUseReasoning(conversationId, message, refs, memoryBackground)) {
            boolean useReasoning = false;
            try {
                rateLimitService.tryAcquire(user, RateLimitService.AiApiType.REASONING);
                useReasoning = true;
            } catch (RateLimitException e) {
                log.info("推理额度不足，降级到普通聊天 userId={}", user.getId());
            }
            if (useReasoning) {
                log.info("非流式聊天路由结果：reasoning，conversationId={}，messageLength={}", conversationId,
                        message == null ? 0 : message.length());
                userGrowthService.addExp(user.getId(), ExpAction.CHAT, null);
                return callReasoningModel(request, message, auth, conversationId, ragCtx);
            }
        }

        log.info("非流式聊天路由结果：normal，conversationId={}，messageLength={}", conversationId,
                message == null ? 0 : message.length());
        rateLimitService.tryAcquire(user, RateLimitService.AiApiType.CHAT);
        userGrowthService.addExp(user.getId(), ExpAction.CHAT, null);

        String result = chatChatClient.prompt()
                .user(message)
                .system(s -> {
                    StringBuilder sys = new StringBuilder();
                    sys.append(AGENT_TOOLS_PROMPT).append("\n\n");
                    if (request.context() != null && !request.context().isBlank()) {
                        sys.append(request.context()).append("\n\n");
                    }
                    if (request.summary() != null && !request.summary().isBlank()) {
                        sys.append("<conversation_summary>\n")
                           .append(request.summary())
                           .append("\n</conversation_summary>\n\n");
                    }
                    sys.append(ragCtx).append("\n").append(buildTimeMetadata());
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



    private boolean shouldUseReasoning(Long conversationId, String message, List<String> refs,
            String memoryBackground) {
        return chatIntentRouter.shouldUseReasoning(message, refs, memoryBackground, conversationId);
    }

    private List<Map<String, Object>> buildMessagesForReasoner(ChatRequest request, String message, Authentication auth, String ragCtx) {
        List<Map<String, Object>> msgs = new ArrayList<>();
        StringBuilder sys = new StringBuilder();
        sys.append(AGENT_TOOLS_PROMPT).append("\n\n");
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
        sys.append(buildReasoningDataContext(auth)).append("\n").append(buildTimeMetadata());
        
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
                        "根据实体关键词，从知识图谱中查询因果/情绪归因关系三元组。keyword 是要搜索的实体关键词（如'工作'、'失眠'），limit 可选，默认 20，最大 50。返回三元组列表（headEntity relation tailEntity）。适合回答「什么导致了什么」、「为什么」这类因果追溯问题。",
                        new LinkedHashMap<>() {{
                            put("keyword", Map.of("type", "string", "description", "实体关键词"));
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
                                assistantMsg.put("content", null);
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
                                    "snippet", d.snippet() != null ? d.snippet() : "",
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
                                    "snippet", g.content() != null ? g.content() : "",
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
                                "snippet", dir.analysisResult(),
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
                        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.moodcopilot.entity.DiaryKnowledgeGraphEntity>()
                                .eq(com.moodcopilot.entity.DiaryKnowledgeGraphEntity::getUserId, userId)
                                .and(w -> w
                                        .like(com.moodcopilot.entity.DiaryKnowledgeGraphEntity::getHeadEntity, keyword)
                                        .or()
                                        .like(com.moodcopilot.entity.DiaryKnowledgeGraphEntity::getRelation, keyword)
                                        .or()
                                        .like(com.moodcopilot.entity.DiaryKnowledgeGraphEntity::getTailEntity, keyword))
                                .orderByDesc(com.moodcopilot.entity.DiaryKnowledgeGraphEntity::getCreatedAt)
                                .last("LIMIT " + limit);
                        var triples = diaryKnowledgeGraphMapper.selectList(wrapper);
                        for (var t : triples) {
                            items.add(new GraphSearchResult.GraphItem(
                                    t.getHeadEntity() + " " + t.getRelation() + " " + t.getTailEntity(),
                                    t.getCreatedAt() != null ? t.getCreatedAt().toString() : null,
                                    t.getDiaryId()));
                        }
                    }
                    yield new GraphSearchResult(items.size(), items,
                            items.isEmpty() ? "未找到与 '" + keyword + "' 相关的图谱三元组" : "已返回图谱三元组");
                }
                case "diaryImageAnalysisFunction" -> {
                    var req = objectMapper.readValue(argumentsJson, DiaryImageAnalysisRequest.class);
                    UserEntity user = (UserEntity) auth.getPrincipal();
                    log.info("触发图片深度分析(VLM)工具 userId={}, diaryIds={}, prompt={}", user.getId(), req.diaryIds(), req.prompt());
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
                    .system(COMPRESSION_SYSTEM_PROMPT)
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

        if ("新对话".equals(conv.getTitle()) && message != null && !message.isBlank()) {
            String title = message.length() > 20 ? message.substring(0, 20) : message;
            conv.setTitle(title);
        }
        conv.setUpdatedAt(java.time.LocalDateTime.now());
        conversationMapper.updateById(conv);

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
            String json = objectMapper.writeValueAsString(body.get("messages"));
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
            // log.info("读取聊天历史，userId={}，conversationId={}，hit={}", user.getId(),
            // conversationId, json != null);
            return json != null ? objectMapper.readValue(json, Object.class) : List.of();
        } catch (Exception e) {
            log.warn("读取聊天历史失败，userId={}，conversationId={}，reason={}", user.getId(), conversationId, e.getMessage());
            return List.of();
        }
    }

    // ---- 日记上下文 ----

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
