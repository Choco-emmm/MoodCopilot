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
            由于这些记忆是长期增量提取的，里面可能包含许多语义重复、维度重叠的属性（例如“工作压力”、“近期焦虑”、“长期压力源：工作”等互相交织的条目），甚至包含了一些早已过时的短期情绪记录。

            你的任务是将它们合并、去重、提纯，输出一份高度精简、维度清晰且立体的【心理与行为画像】。
            
            【整理规则】
            1. 合并同类项但保留细节：将描述同一维度或同一事物的多个条目合并为一个更全面、准确的条目。合并时**绝不能丢失关键细节**，尤其是具体的引发事件、特定人物或特定感受。
            2. 剔除噪声与瞬时状态：坚决删除那些明显是一次性事件、瞬时情绪或已经过时的状态（例如“今天中午吃了火锅”、“昨天因为下雨很烦”）。我们只保留【跨时间成立的长期特征】。
            3. 保留核心属性：对于极其重要的底层性格、深层创伤、核心沟通偏好（原来 isCore=true 的项），在合并时应继续保留，并务必将其 isCore 继续设为 true。
            4. 统一专业命名：attributeKey 必须精确、分类清晰且原子化（如“情绪模式”、“社交偏好”、“核心压力源”、“健康状况”、“自我认知”），不要使用宽泛或冗长的 Key。
            5. 解决冲突：如果发现旧记忆和新记忆有矛盾，请在合并时提炼为动态变化，如“原本社恐，但近期开始尝试社交突破”。
            6. 控制数量：合并后的属性条目数量应尽可能精简，最好控制在 10-15 条以内，避免碎片化。

            【严格格式要求】
            只输出合法 JSON，**绝不要**输出 markdown 格式代码块（不要有 ```json 标签），**绝不要**有任何多余解释或开头语。
            正确格式示例：
            {
              "attributes": [
                {"attributeKey": "情绪模式", "attributeValue": "偏敏感内耗，遇到批评容易陷入自责，但近期在尝试自我开解", "isCore": true},
                {"attributeKey": "长期压力源", "attributeValue": "工作强度过大导致长期处于紧绷状态", "isCore": true},
                {"attributeKey": "运动习惯", "attributeValue": "近期开始养成每周跑步的习惯，有助于缓解焦虑", "isCore": false}
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
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "每天最多只能进行2次个人画像整理");
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
