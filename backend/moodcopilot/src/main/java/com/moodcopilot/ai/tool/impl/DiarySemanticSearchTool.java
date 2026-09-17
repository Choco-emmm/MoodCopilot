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
        return "向量语义检索工具，专门用于根据『概念、情感、抽象描述』查找历史日记。"
                + "如果用户的提问是关于感觉、宽泛概念或意境（例如'关于工作压力的事'、'那张下雨天的图片'），【必须】使用此工具。"
                + "【强烈警告】：由于底层采用向量检索，对专有名词或小众词汇的命中率极低！如果用户询问的是具体的人名、地名、实体名词（如'小星'、'南京'），【绝对禁止】使用此工具，请务必改用 diaryKeywordSearchFunction。"
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
