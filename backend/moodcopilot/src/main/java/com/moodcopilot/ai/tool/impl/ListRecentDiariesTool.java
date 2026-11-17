package com.moodcopilot.ai.tool.impl;

import com.moodcopilot.ai.tool.ChatTool;
import com.moodcopilot.ai.tool.ToolExecutionContext;
import com.moodcopilot.ai.tool.ToolSnippets;
import com.moodcopilot.diary.DiarySearchRequest;
import com.moodcopilot.diary.DiarySearchResult;
import com.moodcopilot.diary.DiaryService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按时间倒序列出最近的日记摘要。
 * <p>
 * 「我最近都写了什么」「日记里提到些什么」这类**没有具体检索词**的概览问题，
 * 之前只能靠 diaryKeywordSearch / diarySemanticSearch 反复试探关键词再逐篇 readDiary，
 * 很容易把 Agent Loop 的递归深度耗光。这里复用
 * {@link DiaryService#searchOwnDiarySummaries} 在 keyword 为空时的倒序全量能力，
 * 让这类问题一次拿全。
 * <p>
 * 只读工具，不产生任何写入，不需要审批。
 */
public class ListRecentDiariesTool extends ChatTool<ListRecentDiariesTool.RecentDiaryListRequest> {

    private final DiaryService diaryService;

    public ListRecentDiariesTool(DiaryService diaryService) {
        this.diaryService = diaryService;
    }

    /** 与服务层 MAX_SUMMARY_LIMIT 对齐；超过它也不会多给，只能靠缩小时间范围拿全。 */
    private static final int MAX_LIMIT = 50;
    private static final int DEFAULT_LIMIT = 10;

    @Override
    public String name() {
        return "listRecentDiariesFunction";
    }

    @Override
    public String description() {
        return "按时间倒序列出最近写过的日记摘要（含 id、时间、节选）。"
                + "当用户问『我最近写了什么』『日记里都提到些什么』『这段时间记了哪些事』『帮我回顾一下最近的记录』这类"
                + "**没有具体检索词**的概览问题时，【必须】先用本工具一次拿到列表，不要用其他检索工具逐条试探。"
                + "拿到列表后如果某篇的摘要不足以回答，再用返回的 id 调用 readDiaryFunction 取全文。";
    }

    @Override
    public Class<RecentDiaryListRequest> requestType() {
        return RecentDiaryListRequest.class;
    }

    @Override
    public LinkedHashMap<String, Object> properties() {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put("limit", Map.of("type", "integer",
                "description", "最多返回几篇，默认 " + DEFAULT_LIMIT + "，上限 " + MAX_LIMIT));
        props.put("startDate", Map.of("type", "string", "description", "开始日期，格式 YYYY-MM-DD，可省略"));
        props.put("endDate", Map.of("type", "string", "description", "结束日期，格式 YYYY-MM-DD，可省略"));
        return props;
    }

    @Override
    public List<String> required() {
        return List.of();
    }

    @Override
    public Object execute(RecentDiaryListRequest request, ToolExecutionContext context) {
        Long userId = context.userId();
        LocalDate startDate = request == null ? null : request.startDate();
        LocalDate endDate = request == null ? null : request.endDate();
        int limit = normalizedLimit(request == null ? null : request.limit());

        // keyword 传 null：服务层就退化成「按创建时间倒序取该用户的日记」，不额外加检索条件。
        // total 由服务层回填真实匹配数，note 里会写清「还有更早的没返回」，模型据此缩小时间范围。
        return diaryService.searchOwnDiarySummaries(userId,
                new DiarySearchRequest(null, startDate, endDate), limit);
    }

    @Override
    public List<Map<String, String>> references(Object result) {
        if (!(result instanceof DiarySearchResult searchResult) || searchResult.diaries() == null) {
            return List.of();
        }
        List<Map<String, String>> items = new ArrayList<>();
        for (DiarySearchResult.DiarySummary diary : searchResult.diaries()) {
            items.add(Map.of(
                    "type", "tool_memory",
                    "diaryId", diary.id() != null ? diary.id().toString() : "",
                    "date", diary.date() != null ? diary.date().toString() : "",
                    "snippet", ToolSnippets.compact(diary.snippet()),
                    "toolName", displayName()));
        }
        return items;
    }

    private static int normalizedLimit(Integer limit) {
        if (limit == null) return DEFAULT_LIMIT;
        return Math.min(MAX_LIMIT, Math.max(1, limit));
    }

    /** 全部参数可选：不带任何条件时就是「我最近的日记」。 */
    public record RecentDiaryListRequest(Integer limit, LocalDate startDate, LocalDate endDate) {
    }
}
