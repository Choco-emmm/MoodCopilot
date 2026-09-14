package com.moodcopilot.ai.tool;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Sinks;

/**
 * Spring AI {@link ToolContext} → {@link ToolExecutionContext} 的桥。
 * <p>
 * 这是整个 ai.tool 包里唯一知道 Spring AI 的文件。flash 路径仍走 Spring AI 的
 * FunctionCallback 时由它适配；flash 迁到自研循环后，本文件与其全部引用一并删除。
 */
public final class LegacyToolContextAdapter {

    public static final String AUTH_KEY = "auth";
    public static final String SSE_SINK_KEY = "sseSink";

    private LegacyToolContextAdapter() {
    }

    public static ToolExecutionContext from(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return ToolExecutionContext.of(null);
        }
        Authentication auth = (Authentication) toolContext.getContext().get(AUTH_KEY);
        @SuppressWarnings("unchecked")
        Sinks.Many<String> sink = (Sinks.Many<String>) toolContext.getContext().get(SSE_SINK_KEY);
        return new ToolExecutionContext(auth, sink);
    }
}
