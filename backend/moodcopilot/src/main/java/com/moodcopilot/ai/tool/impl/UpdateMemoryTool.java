package com.moodcopilot.ai.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.ai.MemoryOrchestrator;
import com.moodcopilot.ai.MemoryWriteResult;
import com.moodcopilot.ai.tool.ChatTool;
import com.moodcopilot.ai.tool.ToolExecutionContext;
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
        properties.put("expectedValue", Map.of("type", "string", "description", "读取到的原始记忆正文，原样填写"));
        properties.put("newValue", Map.of("type", "string", "description", "建议的新正文，1–500 字"));
        properties.put("evidence", Map.of("type", "string", "description", "支持改动的用户原文，1–160 字"));
        return properties;
    }
    @Override public List<String> required() { return List.of("memoryId", "expectedValue", "newValue", "evidence"); }
    @Override public Map<String,Object> approvalPreview(ObjectMapper mapper, String json, ToolExecutionContext context) throws Exception {
        var request = mapper.readValue(json, requestType());
        if (request.memoryId() == null) throw new IllegalArgumentException("缺少目标记忆");
        return orchestrator.prepareUpdate(context.userId(), context.runId(), context.toolCallId(), request.memoryId(),
                request.expectedValue(), request.newValue(), request.evidence(), source(context));
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
