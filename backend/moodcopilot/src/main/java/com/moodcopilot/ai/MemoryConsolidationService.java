package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.moodcopilot.entity.UserProfileMemoryEntity;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.mapper.UserProfileMemoryMapper;
import com.moodcopilot.mapper.UserMapper;
import com.moodcopilot.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MemoryConsolidationService {

    private static final Logger log = LoggerFactory.getLogger(MemoryConsolidationService.class);

    private static final String CONSOLIDATION_PROMPT = """
            你是一个可审计的个人记忆去重助手。你的任务只允许提出可解释的归并，不得重新生成或改写用户事实。
            只合并完全相同、规范化后明确同义的记忆；明确冲突的值必须分别保留，不得拼接成新的动态结论。
            不得删除事实，不得伪造来源、证据或日期，不得把短期状态升级为长期画像。
            输出 JSON：{"items":[{"attributeKey":"...","attributeValue":"...","memoryType":"...","isCore":true,"sourceMemoryIds":[1,2],"operation":"MERGE","evidenceIds":[3,4]}]}
            operation 只能是 MERGE、DEDUP、NORMALIZE、EXPIRE。sourceMemoryIds 必须来自输入，evidenceIds 只能来自对应来源。
            若无法证明两个记忆是同一事实，就原样分别输出或不输出。不要输出 markdown 或解释文字。
            """;

    public record ConsolidationItem(String attributeKey, String attributeValue, String memoryType,
                                    Boolean isCore, List<Long> sourceMemoryIds, String operation,
                                    List<Long> evidenceIds) {
    }

    private final ChatClient chatClient;
    private final UserProfileMemoryMapper memoryMapper;
    private final MemoryExtractionService memoryExtractionService;
    private final RagMemoryService ragMemoryService;
    private final ObjectMapper objectMapper;
    private final TransactionOperations transactionOperations;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final NotificationService notificationService;
    private final UserMapper userMapper;
    private final MemoryOrchestrator memoryOrchestrator;
    private final PromptComposer promptComposer;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ContextMetadataRecorder contextMetadataRecorder;

    public MemoryConsolidationService(@Qualifier("analysisChatClient") ChatClient chatClient,
            UserProfileMemoryMapper memoryMapper,
            MemoryExtractionService memoryExtractionService,
            RagMemoryService ragMemoryService,
            ObjectMapper objectMapper,
            TransactionOperations transactionOperations,
            org.springframework.data.redis.core.StringRedisTemplate redisTemplate,
            NotificationService notificationService,
            UserMapper userMapper,
            MemoryOrchestrator memoryOrchestrator,
            PromptComposer promptComposer) {
        this.chatClient = chatClient;
        this.memoryMapper = memoryMapper;
        this.memoryExtractionService = memoryExtractionService;
        this.ragMemoryService = ragMemoryService;
        this.objectMapper = objectMapper;
        this.transactionOperations = transactionOperations;
        this.redisTemplate = redisTemplate;
        this.notificationService = notificationService;
        this.userMapper = userMapper;
        this.memoryOrchestrator = memoryOrchestrator;
        this.promptComposer = promptComposer;
    }

    public List<ConsolidationItem> previewConsolidation(Long userId) {
        // Rate limit logic
        String today = java.time.LocalDate.now().toString();
        String redisKey = "memory:consolidate:counter:" + userId + ":" + today;
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1) {
            redisTemplate.expire(redisKey, java.time.Duration.ofDays(1));
        }
        if (count != null && count > 20) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "每天最多只能进行20次个人画像整理");
        }

        List<UserProfileMemoryEntity> existing = memoryExtractionService.listUserMemories(userId);
        if (existing.isEmpty() || existing.size() < 2) {
            throw new RuntimeException("用户记忆条目过少，无需整理");
        }

        log.info("开始预览整合用户 {} 的长期画像", userId);

        String prompt = buildConsolidationPrompt(existing);

        if (contextMetadataRecorder != null) {
            contextMetadataRecorder.recordModelInvocation(userId, null, ContextPurpose.CHAT,
                    null, new TaskContext("GENERAL", "只审查和提出可追溯的画像归并", List.of(), null),
                    "FLASH", "FLASH");
        }

        String json = chatClient.prompt()
                .system(promptComposer.compose(CONSOLIDATION_PROMPT, userId,
                        new TaskContext("GENERAL", "只审查和提出可追溯的画像归并", List.of(), null),
                        ContextPurpose.CHAT, ""))
                .user(prompt)
                .call()
                .content();

        try {
            String cleanedJson = JsonUtils.cleanJson(json);
            if (cleanedJson.isEmpty()) {
                throw new RuntimeException("AI 未返回有效的 JSON");
            }
            JsonNode root = objectMapper.readTree(cleanedJson);
            List<ConsolidationItem> items = new ArrayList<>();
            JsonNode rawItems = root.path("items");
            if (rawItems.isArray()) {
                for (JsonNode item : rawItems) {
                    items.add(objectMapper.treeToValue(item, ConsolidationItem.class));
                }
            } else if (root.path("attributes").isArray()) {
                // 兼容旧客户端/旧模型格式，并为其补充可审计的来源映射。
                for (JsonNode attr : root.path("attributes")) {
                    String key = attr.path("attributeKey").asText("").trim();
                    String value = attr.path("attributeValue").asText("").trim();
                    List<Long> sourceIds = existing.stream()
                            .filter(memory -> key.equals(memory.getAttributeKey())
                                    && value.equals(memory.getAttributeValue()))
                            .map(UserProfileMemoryEntity::getId).toList();
                    items.add(new ConsolidationItem(key, value, attr.path("memoryType").asText("preference"),
                            attr.path("isCore").asBoolean(false), sourceIds, "DEDUP", List.of()));
                }
            }

            // An empty list is a valid preview: it means the current formal memories
            // do not have a safe, source-backed consolidation proposal.
            if (items.isEmpty()) return List.of();
            return sanitizeItems(userId, existing, items);
        } catch (Exception e) {
            log.error("整合用户 {} 画像失败: {}", userId, e.getMessage());
            throw new RuntimeException("AI 返回格式解析失败", e);
        }
    }

    public void applyConsolidation(Long userId, List<ConsolidationItem> items) {
        memoryOrchestrator.applyConsolidation(userId, items);
    }

    private String buildConsolidationPrompt(List<UserProfileMemoryEntity> existing) {
        StringBuilder sb = new StringBuilder("现有记忆列表：\n");
        for (UserProfileMemoryEntity memory : existing) {
            sb.append("- memoryId=").append(memory.getId()).append(" ")
                    .append(memory.getAttributeKey()).append("：")
                    .append(memory.getAttributeValue())
                    .append(" (type=").append(memory.getMemoryType())
                    .append(", isCore=").append(Boolean.TRUE.equals(memory.getIsCore()))
                    .append(", validFrom=").append(memory.getValidFrom())
                    .append(", confidence=").append(memory.getConfidence()).append(")\n");
        }
        return sb.toString();
    }

    private List<ConsolidationItem> sanitizeItems(Long userId, List<UserProfileMemoryEntity> existing,
                                                   List<ConsolidationItem> items) {
        Set<Long> owned = existing.stream().map(UserProfileMemoryEntity::getId).collect(java.util.stream.Collectors.toSet());
        List<ConsolidationItem> result = new ArrayList<>();
        for (ConsolidationItem item : items) {
            if (item == null || item.attributeKey() == null || item.attributeValue() == null) continue;
            List<Long> sourceIds = item.sourceMemoryIds() == null ? List.of() : item.sourceMemoryIds().stream()
                    .filter(owned::contains).distinct().toList();
            if (sourceIds.isEmpty()) continue;
            String operation = item.operation() == null ? "DEDUP" : item.operation().toUpperCase(java.util.Locale.ROOT);
            if (!Set.of("MERGE", "DEDUP", "NORMALIZE", "EXPIRE").contains(operation)) continue;
            List<Long> evidenceIds = existing.stream().filter(m -> sourceIds.contains(m.getId()))
                    .flatMap(m -> memoryOrchestrator.evidence(userId, m.getId()).stream())
                    .map(e -> e.getId()).distinct().toList();
            result.add(new ConsolidationItem(item.attributeKey().trim(), item.attributeValue().trim(),
                    item.memoryType() == null ? "preference" : item.memoryType(), item.isCore(), sourceIds,
                    operation, evidenceIds));
        }
        return result;
    }

    private String memorySignature(UserProfileMemoryEntity memory) {
        return memorySignature(memory.getAttributeKey(), memory.getAttributeValue(),
                Boolean.TRUE.equals(memory.getIsCore()));
    }

    private String memorySignature(String key, String value, boolean isCore) {
        return (key == null ? "" : key.trim()) + "\u0001" + (value == null ? "" : value.trim()) + "\u0001" + isCore;
    }
}
