package com.moodcopilot.ai.tool.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodcopilot.ai.GraphSearchFunctionSupport;
import com.moodcopilot.ai.GraphSearchRequest;
import com.moodcopilot.ai.GraphSearchResult;
import com.moodcopilot.ai.RagMemoryService;
import com.moodcopilot.ai.tool.ChatTool;
import com.moodcopilot.ai.tool.ToolExecutionContext;
import com.moodcopilot.ai.tool.ToolSnippets;
import com.moodcopilot.entity.DiaryKnowledgeGraphEntity;
import com.moodcopilot.mapper.DiaryKnowledgeGraphMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GraphSearchTool extends ChatTool<GraphSearchRequest> {

    private static final String GRAPH_ID_PREFIX = "graph:";
    private static final String STATUS_ACTIVE = "active";

    private final DiaryKnowledgeGraphMapper diaryKnowledgeGraphMapper;
    private final RagMemoryService ragMemoryService;

    public GraphSearchTool(DiaryKnowledgeGraphMapper diaryKnowledgeGraphMapper, RagMemoryService ragMemoryService) {
        this.diaryKnowledgeGraphMapper = diaryKnowledgeGraphMapper;
        this.ragMemoryService = ragMemoryService;
    }

    @Override
    public String name() {
        return GraphSearchFunctionSupport.NAME;
    }

    @Override
    public String description() {
        return "根据实体关键词，从知识图谱中查询因果/情绪归因关系三元组。keyword 是要搜索的实体关键词（如'工作'、'失眠'），limit 可选，默认 20，最大 50。"
                + "返回三元组列表。适合回答「什么导致了什么」、「为什么」等因果问题。如果想获取用户的整体关系图谱概览，可传入空的 keyword。";
    }

    @Override
    public Class<GraphSearchRequest> requestType() {
        return GraphSearchRequest.class;
    }

    @Override
    public LinkedHashMap<String, Object> properties() {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put("keyword", Map.of("type", "string", "description", "实体关键词，传空获取整体概览"));
        props.put("limit", Map.of("type", "integer", "description", "返回数量上限，默认 20，最大 50"));
        return props;
    }

    @Override
    public List<String> required() {
        return List.of("keyword", "limit");
    }

    @Override
    public Object execute(GraphSearchRequest request, ToolExecutionContext context) {
        long userId = context.userId();
        String keyword = request.keyword() != null ? request.keyword().trim() : "";
        int limit = request.limit() != null ? Math.min(50, Math.max(1, request.limit())) : 20;

        List<GraphSearchResult.GraphItem> items = keyword.isBlank()
                ? fetchRecentTriples(userId, limit)
                : searchTriples(userId, keyword, limit);

        if (items.isEmpty()) {
            String note = keyword.isBlank() ? "当前暂无知识图谱记录" : "未找到与 '" + keyword + "' 相关的图谱三元组";
            return new GraphSearchResult(0, items, note);
        }
        return new GraphSearchResult(items.size(), items, "已返回知识图谱因果三元组（共 " + items.size() + " 条）");
    }

    private List<GraphSearchResult.GraphItem> searchTriples(long userId, String keyword, int limit) {
        List<GraphSearchResult.GraphItem> items = new ArrayList<>();
        Set<Long> graphIds = new LinkedHashSet<>();
        for (var hit : ragMemoryService.search(userId, keyword, limit, RagMemoryService.SOURCE_GRAPH)) {
            if (hit.sourceId() != null && hit.sourceId().startsWith(GRAPH_ID_PREFIX)) {
                try {
                    graphIds.add(Long.parseLong(hit.sourceId().substring(GRAPH_ID_PREFIX.length())));
                } catch (NumberFormatException ignored) {
                    // 非数值 sourceId 直接跳过
                }
            }
        }
        if (graphIds.isEmpty()) {
            return items;
        }
        LambdaQueryWrapper<DiaryKnowledgeGraphEntity> wrapper = activeGraphQuery(userId)
                .in(DiaryKnowledgeGraphEntity::getId, graphIds);
        Map<Long, DiaryKnowledgeGraphEntity> byId = new LinkedHashMap<>();
        for (var entity : diaryKnowledgeGraphMapper.selectList(wrapper)) {
            byId.put(entity.getId(), entity);
        }
        for (Long graphId : graphIds) {
            var entity = byId.get(graphId);
            if (entity != null) {
                items.add(toGraphItem(entity));
            }
        }
        return items;
    }

    /** 降级全量拉取：返回最近的图谱关系概览。 */
    private List<GraphSearchResult.GraphItem> fetchRecentTriples(long userId, int limit) {
        LambdaQueryWrapper<DiaryKnowledgeGraphEntity> wrapper = activeGraphQuery(userId)
                .orderByDesc(DiaryKnowledgeGraphEntity::getCreatedAt);
        List<GraphSearchResult.GraphItem> items = new ArrayList<>();
        for (var entity : diaryKnowledgeGraphMapper.selectPage(
                com.baomidou.mybatisplus.extension.plugins.pagination.Page.of(1, limit), wrapper).getRecords()) {
            items.add(toGraphItem(entity));
        }
        return items;
    }

    private LambdaQueryWrapper<DiaryKnowledgeGraphEntity> activeGraphQuery(long userId) {
        return new LambdaQueryWrapper<DiaryKnowledgeGraphEntity>()
                .eq(DiaryKnowledgeGraphEntity::getUserId, userId)
                .and(w -> w.isNull(DiaryKnowledgeGraphEntity::getStatus)
                        .or().eq(DiaryKnowledgeGraphEntity::getStatus, STATUS_ACTIVE));
    }

    private GraphSearchResult.GraphItem toGraphItem(DiaryKnowledgeGraphEntity entity) {
        return new GraphSearchResult.GraphItem(
                entity.getHeadEntity() + " " + entity.getRelation() + " " + entity.getTailEntity(),
                entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null,
                entity.getDiaryId());
    }

    @Override
    public List<Map<String, String>> references(Object result) {
        if (!(result instanceof GraphSearchResult graphResult) || graphResult.items() == null) {
            return List.of();
        }
        List<Map<String, String>> items = new ArrayList<>();
        for (var graphItem : graphResult.items()) {
            items.add(Map.of(
                    "type", "graph_memory",
                    "snippet", ToolSnippets.compact(graphItem.content()),
                    "date", graphItem.date() != null ? graphItem.date() : "",
                    "diaryId", graphItem.diaryId() != null ? graphItem.diaryId().toString() : "",
                    "toolName", displayName()));
        }
        return items;
    }
}
