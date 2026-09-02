package com.moodcopilot.ai;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.ChatConversationEntity;
import com.moodcopilot.mapper.ChatConversationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Generates conversation titles away from the request thread. */
@Service
public class ChatTitleService {

    private static final Logger log = LoggerFactory.getLogger(ChatTitleService.class);
    private static final String MSG_PREFIX = "chat:msgs:";
    private static final String LOCK_PREFIX = "chat:title:pending:";
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private final ChatClient analysisChatClient;
    private final ChatConversationMapper conversationMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ChatTitleService(@Qualifier("analysisChatClient") ChatClient analysisChatClient,
            ChatConversationMapper conversationMapper,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.analysisChatClient = analysisChatClient;
        this.conversationMapper = conversationMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Enqueues one title generation attempt. The caller deliberately does not wait for this method.
     */
    @Async("aiExecutor")
    public void requestGeneration(Long conversationId, Long userId, String messageHint) {
        if (conversationId == null || userId == null) return;

        String lockKey = LOCK_PREFIX + conversationId;
        try {
            if (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL))) {
                return;
            }

            ChatConversationEntity conversation = conversationMapper.selectById(conversationId);
            if (conversation == null || !userId.equals(conversation.getUserId())
                    || !isPlaceholderTitle(conversation.getTitle())) {
                return;
            }

            String firstMessage = firstUserMessage(conversationId);
            if (firstMessage.isBlank()) {
                firstMessage = cleanSourceMessage(messageHint);
            }
            if (firstMessage.isBlank()) return;

            String generated = generateTitle(firstMessage);
            if (generated.isBlank()) {
                log.info("聊天标题生成结果为空，保留默认标题 conversationId={}", conversationId);
                return;
            }

            LambdaUpdateWrapper<ChatConversationEntity> update = new LambdaUpdateWrapper<ChatConversationEntity>()
                    .eq(ChatConversationEntity::getId, conversationId)
                    .eq(ChatConversationEntity::getUserId, userId)
                    .set(ChatConversationEntity::getTitle, generated);
            if (conversation.getTitle() == null) {
                update.isNull(ChatConversationEntity::getTitle);
            } else {
                update.eq(ChatConversationEntity::getTitle, conversation.getTitle());
            }
            int updated = conversationMapper.update(null, update);
            if (updated > 0) {
                log.info("聊天标题异步生成完成 conversationId={} title={}", conversationId, generated);
            }
        } catch (Exception e) {
            log.warn("聊天标题异步生成失败，保留默认标题 conversationId={} reason={}", conversationId, e.getMessage());
        } finally {
            try {
                redisTemplate.delete(lockKey);
            } catch (Exception e) {
                log.debug("释放聊天标题任务锁失败 conversationId={}", conversationId, e);
            }
        }
    }

    private String firstUserMessage(Long conversationId) {
        try {
            String json = redisTemplate.opsForValue().get(MSG_PREFIX + conversationId);
            if (json == null || json.isBlank()) return "";
            List<?> history = objectMapper.readValue(json, List.class);
            for (Object item : history) {
                if (!(item instanceof Map<?, ?> map)
                        || !"user".equalsIgnoreCase(String.valueOf(map.get("role")))) {
                    continue;
                }
                Object rawContent = map.get("content");
                String content = cleanSourceMessage(rawContent == null ? "" : String.valueOf(rawContent));
                if (!content.isBlank()) return content;
            }
        } catch (Exception e) {
            log.debug("读取首条聊天消息失败 conversationId={} reason={}", conversationId, e.getMessage());
        }
        return "";
    }

    private String generateTitle(String firstMessage) {
        String response = analysisChatClient.prompt()
                .system("你是聊天窗口标题生成器。根据用户的第一条消息生成一个简短、自然的中文标题。"
                        + "标题控制在4到12个中文字符左右，只返回标题本身，不要引号、解释、Markdown或前缀。"
                        + "不要使用‘新聊天’、‘新对话’等默认词。")
                .user(firstMessage)
                .call()
                .content();
        return cleanGeneratedTitle(response);
    }

    private String cleanGeneratedTitle(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String value = raw.replaceAll("(?is)<think>.*?</think>", "")
                .replace("```", "")
                .trim();
        try {
            if (value.startsWith("{") && value.endsWith("}")) {
                JsonNode node = objectMapper.readTree(value);
                JsonNode title = node.get("title");
                if (title == null) title = node.get("name");
                if (title != null && title.isTextual()) value = title.asText();
            }
        } catch (Exception ignored) {
            // Plain text is the normal response format; malformed JSON is cleaned below.
        }
        value = value.split("\\R", 2)[0]
                .replaceFirst("^(标题|Title)\\s*[:：]\\s*", "")
                .replaceAll("^[\\\"'‘’“”「」『』【】\\s]+|[\\\"'‘’“”「」『』【】\\s]+$", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (value.isBlank() || value.contains("新聊天") || value.contains("新对话")) return "";
        int maxCodePoints = 20;
        if (value.codePointCount(0, value.length()) > maxCodePoints) {
            value = value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
        }
        return value;
    }

    private String cleanSourceMessage(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String value = raw.trim();
        String reminder = "请优先结合我引用的日记内容来回应，不要忽略日记中的具体细节和情绪";
        for (String prefix : List.of("（" + reminder + "）", "(" + reminder + ")")) {
            if (value.startsWith(prefix)) {
                value = value.substring(prefix.length()).trim();
                break;
            }
        }
        if (value.startsWith("[重点跟进事件]")) {
            int separator = value.indexOf("\n\n");
            value = separator >= 0 ? value.substring(separator + 2).trim() : "";
        }
        if (value.startsWith("[用户引用了之前的发言：")) {
            String marker = "]\n\n用户的回复是：";
            int markerIndex = value.indexOf(marker);
            value = markerIndex >= 0 ? value.substring(markerIndex + marker.length()).trim() : "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private boolean isPlaceholderTitle(String title) {
        if (title == null || title.isBlank() || "新聊天".equals(title) || "新对话".equals(title)) return true;
        String value = title.trim();
        return value.startsWith("（请优先结合")
                || value.startsWith("(请优先结合")
                || value.startsWith("[重点跟进事件]")
                || value.startsWith("[用户引用了之前的发言：");
    }
}
