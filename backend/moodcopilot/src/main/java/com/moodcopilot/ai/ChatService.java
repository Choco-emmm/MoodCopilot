package com.moodcopilot.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.*;
import com.moodcopilot.mapper.ChatConversationMapper;
import com.moodcopilot.security.RateLimitService;
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

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class ChatService {

    private static final String MSG_PREFIX = "chat:msgs:";
    private static final String USER_CONTEXT_PREFIX = "chat:user-context:";

    private final ChatClient chatChatClient;
    private final ChatConversationMapper conversationMapper;
    private final Map<String, ChatMemory> userChatMemories;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RateLimitService rateLimitService;

    public ChatService(ChatClient chatChatClient,
            ChatConversationMapper conversationMapper,
            Map<String, ChatMemory> userChatMemories,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RateLimitService rateLimitService) {
        this.chatChatClient = chatChatClient;
        this.conversationMapper = conversationMapper;
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
        // 清除 ChatMemory
        String memKey = user.getId() + ":" + conversationId;
        userChatMemories.remove(memKey);
        // 清除 Redis 消息历史
        try {
            redisTemplate.delete(MSG_PREFIX + conversationId);
        } catch (Exception ignored) {
        }
        // 删除数据库记录
        conversationMapper.deleteById(conversationId);
    }

    // ---- 聊天 ----

    public Flux<String> chat(Long conversationId, String message, List<String> refs) {
        ChatRequest request = prepareChatRequest(conversationId, message, refs);

        return chatChatClient.prompt()
                .user(message)
                .system(s -> s.text(request.context()))
                .advisors(new MessageChatMemoryAdvisor(request.memory()))
                .stream()
                .content();
    }

    public String reply(Long conversationId, String message, List<String> refs) {
        ChatRequest request = prepareChatRequest(conversationId, message, refs);

        return chatChatClient.prompt()
                .user(message)
                .system(s -> s.text(request.context()))
                .advisors(new MessageChatMemoryAdvisor(request.memory()))
                .call()
                .content();
    }

    private ChatRequest prepareChatRequest(Long conversationId, String message, List<String> refs) {
        UserEntity user = currentUser();
        rateLimitService.tryAcquire(user.getId(), RateLimitService.AiApiType.CHAT);
        ChatConversationEntity conv = conversationMapper.selectById(conversationId);
        if (conv == null || !conv.getUserId().equals(user.getId())) {
            throw new ResponseStatusException(BAD_REQUEST, "会话不存在");
        }

        String context = buildContext(user.getId(), refs);
        String memKey = user.getId() + ":" + conversationId;
        ChatMemory memory = userChatMemories.computeIfAbsent(memKey, k -> new InMemoryChatMemory());

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
        try {
            String json = objectMapper.writeValueAsString(body.get("messages"));
            redisTemplate.opsForValue().set(MSG_PREFIX + conversationId, json, Duration.ofDays(7));
        } catch (Exception ignored) {
        }
    }

    public Object loadHistory(Long conversationId) {
        try {
            String json = redisTemplate.opsForValue().get(MSG_PREFIX + conversationId);
            return json != null ? objectMapper.readValue(json, Object.class) : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    // ---- 聊天上下文 ----

    private String buildContext(long userId, List<String> refs) {
        StringBuilder sb = new StringBuilder();

        String userContext = "";
        try {
            String raw = redisTemplate.opsForValue().get(USER_CONTEXT_PREFIX + userId);
            if (raw != null) {
                userContext = raw.trim();
            }
        } catch (Exception ignored) {
        }

        if (!userContext.isBlank()) {
            sb.append("以下是用户长期背景（自动摘要）：\n")
                    .append(userContext)
                    .append("\n\n");
        }

        if (refs != null && !refs.isEmpty()) {
            sb.append("以下是用户提供的重点引用（请优先结合这些信息回答）：\n");
            refs.stream().limit(2).forEach(ref -> {
                String compact = ref == null ? "" : ref.trim();
                if (compact.length() > 120) {
                    compact = compact.substring(0, 120);
                }
                if (!compact.isEmpty()) {
                    sb.append("- ").append(compact).append("\n");
                }
            });
            sb.append("\n");
        }

        return sb.toString();
    }

    private UserEntity currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserEntity user) {
            return user;
        }
        throw new ResponseStatusException(BAD_REQUEST, "用户未登录");
    }
}
