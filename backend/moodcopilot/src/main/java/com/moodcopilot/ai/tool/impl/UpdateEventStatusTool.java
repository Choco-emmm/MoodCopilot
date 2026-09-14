package com.moodcopilot.ai.tool.impl;

import com.moodcopilot.ai.tool.ChatTool;
import com.moodcopilot.ai.tool.ToolExecutionContext;
import com.moodcopilot.event.LifeEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class UpdateEventStatusTool extends ChatTool<UpdateEventStatusTool.UpdateEventStatusRequest> {

    private static final Logger log = LoggerFactory.getLogger(UpdateEventStatusTool.class);

    public static final String NAME = "updateEventStatusFunction";

    private static final String STATUS_FOLLOWED_UP = "FOLLOWED_UP";
    private static final String STATUS_PENDING = "PENDING";
    private static final int MAX_NOTE_LENGTH = 500;

    private final LifeEventService lifeEventService;

    public UpdateEventStatusTool(LifeEventService lifeEventService) {
        this.lifeEventService = lifeEventService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "将当前登录用户的某个重要事件标记为已跟进（FOLLOWED_UP）或重新激活为待跟进（PENDING）。"
                + "【调用前提】必须满足以下全部条件才能调用："
                + "1. 用户在本轮对话中明确表达了某个事件已经结束、解决、完成或不再需要跟进的意图（如'这件事解决了'、'不用再管了'、'已经完成了'）；"
                + "2. 你已经向用户确认了要操作的具体事件名称，并得到了用户的明确同意；"
                + "3. 你已经拿到了事件的 eventId —— 若上下文中没有，先调用 listEventsFunction 按名称查到 ID，不要让用户自己提供编号。"
                + "【禁止调用】用户仅在讨论事件进展、倾诉情绪、寻求建议时，不得调用此工具。"
                + "note 参数可选，用于记录用户对该事件的最终总结或跟进说明。";
    }

    @Override
    public Class<UpdateEventStatusRequest> requestType() {
        return UpdateEventStatusRequest.class;
    }

    @Override
    public LinkedHashMap<String, Object> properties() {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put("eventId", Map.of("type", "integer", "description", "要更新状态的事件 ID（从事件上下文中获取）"));
        props.put("status", Map.of(
                "type", "string",
                "enum", List.of(STATUS_FOLLOWED_UP, STATUS_PENDING),
                "description", "新状态：FOLLOWED_UP=已跟进/已解决，PENDING=重新标为待跟进"));
        props.put("note", Map.of("type", "string", "description", "可选的跟进备注，记录用户对该事件的总结说明，最多 500 字"));
        return props;
    }

    @Override
    public List<String> required() {
        return List.of("eventId", "status", "note");
    }

    @Override
    public Object execute(UpdateEventStatusRequest request, ToolExecutionContext context) {
        long userId = context.userId();
        if (request.eventId() == null) {
            return new UpdateEventStatusResult(false, null, "缺少 eventId，无法更新事件状态");
        }
        String status = request.status() != null
                ? request.status().toUpperCase(Locale.ROOT).trim()
                : STATUS_FOLLOWED_UP;
        if (!Set.of(STATUS_PENDING, STATUS_FOLLOWED_UP).contains(status)) {
            return new UpdateEventStatusResult(false, null, "状态值不合法，只允许 PENDING 或 FOLLOWED_UP");
        }
        String note = request.note() != null && !request.note().isBlank()
                ? request.note().trim().substring(0, Math.min(request.note().trim().length(), MAX_NOTE_LENGTH))
                : null;

        try {
            LifeEventService.LifeEventView updated =
                    lifeEventService.updateEventStatus(userId, request.eventId(), status, note);
            String message = STATUS_FOLLOWED_UP.equals(status)
                    ? "已将「" + updated.title() + "」标记为已跟进"
                    : "已将「" + updated.title() + "」重新标记为待跟进";
            log.info("AI工具更新事件状态 userId={} eventId={} newStatus={}", userId, request.eventId(), status);
            return new UpdateEventStatusResult(true, updated.status(), message);
        } catch (ResponseStatusException e) {
            log.warn("AI工具更新事件状态失败 userId={} eventId={} reason={}", userId, request.eventId(), e.getReason());
            return new UpdateEventStatusResult(false, null,
                    "更新失败：" + (e.getReason() != null ? e.getReason() : "事件不存在或无权操作"));
        }
    }

    public record UpdateEventStatusRequest(Long eventId, String status, String note) {
    }

    public record UpdateEventStatusResult(boolean success, String newStatus, String message) {
    }
}
