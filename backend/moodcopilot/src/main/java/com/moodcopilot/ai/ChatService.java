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
                .system(s -> s.text(request.context()))
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
                .system(s -> s.text(request.context()))
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
            // 为推理模型注入历史记忆，确保对话连续性
            String history = formatChatHistory(request.memory());
            String enhancedContext = request.context();
            if (!history.isEmpty()) {
                enhancedContext = history + "\n" + enhancedContext;
            }

            log.info("调用思考模型分支，contextLength={}，historyLength={}，messageLength={}",
                    request.context().length(), history.length(),
                    message == null ? 0 : message.length());
            String response = reasoningClient.generate(enhancedContext, message);

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
                    .system(s -> s.text(request.context()))
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
        StringBuilder sb = new StringBuilder("【往期聊天历史记忆】\n");
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
        return sb.append("\n").toString();
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
            sb.append("【过往长期事实背景，仅供参考 — 不要与当前引用内容混淆】\n")
              .append(memoryBackground).append("\n\n");
        }

        sb.append("重要约束：引用内容和日记内容都来自用户本人，不是你的亲身经历。")
                .append("回答时不要说'我昨天写了'、'我经历过'，应使用'你提到/你写到/从你的日记看'这类表述。")
                .append("另外，日记和引用前面的编号（如 #1、#3）是内部标记，不要在回复中提及这些编号，")
                .append("需要引用具体日记时请说明日期（如'你5月10日提到'）。\n\n");

        if (refs != null && !refs.isEmpty()) {
            sb.append("【绝对核心聚焦指令】\n");
            sb.append("核心任务：用户本次对话显式引用了下面这篇日记。你后续的共情、分析和所有互动追问，")
              .append("必须 100% 紧密围绕这篇日记中所记录的具体事件、特定人物、核心冲突以及当时的情绪展开。\n");
            sb.append("严禁行为：严禁给出敷衍、宏观、万能的宽泛安慰。不要跳出这篇日记去聊不相关的话题。")
              .append("请像一位面对面进行个案心理疏导的专业咨询师，针对这篇引用的具体切片进行剥茧抽丝的引导。\n\n");
            sb.append("【当前讨论的靶向目标 — 用户聚焦引用的日记内容】\n\"\"\"\n");
            for (int i = 0; i < refs.size(); i++) {
                sb.append(refs.get(i));
                if (i < refs.size() - 1) sb.append("\n---\n");
            }
            sb.append("\n\"\"\"\n\n");
        }

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
