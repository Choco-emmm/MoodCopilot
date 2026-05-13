package com.moodcopilot.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.entity.UserProfileMemoryEntity;
import com.moodcopilot.mapper.UserProfileMemoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MemoryExtractionService {

    private static final Logger log = LoggerFactory.getLogger(MemoryExtractionService.class);

    private static final String MEMORY_EXTRACTION_PROMPT = """
            你是用户长期画像提取助手。请根据“新日记”和“旧属性列表”，判断哪些长期特征应该新增、保留、修改或删除。
            只输出合法 JSON，不要输出 markdown，不要解释。
            JSON 格式必须是：
            {
              "attributes": [
                {"attributeKey": "性格", "attributeValue": "...."},
                {"attributeKey": "长期目标", "attributeValue": "...."}
              ]
            }
            规则：
            1. 只保留相对稳定、跨时间成立的特征，不要记录一次性的当天状态。
            2. 如果旧特征已被新日记推翻或明显变化，请输出更新后的值。
            3. 如果没有足够证据支持某条旧特征继续保留，可以不输出该条。
            4. attributeKey 使用简洁中文，例如：性格、长期目标、关键人物、长期压力源、重要关系。
            5. attributeValue 使用一句简洁中文，避免重复和空话。""";

    private final ChatClient analysisChatClient;
    private final UserProfileMemoryMapper userProfileMemoryMapper;
    private final ObjectMapper objectMapper;

    public MemoryExtractionService(ChatClient analysisChatClient,
                                   UserProfileMemoryMapper userProfileMemoryMapper,
                                   ObjectMapper objectMapper) {
        this.analysisChatClient = analysisChatClient;
        this.userProfileMemoryMapper = userProfileMemoryMapper;
        this.objectMapper = objectMapper;
    }

    @Async("aiExecutor")
    public void extractAndSyncMemory(Long userId, String diaryContent) {
        try {
            List<UserProfileMemoryEntity> existing = listUserMemories(userId);
            String prompt = buildExtractionUserPrompt(diaryContent, existing);
            String json = analysisChatClient.prompt()
                    .system(MEMORY_EXTRACTION_PROMPT)
                    .user(prompt)
                    .call()
                    .content();
            MemoryExtractionResponse response = objectMapper.readValue(json, MemoryExtractionResponse.class);
            syncMemories(userId, existing, sanitizeAttributes(response.attributes()));
        } catch (Exception e) {
            log.warn("长记忆提取失败，userId={}: {}", userId, e.getMessage());
        }
    }

    public String buildUserMemoryPrompt() {
        List<UserProfileMemoryEntity> memories = listUserMemories(currentUser().getId());
        if (memories.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("以下是用户已经形成的长期背景知识：\n");
        for (UserProfileMemoryEntity memory : memories) {
            sb.append("- ").append(memory.getAttributeKey()).append("：")
                    .append(memory.getAttributeValue()).append("\n");
        }
        return sb.toString();
    }

    private List<UserProfileMemoryEntity> listUserMemories(Long userId) {
        return userProfileMemoryMapper.selectList(new LambdaQueryWrapper<UserProfileMemoryEntity>()
                .eq(UserProfileMemoryEntity::getUserId, userId)
                .orderByAsc(UserProfileMemoryEntity::getAttributeKey));
    }

    private String buildExtractionUserPrompt(String diaryContent, List<UserProfileMemoryEntity> existing) {
        StringBuilder sb = new StringBuilder("新日记：\n").append(diaryContent).append("\n\n旧属性列表：\n");
        if (existing.isEmpty()) {
            sb.append("- 无\n");
        } else {
            for (UserProfileMemoryEntity memory : existing) {
                sb.append("- ").append(memory.getAttributeKey()).append("：")
                        .append(memory.getAttributeValue()).append("\n");
            }
        }
        return sb.toString();
    }

    private List<MemoryAttribute> sanitizeAttributes(List<MemoryAttribute> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return List.of();
        }
        Map<String, MemoryAttribute> deduped = new LinkedHashMap<>();
        for (MemoryAttribute attribute : attributes) {
            if (attribute == null || attribute.attributeKey() == null || attribute.attributeValue() == null) {
                continue;
            }
            String key = attribute.attributeKey().trim();
            String value = attribute.attributeValue().trim();
            if (key.isEmpty() || value.isEmpty()) {
                continue;
            }
            deduped.put(key, new MemoryAttribute(key, value));
        }
        return List.copyOf(deduped.values());
    }

    private void syncMemories(Long userId, List<UserProfileMemoryEntity> existing, List<MemoryAttribute> attributes) {
        Map<String, UserProfileMemoryEntity> existingByKey = existing.stream()
                .collect(Collectors.toMap(UserProfileMemoryEntity::getAttributeKey, memory -> memory, (a, b) -> a, LinkedHashMap::new));

        Set<String> newKeys = attributes.stream().map(MemoryAttribute::attributeKey).collect(Collectors.toSet());
        for (UserProfileMemoryEntity memory : existing) {
            if (!newKeys.contains(memory.getAttributeKey())) {
                userProfileMemoryMapper.deleteById(memory.getId());
            }
        }

        LocalDateTime now = LocalDateTime.now();
        for (MemoryAttribute attribute : attributes) {
            UserProfileMemoryEntity existingEntity = existingByKey.get(attribute.attributeKey());
            if (existingEntity != null) {
                existingEntity.setAttributeValue(attribute.attributeValue());
                existingEntity.setUpdateTime(now);
                userProfileMemoryMapper.updateById(existingEntity);
                continue;
            }
            UserProfileMemoryEntity entity = new UserProfileMemoryEntity();
            entity.setUserId(userId);
            entity.setAttributeKey(attribute.attributeKey());
            entity.setAttributeValue(attribute.attributeValue());
            entity.setUpdateTime(now);
            userProfileMemoryMapper.insert(entity);
        }
    }

    private UserEntity currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserEntity user) {
            return user;
        }
        throw new IllegalStateException("用户未登录");
    }

    record MemoryExtractionResponse(List<MemoryAttribute> attributes) {}

    record MemoryAttribute(String attributeKey, String attributeValue) {}
}
