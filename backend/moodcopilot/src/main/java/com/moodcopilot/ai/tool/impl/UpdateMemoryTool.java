package com.moodcopilot.ai.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.ai.MemoryOrchestrator;
import com.moodcopilot.ai.MemoryWriteResult;
import com.moodcopilot.ai.tool.ChatTool;
import com.moodcopilot.ai.tool.ToolExecutionContext;
import com.moodcopilot.entity.UserProfileMemoryEntity;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

public final class UpdateMemoryTool extends ChatTool<UpdateMemoryTool.UpdateMemoryRequest> {
    public static final String NAME = "updateMemoryFunction";
    private final MemoryOrchestrator orchestrator;
    public UpdateMemoryTool(MemoryOrchestrator orchestrator) { this.orchestrator = orchestrator; }
    @Override public String name() { return NAME; }
    @Override public String description() {
        return "修改用户现有的长期记忆正文。用户明确要求修改或本轮明确更正旧事实时可提出建议，系统展示前后差异，用户确认后才保存。"
                + "先从用户当前记忆中确定唯一 memoryId 和原值 expectedValue，指代不明确则先询问。"
                + "evidence 必须是用户本轮消息或主动引用中支持改动的短连续原文，不能使用助手话语、推测或临时情绪。"
                + "不能修改属性名、类别、核心标记，不能删除；拒绝或失败均不能声称保存成功。";
    }
    @Override public Class<UpdateMemoryRequest> requestType() { return UpdateMemoryRequest.class; }
    @Override public boolean requiresApproval() { return true; }
    @Override public LinkedHashMap<String,Object> properties() {
        var properties = new LinkedHashMap<String,Object>();
        properties.put("memoryId", Map.of("type", "integer", "description", "当前正式记忆的 ID"));
        properties.put("expectedValue", Map.of("type", "string", "description", "读取到的原始记忆正文（attributeValue 全文），必须一字不差地原样复制，不可省略或改写"));
        properties.put("newValue", Map.of("type", "string", "description", "建议的新正文，1–500 字"));
        properties.put("evidence", Map.of("type", "string", "description", "必须逐字摘录用户本轮最新消息或本轮主动引用中支持改动的连续原文片段（1–160 字），不能摘录历史轮次或旧记忆"));
        return properties;
    }
    @Override public List<String> required() { return List.of("memoryId", "expectedValue", "newValue", "evidence"); }
    @Override public Map<String,Object> approvalPreview(ObjectMapper mapper, String json, ToolExecutionContext context) throws Exception {
        var request = mapper.readValue(json == null || json.isBlank() ? "{}" : json, requestType());
        if (request.memoryId() == null) throw new IllegalArgumentException("缺少目标记忆");
        try {
            return orchestrator.prepareUpdate(context.userId(), context.runId(), context.toolCallId(), request.memoryId(),
                    request.expectedValue(), request.newValue(), request.evidence(), source(context));
        } catch (ResponseStatusException rse) {
            if (context == null || context.userId() == null) throw rse;
            UserProfileMemoryEntity old = orchestrator.detail(context.userId(), request.memoryId());
            if (old == null) throw rse;
            var preview = new LinkedHashMap<String, Object>();
            preview.put("attributeKey", old.getAttributeKey());
            preview.put("oldValue", old.getAttributeValue() == null ? "" : old.getAttributeValue());
            preview.put("newValue", request.newValue() == null ? "" : request.newValue());
            preview.put("memoryId", request.memoryId());
            preview.put("kind", "updated");
            preview.put("editableNewValue", false);
            preview.put("canApprove", false);
            preview.put("previewError", previewErrorMessage(rse.getReason()));
            return preview;
        }
    }

    private static String previewErrorMessage(String reason) {
        if (reason == null || reason.isBlank()) return "这条修改未通过校验，请暂不修改后重试。";
        if ("记忆已变化，请重新提出修改".equals(reason)) {
            return "记忆原值与当前版本不一致，无法直接保存，请暂不修改后让 AI 重新核对再试。";
        }
        if ("缺少有效的用户原文依据".equals(reason)) {
            return "缺少你在本轮对话中的明确原话依据，请暂不修改并在消息中具体说明修改要求后再试。";
        }
        return reason;
    }
    @Override public Object execute(UpdateMemoryRequest request, ToolExecutionContext context) {
        MemoryWriteResult result = orchestrator.applyUpdate(context.userId(), context.runId(), context.toolCallId(),
                request.memoryId(), request.newValue(), context.conversationId());
        var response = new LinkedHashMap<String,Object>();
        response.put("success", result.success()); response.put("status", result.status());
        response.put("reasonCode", result.reasonCode()); response.put("memoryId", result.memoryId());
        response.put("executed", result.executed());
        if (result.success()) response.put("newValue", com.moodcopilot.ai.MemoryWritePolicy.text(request.newValue()));
        return response;
    }
    public void reject(ToolExecutionContext context, String reasonCode) {
        orchestrator.rejectUpdate(context.userId(), context.runId(), context.toolCallId(), reasonCode);
    }

    static String source(ToolExecutionContext context) {
        return context.userMessage() + "\n" + String.join("\n", context.userReferences());
    }
    public record UpdateMemoryRequest(Long memoryId, String expectedValue, String newValue, String evidence) {}
}
