package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.UserProfileMemoryEntity;
import com.moodcopilot.mapper.UserProfileMemoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MemoryConsolidationService {

    private static final Logger log = LoggerFactory.getLogger(MemoryConsolidationService.class);

    private static final String CONSOLIDATION_PROMPT = """
            你是一个 AI 记忆档案整理专家。下面是一位用户长期积累的个人画像和记忆特征列表。
            由于这些记忆是长期增量提取的，里面可能包含许多语义重复、维度重叠的属性（例如“工作压力”、“近期焦虑”、“长期压力源：工作”等互相交织的条目）。

            你的任务是将它们合并、去重、提纯，输出一份高度精简且全面的记忆列表。
            
            【整理规则】
            1. 合并同类项：将描述同一维度或同一事物的多个条目（如各种情绪问题、习惯等）合并为一个更全面、准确的条目。
            2. 去除冗余与过时信息：删除那些明显是瞬时状态、已经过时或被后续状态覆盖的信息，只保留跨时间成立的长期特征。
            3. 保留核心属性：对于极其重要的底层性格、价值观（原来 isCore=true 的项），在合并时应继续保留并将其 isCore 设为 true。
            4. 统一命名：attributeKey 必须精确、专业（如“情绪模式”、“社交偏好”、“核心压力源”）。
            
            只输出合法 JSON，不要输出 markdown 格式，不要有任何解释。
            JSON 格式必须是：
            {
              "attributes": [
                {"attributeKey": "情绪模式", "attributeValue": "....", "isCore": true},
                {"attributeKey": "近期压力", "attributeValue": "....", "isCore": false}
              ]
            }
            """;

    private final ChatClient chatClient;
    private final UserProfileMemoryMapper memoryMapper;
    private final MemoryExtractionService memoryExtractionService;
    private final RagMemoryService ragMemoryService;
    private final ObjectMapper objectMapper;
    private final TransactionOperations transactionOperations;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    public MemoryConsolidationService(@Qualifier("analysisChatClient") ChatClient chatClient,
                                      UserProfileMemoryMapper memoryMapper,
                                      MemoryExtractionService memoryExtractionService,
                                      RagMemoryService ragMemoryService,
                                      ObjectMapper objectMapper,
                                      TransactionOperations transactionOperations,
                                      org.springframework.data.redis.core.StringRedisTemplate redisTemplate) {
        this.chatClient = chatClient;
        this.memoryMapper = memoryMapper;
        this.memoryExtractionService = memoryExtractionService;
        this.ragMemoryService = ragMemoryService;
        this.objectMapper = objectMapper;
        this.transactionOperations = transactionOperations;
        this.redisTemplate = redisTemplate;
    }

    public List<MemoryExtractionService.MemoryAttribute> previewConsolidation(Long userId) {
        // Rate limit logic
        String today = java.time.LocalDate.now().toString();
        String redisKey = "memory:consolidate:count:" + userId + ":" + today;
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1) {
            redisTemplate.expire(redisKey, java.time.Duration.ofDays(1));
        }
        if (count != null && count > 2) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "每天最多只能进行两次智能整理");
        }

        List<UserProfileMemoryEntity> existing = memoryExtractionService.listUserMemories(userId);
        if (existing.isEmpty() || existing.size() < 2) {
            throw new RuntimeException("用户记忆条目过少，无需整理");
        }

        log.info("开始预览整合用户 {} 的长期画像", userId);

        String prompt = buildConsolidationPrompt(existing);

        String json = chatClient.prompt()
                .system(CONSOLIDATION_PROMPT)
                .user(prompt)
                .call()
                .content();

        try {
            String cleanedJson = JsonUtils.cleanJson(json);
            if (cleanedJson.isEmpty()) {
                throw new RuntimeException("AI 未返回有效的 JSON");
            }
            MemoryExtractionService.MemoryExtractionResponse response = objectMapper.readValue(cleanedJson, MemoryExtractionService.MemoryExtractionResponse.class);
            List<MemoryExtractionService.MemoryAttribute> attributes = response.attributes();

            if (attributes == null || attributes.isEmpty()) {
                throw new RuntimeException("AI 返回的属性列表为空");
            }

            // Deduplicate
            Map<String, MemoryExtractionService.MemoryAttribute> deduped = new LinkedHashMap<>();
            for (MemoryExtractionService.MemoryAttribute attr : attributes) {
                if (attr != null && attr.attributeKey() != null && attr.attributeValue() != null) {
                    deduped.put(attr.attributeKey().trim(), attr);
                }
            }
            return new java.util.ArrayList<>(deduped.values());
        } catch (Exception e) {
            log.error("整合用户 {} 画像失败: {}", userId, e.getMessage());
            throw new RuntimeException("AI 返回格式解析失败", e);
        }
    }

    public void applyConsolidation(Long userId, List<MemoryExtractionService.MemoryAttribute> attributes) {
        List<UserProfileMemoryEntity> existing = memoryExtractionService.listUserMemories(userId);
        LocalDateTime now = LocalDateTime.now();

        transactionOperations.execute(status -> {
            for (UserProfileMemoryEntity old : existing) {
                memoryMapper.deleteById(old.getId());
            }

            for (MemoryExtractionService.MemoryAttribute attr : attributes) {
                UserProfileMemoryEntity entity = new UserProfileMemoryEntity();
                entity.setUserId(userId);
                String key = attr.attributeKey().trim();
                if (key.length() > 64) key = key.substring(0, 64);
                String val = attr.attributeValue().trim();
                if (val.length() > 500) val = val.substring(0, 500);

                entity.setAttributeKey(key);
                entity.setAttributeValue(val);
                entity.setIsCore(Boolean.TRUE.equals(attr.isCore()));
                entity.setUpdateTime(now);
                memoryMapper.insert(entity);
            }
            return null;
        });

        List<UserProfileMemoryEntity> latest = memoryExtractionService.listUserMemories(userId);
        ragMemoryService.indexUserProfile(userId, latest);
    }

    private String buildConsolidationPrompt(List<UserProfileMemoryEntity> existing) {
        StringBuilder sb = new StringBuilder("现有记忆列表：\n");
        for (UserProfileMemoryEntity memory : existing) {
            sb.append("- ").append(memory.getAttributeKey()).append("：")
                    .append(memory.getAttributeValue())
                    .append(" (isCore=").append(Boolean.TRUE.equals(memory.getIsCore())).append(")\n");
        }
        return sb.toString();
    }
}
