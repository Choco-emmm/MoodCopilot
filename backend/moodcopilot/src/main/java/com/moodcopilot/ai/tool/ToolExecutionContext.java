package com.moodcopilot.ai.tool;

import com.moodcopilot.entity.UserEntity;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Sinks;

/**
 * 工具执行上下文。
 * <p>
 * sseSink 仅在流式路径存在；非流式与推理的同步路径传 null，引用帧会被静默跳过。
 */
public record ToolExecutionContext(Authentication auth, Sinks.Many<String> sseSink) {

    public static ToolExecutionContext of(Authentication auth) {
        return new ToolExecutionContext(auth, null);
    }

    public UserEntity user() {
        return auth == null ? null : (UserEntity) auth.getPrincipal();
    }

    /** 无认证时返回 null；调用方按原行为解包，缺失即抛 NPE。 */
    public Long userId() {
        UserEntity user = user();
        return user == null ? null : user.getId();
    }
}
