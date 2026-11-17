package com.moodcopilot.ai.tool.impl;

import com.moodcopilot.ai.RagMemoryService;
import com.moodcopilot.ai.tool.ChatTool;
import com.moodcopilot.ai.tool.ToolExecutionContext;
import com.moodcopilot.ai.tool.ToolSnippets;
import com.moodcopilot.diary.DiarySearchRequest;
import com.moodcopilot.diary.DiarySearchResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DiarySemanticSearchTool extends ChatTool<DiarySearchRequest> {

    private final RagMemoryService ragMemoryService;

    public DiarySemanticSearchTool(RagMemoryService ragMemoryService) {
        this.ragMemoryService = ragMemoryService;
    }

    @Override
    public String name() {
        return "diarySemanticSearchFunction";
    }

    @Override
    public String description() {
        return "向量语义检索工具，用于按概念、情感或抽象描述查找历史日记。"
                + "用户的提问是关于感觉、宽泛概念或意境时（例如'关于工作压力的事'、'那张下雨天的图片'）用这个。"
                + "向量检索对专有名词和小众词汇命中率低，具体的人名、地名、实体名词请改用 diaryKeywordSearchFunction。"
                + "如果用户没有给出任何检索词（例如'我最近写了什么'、'日记里都提到些什么'），"
                + "请直接用 listRecentDiariesFunction 拿列表，不要用本工具编一个查询反复试探。"
                + "返回的是日记摘要，不足以回答具体问题时，用返回的 id 调用 readDiaryFunction 取全文。";
    }

    @Override
    public Class<DiarySearchRequest> requestType() {
        return DiarySearchRequest.class;
    }

    @Override
    public LinkedHashMap<String, Object> properties() {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put("keyword", Map.of("type", "string", "description", "要语义检索的概念或抽象感觉"));
        props.put("startDate", Map.of("type", "string", "description", "开始日期，格式 YYYY-MM-DD"));
        props.put("endDate", Map.of("type", "string", "description", "结束日期，格式 YYYY-MM-DD"));
        return props;
    }

    @Override
    public List<String> required() {
        return List.of("keyword", "startDate", "endDate");
    }

    @Override
    public Object execute(DiarySearchRequest request, ToolExecutionContext context) {
        long userId = context.userId();
        return ragMemoryService.searchForTool(userId, request);
    }

    @Override
    public List<Map<String, String>> references(Object result) {
        if (!(result instanceof DiarySearchResult searchResult) || searchResult.diaries() == null) {
            return List.of();
        }
        List<Map<String, String>> items = new ArrayList<>();
        for (var diary : searchResult.diaries()) {
            items.add(Map.of(
                    "type", "tool_memory",
                    "diaryId", diary.id() != null ? diary.id().toString() : "",
                    "date", diary.date() != null ? diary.date().toString() : "",
                    "snippet", ToolSnippets.compact(diary.snippet()),
                    "toolName", displayName()));
        }
        return items;
    }
}
