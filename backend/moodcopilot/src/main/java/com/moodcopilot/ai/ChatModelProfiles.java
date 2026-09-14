package com.moodcopilot.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * flash / pro 两个聊天档位的参数。
 * <p>
 * 直接复用既有的配置键，避免引入第二套模型配置造成漂移：
 * flash 用 Spring AI 原本读的 spring.ai.openai.chat.options.*，
 * pro 用 DeepSeekClient 原本读的 DEEPSEEK_REASONING_MODEL / spring.ai.reasoning.max-tokens。
 * <p>
 * temperature 两个档位都留 null（沿用端点默认，与改造前一致）；
 * exposeReasoning 仅 pro 为 true —— flash 此前不暴露思考过程，保持行为不变。
 */
@Component
public class ChatModelProfiles {

    private static final int MIN_MAX_TOKENS = 1024;
    private static final int DEFAULT_MAX_DEPTH = 5;

    private final AgentLoopOptions flash;
    private final AgentLoopOptions pro;

    public ChatModelProfiles(
            @Value("${spring.ai.openai.chat.options.model:deepseek-v4-flash}") String flashModel,
            @Value("${spring.ai.openai.chat.options.max-tokens:32768}") int flashMaxTokens,
            @Value("${DEEPSEEK_REASONING_MODEL:deepseek-v4-pro}") String proModel,
            @Value("${spring.ai.reasoning.max-tokens:32768}") int proMaxTokens) {
        this.flash = new AgentLoopOptions(
                normalize(flashModel, "deepseek-v4-flash"),
                Math.max(MIN_MAX_TOKENS, flashMaxTokens),
                null,
                null,
                DEFAULT_MAX_DEPTH,
                false,
                "CHAT_STREAM",
                "FLASH");
        this.pro = new AgentLoopOptions(
                normalize(proModel, "deepseek-v4-pro"),
                Math.max(MIN_MAX_TOKENS, proMaxTokens),
                null,
                "high",
                DEFAULT_MAX_DEPTH,
                true,
                "CHAT_AGENT_STREAM",
                "PRO");
    }

    public AgentLoopOptions flash() {
        return flash;
    }

    public AgentLoopOptions pro() {
        return pro;
    }

    private static String normalize(String model, String fallback) {
        return model == null || model.isBlank() ? fallback : model.trim();
    }
}
