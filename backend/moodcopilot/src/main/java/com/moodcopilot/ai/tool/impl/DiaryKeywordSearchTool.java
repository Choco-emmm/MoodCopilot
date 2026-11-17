package com.moodcopilot.ai.tool.impl;

import com.moodcopilot.ai.tool.ChatTool;
import com.moodcopilot.ai.tool.ToolExecutionContext;
import com.moodcopilot.ai.tool.ToolSnippets;
import com.moodcopilot.diary.DiarySearchRequest;
import com.moodcopilot.diary.DiarySearchResult;
import com.moodcopilot.diary.DiaryService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DiaryKeywordSearchTool extends ChatTool<DiarySearchRequest> {

    private final DiaryService diaryService;

    public DiaryKeywordSearchTool(DiaryService diaryService) {
        this.diaryService = diaryService;
    }

    @Override
    public String name() {
        return "diaryKeywordSearchFunction";
    }

    @Override
    public String description() {
        return "数据库精确文本检索工具，用于查找日记中的人名、地名、具体物品、书名、电影名等明确的名词或专有名词。"
                + "用户提到具体实体名称（如'小星'、'三体'、'南京'）时用这个。"
                + "它只做字符级精确匹配，不适用于感觉、情绪或宽泛概念，那类问题请改用 diarySemanticSearchFunction。"
                + "如果用户没有给出任何检索词（例如'我最近写了什么'、'日记里都提到些什么'），"
                + "请直接用 listRecentDiariesFunction 拿列表，不要用本工具猜关键词反复试探。"
                + "返回的是日记摘要，不足以回答具体问题时，用返回的 id 调用 readDiaryFunction 取全文。";
    }

    @Override
    public Class<DiarySearchRequest> requestType() {
        return DiarySearchRequest.class;
    }

    @Override
    public LinkedHashMap<String, Object> properties() {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put("keyword", Map.of("type", "string", "description", "要精确查找的名词或短语"));
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
        return diaryService.searchOwnDiarySummaries(userId, request);
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
