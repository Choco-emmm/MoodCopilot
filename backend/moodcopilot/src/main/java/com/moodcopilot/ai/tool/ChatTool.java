package com.moodcopilot.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一个聊天工具的完整声明：元数据 + JSON schema + 执行体。
 * <p>
 * 实现类不接触 SecurityContextHolder —— 认证上下文的设置与清理统一由
 * {@link ChatToolRegistry#execute} 负责，因为工具运行在 Reactor/Netty 线程上，
 * thread-local 需要由调用方显式注入。
 */
public abstract class ChatTool<REQ> {

    /** 线名，与模型看到的 function name 一致。 */
    public abstract String name();

    public abstract String description();

    public abstract Class<REQ> requestType();

    public abstract LinkedHashMap<String, Object> properties();

    public abstract List<String> required();

    public abstract Object execute(REQ request, ToolExecutionContext context) throws Exception;

    /** 供前端引用面板展示的条目；不产生引用帧的工具返回空列表。 */
    public List<Map<String, String>> references(Object result) {
        return List.of();
    }

    /**
     * 执行前是否需要用户点确认。
     * <p>
     * 这是「以后按用户配权限」的接缝：当前由工具自己声明，将来可以改成按用户订阅
     * 或按风险等级查策略表 —— 图那边的中断逻辑只认这个返回值，不认具体工具。
     */
    public boolean requiresApproval() {
        return false;
    }

    /**
     * 中断时推给前端的预览数据，默认没有可预览的内容。
     * <p>
     * 只有 {@link #requiresApproval()} 为 true 的工具才需要它 —— 用户得先看清要改什么
     * 才能决定批不批。返回空 Map 表示「只说工具名就行」。
     */
    public Map<String, Object> approvalPreview(ObjectMapper mapper, String argumentsJson,
            ToolExecutionContext context) throws Exception {
        return Map.of();
    }

    /**
     * 前端展示名：去掉 Function 后缀。
     * 与既有 [[TOOL_EVENT]] 帧里的 toolName 约定一致（如 graphSearchFunction → graphSearch）。
     */
    public String displayName() {
        return name().replaceAll("Function$", "");
    }

    public final Object run(ObjectMapper mapper, String argumentsJson, ToolExecutionContext context) throws Exception {
        String json = (argumentsJson == null || argumentsJson.isBlank()) ? "{}" : argumentsJson;
        return execute(mapper.readValue(json, requestType()), context);
    }

    /** 调用方已持有解析好的请求对象时使用，避免一次多余的 JSON 往返。 */
    @SuppressWarnings("unchecked")
    public final Object runParsed(Object parsedRequest, ToolExecutionContext context) throws Exception {
        return execute((REQ) parsedRequest, context);
    }
}
