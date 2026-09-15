package com.moodcopilot.ai.tool.impl;

import com.moodcopilot.ai.DiarySearchFunctionSupport;
import com.moodcopilot.ai.RagMemoryService;
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

public class DiarySearchTool extends ChatTool<DiarySearchRequest> {

    private final DiaryService diaryService;
    private final RagMemoryService ragMemoryService;

    public DiarySearchTool(DiaryService diaryService, RagMemoryService ragMemoryService) {
        this.diaryService = diaryService;
        this.ragMemoryService = ragMemoryService;
    }

    @Override
    public String name() {
        return DiarySearchFunctionSupport.NAME;
    }

    @Override
    public String description() {
        return "检索当前登录用户自己的历史日记、图片描述、音乐元数据等。keyword、startDate、endDate 都可选，日期格式为 YYYY-MM-DD。"
                + "keyword 参数：要搜索的关键词或语义描述。由于底层采用向量语义检索，你可以直接输入概念或抽象感觉"
                + "（例如'关于工作压力的事'、'那张下雨天的图片'），而不需要精确匹配原文词汇。"
                + "如果用户意图宽泛，可以传入空字符串，结合时间参数查询。"
                + "返回的是索引条目（日记 id、日期、AI 摘要或开头节选），**不是正文** —— "
                + "摘要不足以回答具体问题时，用返回的 id 调用 readDiaryFunction 取全文，不要凭摘要编内容。";
    }

    @Override
    public Class<DiarySearchRequest> requestType() {
        return DiarySearchRequest.class;
    }

    @Override
    public LinkedHashMap<String, Object> properties() {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put("keyword", Map.of("type", "string", "description", "搜索关键词或语义描述"));
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
        DiarySearchResult result = ragMemoryService.searchForTool(userId, request);
        if (result == null) {
            result = diaryService.searchOwnDiarySummaries(request);
        }
        return result;
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
