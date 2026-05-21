package com.moodcopilot.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.*;
import com.moodcopilot.entity.UserProfileMemoryEntity;
import com.moodcopilot.mapper.ChatConversationMapper;
import com.moodcopilot.diary.DiaryService;
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

import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.time.Duration;
import java.util.ArrayList;
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
            AiAnalysisService aiAnalysisService) {
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

    public Flux<String> chat(Long conversationId, String message, List<String> refs, String memoryBackground) {
        // 流式接口：先统一装配上下文，再决定走普通模型还是思考模型。
        ChatRequest request = prepareChatRequest(conversationId, message, refs, memoryBackground);
        // 捕获当前 SecurityContext，通过 Reactor Context 传递给 Function Calling 的异步回调线程
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserEntity user = currentUser();
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
                return callReasoningModelStream(request, message, auth, conversationId);
            }
        }

        log.info("聊天路由结果：normal，conversationId={}，messageLength={}", conversationId,
                message == null ? 0 : message.length());
        rateLimitService.tryAcquire(user, RateLimitService.AiApiType.CHAT);
        userGrowthService.addExp(user.getId(), ExpAction.CHAT, null);

        long uid = ((UserEntity) auth.getPrincipal()).getId();
        String ragCtx = buildRagContextWithFallback(uid, message, request.memory(), 3, RagMemoryService.SOURCE_DIARY, RagMemoryService.SOURCE_PROFILE, RagMemoryService.SOURCE_MUSIC, RagMemoryService.SOURCE_IMAGE);
        return chatChatClient.prompt()
                .user(message)
                .system(s -> {
                    StringBuilder sys = new StringBuilder();
                    if (request.summary() != null && !request.summary().isBlank()) {
                        sys.append("<conversation_summary>\n")
                           .append(request.summary())
                           .append("\n</conversation_summary>\n\n");
                    }
                    sys.append(request.context()).append(buildTimeMetadata()).append(AGENT_TOOLS_PROMPT).append(ragCtx);
                    s.text(sys.toString());
                })
                .advisors(new MessageChatMemoryAdvisor(request.memory()))
                .functions(
                        DiarySearchFunctionSupport.NAME,
                        UserStatsFunctionSupport.NAME,
                        ReportSnapshotFunctionSupport.NAME,
                        MemoryQueryFunctionSupport.NAME)
                .toolContext(Map.of("auth", auth))
                .stream()
                .content();
    }

    public String reply(Long conversationId, String message, List<String> refs, String memoryBackground) {
        // 非流式接口：移动端/公网优先走这里，减少 SSE 连接不稳定的影响。
        ChatRequest request = prepareChatRequest(conversationId, message, refs, memoryBackground);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserEntity user = currentUser();
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
                return callReasoningModel(request, message, auth, conversationId);
            }
        }

        log.info("非流式聊天路由结果：normal，conversationId={}，messageLength={}", conversationId,
                message == null ? 0 : message.length());
        rateLimitService.tryAcquire(user, RateLimitService.AiApiType.CHAT);
        userGrowthService.addExp(user.getId(), ExpAction.CHAT, null);

        long uid = ((UserEntity) auth.getPrincipal()).getId();
        String ragCtx = buildRagContextWithFallback(uid, message, request.memory(), 3, RagMemoryService.SOURCE_DIARY, RagMemoryService.SOURCE_PROFILE, RagMemoryService.SOURCE_MUSIC, RagMemoryService.SOURCE_IMAGE);
        String result = chatChatClient.prompt()
                .user(message)
                .system(s -> {
                    StringBuilder sys = new StringBuilder();
                    if (request.summary() != null && !request.summary().isBlank()) {
                        sys.append("<conversation_summary>\n")
                           .append(request.summary())
                           .append("\n</conversation_summary>\n\n");
                    }
                    sys.append(request.context()).append(buildTimeMetadata()).append(AGENT_TOOLS_PROMPT).append(ragCtx);
                    s.text(sys.toString());
                })
                .advisors(new MessageChatMemoryAdvisor(request.memory()))
                .functions(
                        DiarySearchFunctionSupport.NAME,
                        UserStatsFunctionSupport.NAME,
                        ReportSnapshotFunctionSupport.NAME,
                        MemoryQueryFunctionSupport.NAME)
                .toolContext(Map.of("auth", auth))
                .call()
                .content();
                return result;
    }

    /**
     * RAG 语义搜索，query 经由 HyDE 重写（结合长期画像 + 最近对话历史）
     * 以提升多轮对话中向量检索的语境感知能力。
     */
    private String buildRagContextWithFallback(long userId, String message, ChatMemory memory, int topK, String... sourceTypes) {
        String memoryBg = memoryExtractionService.buildUserMemoryPrompt();
        String recentHistory = extractRecentChatHistory(memory);
        String searchQuery = aiAnalysisService.rewriteQueryForSearch(message, memoryBg, recentHistory);

        // HyDE 重写日志：直观对比原文与改写结果
        if (!searchQuery.equals(message)) {
            log.info("RAG HyDE 重写 userId={} orig=\"{}\" rewritten=\"{}\" origLen={} rewrittenLen={}",
                    userId, message, searchQuery, message.length(), searchQuery.length());
        } else {
            log.info("RAG HyDE 未重写（改写失败/被旁路），使用原始查询 userId={} query=\"{}\"",
                    userId, message);
        }

        // 从原始用户消息中提取时间表达式，用于向量搜索的时间范围过滤
        var timeRangeOpt = TimeExpressionParser.parse(message);
        TimeExpressionParser.TimeRange timeRange = timeRangeOpt.orElse(null);
        if (timeRange != null) {
            log.info("RAG 检测到时间词 userId={} origMsg=\"{}\" timeRange=[{} ~ {}]",
                    userId, message,
                    TimeExpressionParser.formatDateTime(timeRange.fromTimestamp()),
                    TimeExpressionParser.formatDateTime(timeRange.toTimestamp()));
        }

        String ragCtx = ragMemoryService.buildRagContext(userId, searchQuery, topK, timeRange, sourceTypes);
        if (ragCtx.isBlank()) {
            if (timeRange != null) {
                log.info("RAG 时间过滤后零结果，将降级到 diarySearch 工具 userId={} timeRange=[{} ~ {}]",
                        userId,
                        TimeExpressionParser.formatDateTime(timeRange.fromTimestamp()),
                        TimeExpressionParser.formatDateTime(timeRange.toTimestamp()));
            } else {
                log.info("RAG 无结果，将依赖模型的 diarySearchFunction 工具主动查询 userId={}", userId);
            }
        }
        return ragCtx;
    }

    /** 提取最近 3 轮对话历史，格式化为纯文本供 HyDE 重写使用。 */
    private String extractRecentChatHistory(ChatMemory memory) {
        List<org.springframework.ai.chat.messages.Message> messages = memory.get("default", Integer.MAX_VALUE);
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        int start = Math.max(0, messages.size() - 6); // 最近 3 轮（用户+AI 各 3 条）
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < messages.size(); i++) {
            var msg = messages.get(i);
            String role = switch (msg.getMessageType()) {
                case USER -> "用户";
                case ASSISTANT -> "MoodCopilot";
                default -> null;
            };
            if (role == null) continue;
            String text = msg.getText();
            if (text == null || text.isBlank()) continue;
            sb.append("[").append(role).append("]: ").append(text.trim()).append("\n");
        }
        return sb.toString();
    }

    private boolean shouldUseReasoning(Long conversationId, String message, List<String> refs,
            String memoryBackground) {
        return chatIntentRouter.shouldUseReasoning(message, refs, memoryBackground, conversationId);
    }

    private String callReasoningModel(ChatRequest request, String message, Authentication auth, long conversationId) {
        long userId = ((UserEntity) auth.getPrincipal()).getId();
        try {
            String history = formatChatHistory(request.memory());
            String summaryBlock = (request.summary() != null && !request.summary().isBlank())
                    ? "\n\n<conversation_summary>\n" + request.summary() + "\n</conversation_summary>"
                    : "";
            String enhancedContext = request.context() + buildTimeMetadata()
                    + buildReasoningDataContext(auth)
                    + summaryBlock
                    + buildRagContextWithFallback(userId, message, request.memory(), 5, RagMemoryService.SOURCE_DIARY, RagMemoryService.SOURCE_PROFILE, RagMemoryService.SOURCE_MUSIC, RagMemoryService.SOURCE_IMAGE);

            String userMessage;
            if (!history.isEmpty()) {
                userMessage = history + "\n" + "【用户当前消息】\n" + message;
            } else {
                userMessage = message;
            }

            log.info("调用思考模型分支，contextLength={}，historyLength={}，messageLength={}",
                    request.context().length(), history.length(),
                    message == null ? 0 : message.length());
            String response = reasoningClient.generate(enhancedContext, userMessage);

            request.memory().add("default", List.of(
                    new UserMessage(message),
                    new AssistantMessage(response)));
            persistChatMemory(conversationId, request.memory());

            return response;
        } catch (Exception e) {
            log.warn("reasoning model failed in stream path, fallback to chat model: {}", e.getMessage());
            log.info("思考模型失败后回退到普通模型，messageLength={}", message == null ? 0 : message.length());
            String fallback = chatChatClient.prompt()
                    .user(message)
                    .system(s -> {
                        StringBuilder sys = new StringBuilder();
                        if (request.summary() != null && !request.summary().isBlank()) {
                            sys.append("<conversation_summary>\n")
                               .append(request.summary())
                               .append("\n</conversation_summary>\n\n");
                        }
                        sys.append(request.context()).append(buildTimeMetadata()).append(AGENT_TOOLS_PROMPT);
                        s.text(sys.toString());
                    })
                    .advisors(new MessageChatMemoryAdvisor(request.memory()))
                    .functions(
                            DiarySearchFunctionSupport.NAME,
                            UserStatsFunctionSupport.NAME,
                            ReportSnapshotFunctionSupport.NAME,
                            MemoryQueryFunctionSupport.NAME)
                    .toolContext(Map.of("auth", auth))
                    .call()
                    .content();
            return fallback;
        }
    }

    private Flux<String> callReasoningModelStream(ChatRequest request, String message, Authentication auth,
            long conversationId) {
        long userId = ((UserEntity) auth.getPrincipal()).getId();
        try {
            String history = formatChatHistory(request.memory());
            String summaryBlock = (request.summary() != null && !request.summary().isBlank())
                    ? "\n\n<conversation_summary>\n" + request.summary() + "\n</conversation_summary>"
                    : "";
            String enhancedContext = request.context() + buildTimeMetadata()
                    + buildReasoningDataContext(auth)
                    + summaryBlock
                    + buildRagContextWithFallback(userId, message, request.memory(), 5, RagMemoryService.SOURCE_DIARY, RagMemoryService.SOURCE_PROFILE, RagMemoryService.SOURCE_MUSIC, RagMemoryService.SOURCE_IMAGE);

            String userMessage;
            if (!history.isEmpty()) {
                userMessage = history + "\n" + "【用户当前消息】\n" + message;
            } else {
                userMessage = message;
            }

            log.info("调用思考模型分支（流式），contextLength={}，historyLength={}，messageLength={}",
                    request.context().length(), history.length(),
                    message == null ? 0 : message.length());

            StringBuilder fullResponse = new StringBuilder();
            return reasoningClient.generateStream(enhancedContext, userMessage)
                    .doOnNext(fullResponse::append)
                    .doOnComplete(() -> {
                        request.memory().add("default", List.of(
                                new UserMessage(message),
                                new AssistantMessage(fullResponse.toString())));
                        persistChatMemory(conversationId, request.memory());
                                })
                    .onErrorResume(e -> {
                        log.warn("思考模型流式调用失败，回退到普通模型: {}", e.getMessage());
                        return chatChatClient.prompt()
                                .user(message)
                                .system(s -> {
                        StringBuilder sys = new StringBuilder();
                        if (request.summary() != null && !request.summary().isBlank()) {
                            sys.append("<conversation_summary>\n")
                               .append(request.summary())
                               .append("\n</conversation_summary>\n\n");
                        }
                        sys.append(request.context()).append(buildTimeMetadata()).append(AGENT_TOOLS_PROMPT);
                        s.text(sys.toString());
                    })
                                .advisors(new MessageChatMemoryAdvisor(request.memory()))
                                .functions(
                                        DiarySearchFunctionSupport.NAME,
                                        UserStatsFunctionSupport.NAME,
                                        ReportSnapshotFunctionSupport.NAME,
                                        MemoryQueryFunctionSupport.NAME)
                                .toolContext(Map.of("auth", auth))
                                .stream()
                                .content();
                    });
        } catch (Exception e) {
            log.warn("reasoning model failed in stream path, fallback to chat model: {}", e.getMessage());
            log.info("思考模型失败后回退到普通模型（流式），messageLength={}", message == null ? 0 : message.length());
            return chatChatClient.prompt()
                    .user(message)
                    .system(s -> {
                        StringBuilder sys = new StringBuilder();
                        if (request.summary() != null && !request.summary().isBlank()) {
                            sys.append("<conversation_summary>\n")
                               .append(request.summary())
                               .append("\n</conversation_summary>\n\n");
                        }
                        sys.append(request.context()).append(buildTimeMetadata()).append(AGENT_TOOLS_PROMPT);
                        s.text(sys.toString());
                    })
                    .advisors(new MessageChatMemoryAdvisor(request.memory()))
                    .functions(
                            DiarySearchFunctionSupport.NAME,
                            UserStatsFunctionSupport.NAME,
                            ReportSnapshotFunctionSupport.NAME,
                            MemoryQueryFunctionSupport.NAME)
                    .toolContext(Map.of("auth", auth))
                    .stream()
                    .content();
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

            persistChatMemory(conversationId, memory);

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
