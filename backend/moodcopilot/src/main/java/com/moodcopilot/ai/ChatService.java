package com.moodcopilot.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.*;
import com.moodcopilot.mapper.ChatConversationMapper;
import com.moodcopilot.mapper.DiaryAnalysisMapper;
import com.moodcopilot.mapper.DiaryMapper;
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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final String MSG_PREFIX = "chat:msgs:";

    private final ChatClient chatChatClient;
    private final DiaryMapper diaryMapper;
    private final DiaryAnalysisMapper diaryAnalysisMapper;
    private final ChatConversationMapper conversationMapper;
    private final Map<String, ChatMemory> userChatMemories;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RateLimitService rateLimitService;

    public ChatService(ChatClient chatChatClient,
                       DiaryMapper diaryMapper,
                       DiaryAnalysisMapper diaryAnalysisMapper,
                       ChatConversationMapper conversationMapper,
                       Map<String, ChatMemory> userChatMemories,
                       StringRedisTemplate redisTemplate,
                       ObjectMapper objectMapper,
                       RateLimitService rateLimitService) {
        this.chatChatClient = chatChatClient;
        this.diaryMapper = diaryMapper;
        this.diaryAnalysisMapper = diaryAnalysisMapper;
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
                        .orderByDesc(ChatConversationEntity::getUpdatedAt)
        );
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
        userChatMemories.remove(memKey);
        // 清除 Redis 消息历史
        try {
            redisTemplate.delete(MSG_PREFIX + conversationId);
        } catch (Exception ignored) {}
        // 删除数据库记录
        conversationMapper.deleteById(conversationId);
    }

    // ---- 聊天 ----

    public Flux<String> chat(Long conversationId, String message, List<String> refs, String memoryBackground) {
        ChatRequest request = prepareChatRequest(conversationId, message, refs, memoryBackground);

        return chatChatClient.prompt()
                .user(message)
                .system(s -> s.text(request.context()))
                .advisors(new MessageChatMemoryAdvisor(request.memory()))
                .functions(DiarySearchFunctionSupport.NAME)
                .stream()
                .content();
    }

    public String reply(Long conversationId, String message, List<String> refs, String memoryBackground) {
        ChatRequest request = prepareChatRequest(conversationId, message, refs, memoryBackground);

        return chatChatClient.prompt()
                .user(message)
                .system(s -> s.text(request.context()))
                .advisors(new MessageChatMemoryAdvisor(request.memory()))
                .functions(DiarySearchFunctionSupport.NAME)
                .call()
                .content();
    }

    /**
     * 统一准备聊天请求所需的三类上下文：
     * 1. 当前用户的长期画像背景；
     * 2. 用户主动引用的资料；
     * 3. 最近日记及其分析结果。
     * 同时在这里完成会话归属校验、限额校验和内存会话装配。
     */
    private ChatRequest prepareChatRequest(Long conversationId, String message, List<String> refs, String memoryBackground) {
        UserEntity user = currentUser();
        rateLimitService.tryAcquire(user.getId(), RateLimitService.AiApiType.CHAT);
        ChatConversationEntity conv = requireOwnedConversation(conversationId, user);

        String context = buildContext(user.getId(), refs, memoryBackground);
        String memKey = user.getId() + ":" + conversationId;
        ChatMemory memory = userChatMemories.computeIfAbsent(memKey, k -> new InMemoryChatMemory());
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

    private record ChatRequest(String context, ChatMemory memory) {}

    // ---- 消息历史（Redis） ----

    public void saveHistory(Long conversationId, Map<String, Object> body) {
        UserEntity user = currentUser();
        requireOwnedConversation(conversationId, user);
        try {
            String json = objectMapper.writeValueAsString(body.get("messages"));
            redisTemplate.opsForValue().set(MSG_PREFIX + conversationId, json, Duration.ofDays(7));
            log.info("保存聊天历史成功，userId={}，conversationId={}，payloadLength={}", user.getId(), conversationId, json.length());
        } catch (Exception ignored) {}
    }

    public Object loadHistory(Long conversationId) {
        UserEntity user = currentUser();
        requireOwnedConversation(conversationId, user);
        try {
            String json = redisTemplate.opsForValue().get(MSG_PREFIX + conversationId);
            log.info("读取聊天历史，userId={}，conversationId={}，hit={}", user.getId(), conversationId, json != null);
            return json != null ? objectMapper.readValue(json, Object.class) : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    // ---- 日记上下文 ----

    /**
     * 组装给大模型的 system context。
     * 顺序固定为：长期画像 -> 引用资料 -> 最近日记与分析。
     * 这样模型会先拿到稳定的长期背景，再参考当前提问显式引用的内容，最后用近期日记补足上下文。
     */
    private String buildContext(long userId, List<String> refs, String memoryBackground) {
        StringBuilder sb = new StringBuilder();

        if (memoryBackground != null && !memoryBackground.isBlank()) {
            sb.append(memoryBackground).append("\n");
        }

        // 引用栏内容（广场陪跑跳转、引用日记等）
        if (refs != null && !refs.isEmpty()) {
            sb.append("以下内容是用户引用的话题或资料，你的回答应重点基于这些内容：\n");
            for (int i = 0; i < refs.size(); i++) {
                sb.append("[引用 #").append(i + 1).append("] ").append(refs.get(i)).append("\n");
            }
            sb.append("\n");
        }

        List<DiaryEntity> recentDiaries = diaryMapper.selectList(
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getAuthorUserId, userId)
                        .orderByDesc(DiaryEntity::getCreatedAt)
                        .last("LIMIT 10")
        );
        log.info("构建聊天上下文，userId={}，referenceCount={}，recentDiaryCount={}", userId, refs == null ? 0 : refs.size(), recentDiaries.size());

        if (!recentDiaries.isEmpty()) {
            Map<Long, DiaryAnalysisEntity> analysisMap = diaryAnalysisMapper.selectBatchIds(
                recentDiaries.stream().map(DiaryEntity::getId).toList()).stream()
                .collect(Collectors.toMap(DiaryAnalysisEntity::getDiaryId, analysis -> analysis));
            sb.append("以下是你最近日记的内容（你可以引用它们来回复用户）：\n");
            var sorted = recentDiaries.stream()
                    .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                    .toList();
            for (int i = 0; i < sorted.size(); i++) {
                DiaryEntity diary = sorted.get(i);
            DiaryAnalysisEntity analysis = analysisMap.get(diary.getId());
                sb.append("[日记 #").append(i + 1).append(" · ").append(diary.getCreatedAt().toLocalDate()).append("] ");
                if (analysis != null) {
                    sb.append("情绪：").append(analysis.getMoodLabel())
                            .append("，主题：").append(String.join("、", analysis.getTopicLabelsJson()))
                            .append("\n内容：").append(diary.getContent()).append("\n");
                } else {
                    sb.append("内容：").append(diary.getContent()).append("\n");
                }
            }
        }

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
