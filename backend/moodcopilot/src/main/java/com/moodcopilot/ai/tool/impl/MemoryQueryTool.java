package com.moodcopilot.ai.tool.impl;

import com.moodcopilot.ai.MemoryExtractionService;
import com.moodcopilot.ai.MemoryQueryFunctionSupport;
import com.moodcopilot.ai.MemoryQueryRequest;
import com.moodcopilot.ai.MemoryQueryResult;
import com.moodcopilot.ai.RagMemoryService;
import com.moodcopilot.ai.SensitiveDataDetector;
import com.moodcopilot.ai.tool.ChatTool;
import com.moodcopilot.ai.tool.ToolExecutionContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MemoryQueryTool extends ChatTool<MemoryQueryRequest> {

    private static final String PROFILE_PREFIX = "用户长期画像 - ";

    private final MemoryExtractionService memoryExtractionService;
    private final RagMemoryService ragMemoryService;

    public MemoryQueryTool(MemoryExtractionService memoryExtractionService, RagMemoryService ragMemoryService) {
        this.memoryExtractionService = memoryExtractionService;
        this.ragMemoryService = ragMemoryService;
    }

    @Override
    public String name() {
        return MemoryQueryFunctionSupport.NAME;
    }

    @Override
    public String description() {
        return "读取当前登录用户的长期画像条目列表。可以通过 keyword 进行语义检索特定的画像片段（例如'我喜欢的食物'）。如果不提供 keyword 则返回最近更新的条目。limit 可选，默认 20，最大 50。";
    }

    @Override
    public Class<MemoryQueryRequest> requestType() {
        return MemoryQueryRequest.class;
    }

    @Override
    public LinkedHashMap<String, Object> properties() {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put("keyword", Map.of("type", "string", "description", "要检索的画像关键词"));
        props.put("limit", Map.of("type", "integer", "description", "返回数量上限，默认 20，最大 50"));
        return props;
    }

    @Override
    public List<String> required() {
        return List.of("keyword", "limit");
    }

    @Override
    public Object execute(MemoryQueryRequest request, ToolExecutionContext context) {
        long userId = context.userId();
        int limit = request.limit() != null ? Math.min(50, Math.max(1, request.limit())) : 20;
        String keyword = request.keyword() != null ? request.keyword().trim() : "";

        List<MemoryQueryResult.MemoryItem> items = keyword.isBlank()
                ? listRecentMemories(limit)
                : searchMemories(userId, keyword, limit);

        return new MemoryQueryResult(items.size(), items,
                items.isEmpty() ? "当前暂无符合条件的长期画像条目" : "已返回长期画像条目");
    }

    private List<MemoryQueryResult.MemoryItem> searchMemories(long userId, String keyword, int limit) {
        List<MemoryQueryResult.MemoryItem> items = new ArrayList<>();
        for (var hit : ragMemoryService.search(userId, keyword, limit, RagMemoryService.SOURCE_PROFILE)) {
            if (hit.content() == null) {
                continue;
            }
            String content = hit.content();
            String key = "画像片段";
            String value = content;
            if (content.startsWith(PROFILE_PREFIX)) {
                content = content.substring(PROFILE_PREFIX.length());
                String[] parts = content.split(":", 2);
                if (parts.length == 2) {
                    key = parts[0].trim();
                    value = parts[1].trim();
                }
            }
            items.add(new MemoryQueryResult.MemoryItem(key, value, null));
        }
        return items;
    }

    private List<MemoryQueryResult.MemoryItem> listRecentMemories(int limit) {
        return memoryExtractionService.listCurrentUserMemories().stream()
                .filter(m -> m != null && SensitiveDataDetector.allowedForMemory(
                        m.getAttributeKey(), m.getAttributeValue(), null))
                .sorted(Comparator.comparing(
                        m -> m.getUpdateTime(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .map(m -> new MemoryQueryResult.MemoryItem(
                        m.getAttributeKey(),
                        m.getAttributeValue(),
                        m.getUpdateTime() != null ? m.getUpdateTime().toString() : null))
                .toList();
    }

    /**
     * 前端模板是 {{ ref.snippet || ref.value }}，这里不发 snippet，避免「【key】key: value」式的重复显示。
     */
    @Override
    public List<Map<String, String>> references(Object result) {
        if (!(result instanceof MemoryQueryResult queryResult) || queryResult.items() == null) {
            return List.of();
        }
        List<Map<String, String>> items = new ArrayList<>();
        for (var item : queryResult.items()) {
            items.add(Map.of(
                    "type", "profile_memory",
                    "key", item.attributeKey() != null ? item.attributeKey() : "",
                    "value", item.attributeValue() != null ? item.attributeValue() : "",
                    "toolName", displayName()));
        }
        return items;
    }
}
