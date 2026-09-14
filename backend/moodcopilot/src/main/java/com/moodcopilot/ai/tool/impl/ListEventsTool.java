package com.moodcopilot.ai.tool.impl;

import com.moodcopilot.ai.tool.ChatTool;
import com.moodcopilot.ai.tool.ToolExecutionContext;
import com.moodcopilot.ai.tool.ToolSnippets;
import com.moodcopilot.event.LifeEventService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 列出用户的重要事件，让模型能把用户口中的事件名解析成 eventId。
 * <p>
 * 存在的理由：{@link UpdateEventStatusTool} 需要 eventId，而事件只有在用户显式引用
 * （「聊聊这件事」）时才进入上下文。没有这个工具，用户在自由对话里说
 * 「帮我把蓝桥杯标成已完成」时，模型只能拒绝——它根本不知道有哪些事件。
 */
public class ListEventsTool extends ChatTool<ListEventsTool.ListEventsRequest> {

    public static final String NAME = "listEventsFunction";

    private static final int MAX_EVENTS = 20;
    private static final int MAX_DESCRIPTION_CHARS = 120;

    private final LifeEventService lifeEventService;

    public ListEventsTool(LifeEventService lifeEventService) {
        this.lifeEventService = lifeEventService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "列出当前登录用户的重要事件（含事件 ID、标题、状态、目标日期）。"
                + "当用户用名称提到某件事、而你需要 eventId 才能继续时（例如要把某件事标记为已跟进），必须先调用本工具拿到 ID，"
                + "不要让用户自己提供编号。keyword 可选，用于按标题过滤；传空字符串则返回全部事件。";
    }

    @Override
    public Class<ListEventsRequest> requestType() {
        return ListEventsRequest.class;
    }

    @Override
    public LinkedHashMap<String, Object> properties() {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put("keyword", Map.of("type", "string", "description", "按标题过滤的关键词，传空字符串返回全部事件"));
        return props;
    }

    @Override
    public List<String> required() {
        return List.of("keyword");
    }

    @Override
    public Object execute(ListEventsRequest request, ToolExecutionContext context) {
        long userId = context.userId();
        String keyword = request.keyword() != null ? request.keyword().trim() : "";

        List<EventItem> items = new ArrayList<>();
        for (LifeEventService.LifeEventView event : lifeEventService.listUserEvents(userId)) {
            if (items.size() >= MAX_EVENTS) {
                break;
            }
            if (!keyword.isBlank() && (event.title() == null
                    || !event.title().toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)))) {
                continue;
            }
            items.add(new EventItem(event.id(), event.title(), event.status(), event.targetDate(),
                    clip(event.description())));
        }

        String note;
        if (items.isEmpty()) {
            note = keyword.isBlank() ? "该用户当前没有任何重要事件记录" : "没有找到标题包含「" + keyword + "」的事件";
        } else {
            note = "已返回 " + items.size() + " 个事件；如需修改状态，用其中的 id 调用 updateEventStatusFunction";
        }
        return new ListEventsResult(items.size(), items, note);
    }

    private String clip(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > MAX_DESCRIPTION_CHARS
                ? normalized.substring(0, MAX_DESCRIPTION_CHARS) + "…"
                : normalized;
    }

    /**
     * 供前端引用面板展示。
     * <p>
     * 刻意不带 diaryId —— 前端的「N 条记录」是按「有 diaryId 就计入日记记忆」过滤的，
     * 带上会把事件串进日记计数里。
     */
    @Override
    public List<Map<String, String>> references(Object result) {
        if (!(result instanceof ListEventsResult eventsResult) || eventsResult.events() == null) {
            return List.of();
        }
        List<Map<String, String>> items = new ArrayList<>();
        for (EventItem event : eventsResult.events()) {
            String title = event.title() != null ? event.title() : "";
            Map<String, String> item = new LinkedHashMap<>();
            item.put("type", "event_memory");
            item.put("eventId", event.id() != null ? event.id().toString() : "");
            item.put("title", title);
            item.put("status", event.status() != null ? event.status() : "");
            item.put("date", event.targetDate() != null ? event.targetDate() : "");
            item.put("snippet", ToolSnippets.compact(title + "（" + statusLabel(event.status()) + "）"));
            item.put("toolName", displayName());
            items.add(item);
        }
        return items;
    }

    private String statusLabel(String status) {
        if ("FOLLOWED_UP".equals(status)) {
            return "已跟进";
        }
        if ("PENDING".equals(status)) {
            return "待跟进";
        }
        return "状态未知";
    }

    public record ListEventsRequest(String keyword) {
    }

    public record ListEventsResult(int count, List<EventItem> events, String note) {
    }

    public record EventItem(Long id, String title, String status, String targetDate, String description) {
    }
}
