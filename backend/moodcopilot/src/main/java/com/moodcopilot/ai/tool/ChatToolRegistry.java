package com.moodcopilot.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天工具的唯一注册表：声明顺序即发送给模型的顺序。
 * <p>
 * 刻意不使用 Spring 注解 —— 由 {@code ChatToolConfiguration} 显式构造，
 * 保证顺序确定且便于单测。
 */
public class ChatToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ChatToolRegistry.class);

    private final ObjectMapper objectMapper;
    private final Map<String, ChatTool<?>> byName;

    public ChatToolRegistry(ObjectMapper objectMapper, List<ChatTool<?>> tools) {
        this.objectMapper = objectMapper;
        Map<String, ChatTool<?>> ordered = new LinkedHashMap<>();
        for (ChatTool<?> tool : tools) {
            if (ordered.put(tool.name(), tool) != null) {
                throw new IllegalStateException("注册表中存在重复的工具名: " + tool.name());
            }
        }
        this.byName = Collections.unmodifiableMap(ordered);
    }

    public List<ChatTool<?>> tools() {
        return List.copyOf(byName.values());
    }

    /** OpenAI 兼容的 tools 数组，字段与顺序和模型侧约定保持一致。 */
    public List<Map<String, Object>> schemas() {
        List<Map<String, Object>> schemas = new ArrayList<>(byName.size());
        for (ChatTool<?> tool : byName.values()) {
            schemas.add(toSchema(tool));
        }
        return schemas;
    }

    public String description(String name) {
        return require(name).description();
    }

    public Class<?> requestType(String name) {
        return require(name).requestType();
    }

    public Object execute(String name, String argumentsJson, ToolExecutionContext context) throws Exception {
        ChatTool<?> tool = require(name);
        return inSecurityContext(context, () -> tool.run(objectMapper, argumentsJson, context));
    }

    public Object executeParsed(String name, Object parsedRequest, ToolExecutionContext context) throws Exception {
        ChatTool<?> tool = require(name);
        return inSecurityContext(context, () -> tool.runParsed(parsedRequest, context));
    }

    public List<Map<String, String>> references(String name, Object result) {
        return require(name).references(result);
    }

    /**
     * 把引用条目推成 [[TOOL_EVENT]] 帧；sink 缺失时只返回条目不发帧。
     * 返回值供 Agent Loop 记进 AgentLoopOutcome，避免调用方再算一次 references()。
     */
    public List<Map<String, String>> emit(String name, Object result, Sinks.Many<String> sink) {
        if (result == null) {
            return List.of();
        }
        List<Map<String, String>> items;
        try {
            items = references(name, result);
        } catch (Exception e) {
            log.warn("构建工具引用条目失败 {}: {}", name, e.getMessage());
            return List.of();
        }
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        if (sink != null) {
            try {
                Map<String, Object> event = Map.of("type", "tool_references", "items", items);
                sink.tryEmitNext("[[TOOL_EVENT]]" + objectMapper.writeValueAsString(event));
            } catch (Exception e) {
                log.warn("推送工具引用事件失败 {}: {}", name, e.getMessage());
            }
        }
        return items;
    }

    private ChatTool<?> require(String name) {
        ChatTool<?> tool = byName.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("未知的工具函数: " + name);
        }
        return tool;
    }

    /**
     * 工具跑在 Reactor/Netty 线程上，DiaryService 等依赖 currentUser() 读 SecurityContextHolder，
     * 因此认证上下文必须在这里注入并在结尾清理 —— 各工具实现不接触它。
     */
    private Object inSecurityContext(ToolExecutionContext context, ToolInvocation invocation) throws Exception {
        Authentication auth = context == null ? null : context.auth();
        if (auth != null) {
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        try {
            return invocation.invoke();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Map<String, Object> toSchema(ChatTool<?> tool) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", tool.properties());
        parameters.put("required", tool.required());
        parameters.put("additionalProperties", false);

        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", tool.name());
        function.put("description", tool.description());
        function.put("parameters", parameters);
        function.put("strict", true);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "function");
        schema.put("function", function);
        return schema;
    }

    @FunctionalInterface
    private interface ToolInvocation {
        Object invoke() throws Exception;
    }
}
