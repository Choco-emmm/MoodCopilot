package com.moodcopilot.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.*;
import com.moodcopilot.mapper.ChatConversationMapper;
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
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final String MSG_PREFIX = "chat:msgs:";

    private static final String AGENT_TOOLS_PROMPT = """
            \n【你的系统能力（Agent Tools）】
            你配备了后台数据查询工具（如 userStats, diarySearch）。
            当用户在对话中提到"看看我的报告"、"最近的数据总结"、"查询过去的日记"，或者需要结合历史表现聊天时，你**必须主动调用工具**获取用户的真实数据后再进行回复。
            严禁回答"我看不到你的具体报告"或"请你把报告发给我"。作为 MoodCopilot，你完全有权限并且应当自己去后台查阅这些统计数据！

            【工具检索结果的话术规范 — 极其重要】
            如果你通过调用工具拿到了用户过去的数据（报告、统计、历史日记等），记住：这些数据是你自己查出来的，不是用户在这一轮对话里主动告诉你的！
            请严格区分两种情况：
            1. 用户在本轮对话中显式引用/粘贴了某篇日记给你看 → 可以用"你写到的""你分享的这段日记里"等表达。
            2. 你通过 Function Calling 自己去后台搜出来的数据 → **绝对不要**说"正如你提到的""你刚才说""你表示"。必须使用明确表示系统检索的说法，例如：
               - "我帮你查了一下你过去的数据……"
               - "根据你的历史记录显示……"
               - "从你之前的日记/报告中我看到……"
               - "系统检索到你曾经记录过……"
            这样做是为了让用户清楚地知道：有些内容是你主动帮他们查的，而不会产生"我什么时候说过这个？"的困惑。
            """;

    private final ChatClient chatChatClient;
    private final ChatConversationMapper conversationMapper;
    private final ChatIntentRouter chatIntentRouter;
    private final DeepSeekReasoningClient reasoningClient;
    private final Cache<String, ChatMemory> userChatMemories;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RateLimitService rateLimitService;

    public ChatService(ChatClient chatChatClient,
            ChatConversationMapper conversationMapper,
            ChatIntentRouter chatIntentRouter,
            DeepSeekReasoningClient reasoningClient,
            Cache<String, ChatMemory> userChatMemories,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RateLimitService rateLimitService) {
        this.chatChatClient = chatChatClient;
        this.conversationMapper = conversationMapper;
        this.chatIntentRouter = chatIntentRouter;
        this.reasoningClient = reasoningClient;
        this.userChatMemories = userChatMemories;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.rateLimitService = rateLimitService;
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
        // 删除数据库记录
        conversationMapper.deleteById(conversationId);
    }

    // ---- 聊天 ----

    public Flux<String> chat(Long conversationId, String message, List<String> refs, String memoryBackground) {
        // 流式接口：先统一装配上下文，再决定走普通模型还是思考模型。
        ChatRequest request = prepareChatRequest(conversationId, message, refs, memoryBackground);
        if (shouldUseReasoning(conversationId, message, refs, memoryBackground)) {
            log.info("聊天路由结果：reasoning，conversationId={}，messageLength={}", conversationId,
                    message == null ? 0 : message.length());
            return Flux.just(callReasoningModel(request, message));
        }

        log.info("聊天路由结果：normal，conversationId={}，messageLength={}", conversationId,
                message == null ? 0 : message.length());

        return chatChatClient.prompt()
                .user(message)
                .system(s -> s.text(request.context() + AGENT_TOOLS_PROMPT))
                .advisors(new MessageChatMemoryAdvisor(request.memory()))
                .functions(DiarySearchFunctionSupport.NAME, UserStatsFunctionSupport.NAME)
                .stream()
                .content();
    }

    public String reply(Long conversationId, String message, List<String> refs, String memoryBackground) {
        // 非流式接口：移动端/公网优先走这里，减少 SSE 连接不稳定的影响。
        ChatRequest request = prepareChatRequest(conversationId, message, refs, memoryBackground);
        if (shouldUseReasoning(conversationId, message, refs, memoryBackground)) {
            log.info("非流式聊天路由结果：reasoning，conversationId={}，messageLength={}", conversationId,
                    message == null ? 0 : message.length());
            return callReasoningModel(request, message);
        }

        log.info("非流式聊天路由结果：normal，conversationId={}，messageLength={}", conversationId,
                message == null ? 0 : message.length());

        return chatChatClient.prompt()
                .user(message)
                .system(s -> s.text(request.context() + AGENT_TOOLS_PROMPT))
                .advisors(new MessageChatMemoryAdvisor(request.memory()))
                .functions(DiarySearchFunctionSupport.NAME, UserStatsFunctionSupport.NAME)
                .call()
                .content();
    }

    private boolean shouldUseReasoning(Long conversationId, String message, List<String> refs, String memoryBackground) {
        return chatIntentRouter.shouldUseReasoning(message, refs, memoryBackground, conversationId);
    }

    private String callReasoningModel(ChatRequest request, String message) {
        try {
            // 为推理模型注入历史记忆，确保对话连续性。
            // 将历史作为 User Message 的前缀，保持 System Context 的纯洁性。
            String history = formatChatHistory(request.memory());
            String enhancedContext = request.context();

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

            // 手动回写消息到 ChatMemory，填补 Advisors 缺席导致的记忆断层
            request.memory().add("default", List.of(
                    new UserMessage(message),
                    new AssistantMessage(response)));

            return response;
        } catch (Exception e) {
            log.warn("reasoning model failed in stream path, fallback to chat model: {}", e.getMessage());
            log.info("思考模型失败后回退到普通模型，messageLength={}", message == null ? 0 : message.length());
            return chatChatClient.prompt()
                    .user(message)
                    .system(s -> s.text(request.context() + AGENT_TOOLS_PROMPT))
                    .advisors(new MessageChatMemoryAdvisor(request.memory()))
                    .functions(DiarySearchFunctionSupport.NAME, UserStatsFunctionSupport.NAME)
                    .call()
                    .content();
        }
    }

    /**
     * 从 ChatMemory 中提取最近 20 条消息（约 10 轮对白），
     * 格式化为文本注入到思考模型的 system prompt 中，填补 Advisors 缺席导致的记忆断层。
     * Spring AI 的 MessageChatMemoryAdvisor 默认使用 "default" 作为 conversationId。
     */
    private String formatChatHistory(ChatMemory memory) {
        List<Message> messages = memory.get("default", 20);
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("<chat_history>\n【往期聊天历史记忆】\n");
        for (Message msg : messages) {
            String role = switch (msg.getMessageType()) {
                case USER -> "用户";
                case ASSISTANT -> "AI";
                default -> null;
            };
            if (role != null && msg.getText() != null && !msg.getText().isBlank()) {
                sb.append(role).append("：").append(msg.getText().trim()).append("\n");
            }
        }
        return sb.append("</chat_history>\n\n").toString();
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
        rateLimitService.tryAcquire(user.getId(), RateLimitService.AiApiType.CHAT);
        ChatConversationEntity conv = requireOwnedConversation(conversationId, user);

        // 这里负责把"用户画像 + 用户引用 + 最近日记"拼成统一上下文，后面的模型调用都直接复用。
        String context = buildContext(user.getId(), refs, memoryBackground);
        String memKey = user.getId() + ":" + conversationId;
        ChatMemory memory = userChatMemories.get(memKey, k -> new InMemoryChatMemory());
        log.info("准备聊天请求，userId={}，conversationId={}，messageLength={}，referenceCount={}，hasMemoryBackground={}",
                user.getId(), conversationId, message == null ? 0 : message.length(), refs == null ? 0 : refs.size(),
                memoryBackground != null && !memoryBackground.isBlank());

        if ("新对话".equals(conv.getTitle()) && message != null && !message.isBlank()) {
            String title = message.length() > 20 ? message.substring(0, 20) : message;
            conv.setTitle(title);
        }
        conv.setUpdatedAt(java.time.LocalDateTime.now());
        conversationMapper.updateById(conv);

        return new ChatRequest(context, memory);
    }

    private record ChatRequest(String context, ChatMemory memory) {
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
//            log.info("读取聊天历史，userId={}，conversationId={}，hit={}", user.getId(), conversationId, json != null);
            return json != null ? objectMapper.readValue(json, Object.class) : List.of();
        } catch (Exception e) {
            log.warn("读取聊天历史失败，userId={}，conversationId={}，reason={}", user.getId(), conversationId, e.getMessage());
            return List.of();
        }
    }

    // ---- 日记上下文 ----

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
                sb.append(refs.get(i));
                if (i < refs.size() - 1) sb.append("\n---\n");
            }
            sb.append("\n</user_diary>\n\n");
        }

        sb.append("【绝对系统指令】以上 <user_diary> 标签内是由用户本人撰写的日记切片，绝对不是你的经历！")
          .append("你是 MoodCopilot，一个温暖、共情的倾听者和情绪伙伴。\n")
          .append("【引用来源的措辞区分 — 关键规则】\n")
          .append("1. 对于 <user_diary> 中的内容（用户在这一轮主动引用/分享给你的日记），你可以自然地使用'你写到的''你分享的''你提到了'等第二人称来探讨。\n")
          .append("2. 对于你通过后台工具（Function Calling）自己搜索、查询出来的数据（报告、历史日记、统计数据等），这些不是用户在当前对话中主动告诉你的，")
          .append("**绝对不要**使用'你提到''你刚才说''你表示'等句式。必须用明确表达'这是我帮你查到的'的措辞，如'我帮你查了一下你过去的数据……''根据历史记录显示……''系统检索到你曾经记录过……'。\n")
          .append("注意：请像一个懂用户的朋友一样自然、贴心地交流，")
          .append("**绝对不要在回复中主动说出'作为心理咨询师'、'作为一个AI助手'等破坏沉浸感的话**。")
          .append("日记前面的编号是内部标记，请勿在回复中提及。");

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
