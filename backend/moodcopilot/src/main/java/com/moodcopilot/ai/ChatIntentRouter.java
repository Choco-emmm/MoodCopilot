package com.moodcopilot.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Component
public class ChatIntentRouter {

    private static final Logger log = LoggerFactory.getLogger(ChatIntentRouter.class);
    private static final String INTENT_CACHE_PREFIX = "intent:reasoning:";
    private static final Duration LLM_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private static final String CLASSIFIER_SYSTEM_PROMPT = """
            你是一个意图分类器，用于判断用户的输入是否需要深度推理模型处理。
            需要深度推理的情况：要求分析情绪成因、梳理复杂人际关系、对比过去与现在的变化、给出个性化建议、多步骤规划、或涉及矛盾/纠结的心理状态。
            不需要深度推理的情况：简单情绪表达（如"今天好累"）、日常问候、单纯倾诉不要求分析、事实性询问。
            只需输出 true 或 false，不要解释。

            示例：
            输入：今天加班到十点，好累啊。
            输出：false

            输入：为什么我每次和父母聊天都会莫名烦躁？我想搞清楚这个模式。
            输出：true

            输入：谢谢你听我说这些。
            输出：false

            输入：帮我分析一下，我最近两周的情绪波动是不是和工作压力有关，以及我该怎么调整。
            输出：true

            输入：刚才吃了个很好吃的小蛋糕，开心。
            输出：false

            输入：我觉得自己陷入了一个怪圈：越想交朋友越害怕社交，能不能帮我梳理一下这个心理？
            输出：true""";

    private final ChatClient analysisChatClient;
    private final StringRedisTemplate redisTemplate;
    private final Executor aiExecutor;

    public ChatIntentRouter(ChatClient analysisChatClient, StringRedisTemplate redisTemplate,
            Executor aiExecutor) {
        this.analysisChatClient = analysisChatClient;
        this.redisTemplate = redisTemplate;
        this.aiExecutor = aiExecutor;
    }

    /**
     * 保留旧签名以保持向后兼容（无 conversationId 时不做缓存防抖）。
     */
    public boolean shouldUseReasoning(String message, List<String> refs, String memoryBackground) {
        return shouldUseReasoning(message, refs, memoryBackground, null);
    }

    /**
     * 主路由入口：先尝试大模型语义路由，失败或超时则降级为规则路由。
     * conversationId 非空时会启用 Redis 缓存防抖——5 分钟内曾触发 reasoning 的会话，后续短消息会提高走 reasoning 的概率。
     */
    public boolean shouldUseReasoning(String message, List<String> refs, String memoryBackground, Long conversationId) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }

        // 工具调用意图检测：当用户意图需要查询后台数据时，必须走常规模型分支（挂载了 functions）
        // 优先级高于所有其他路由规则，确保 AI 有工具能力可用
        if (hasToolCallingIntent(message)) {
            log.info("聊天路由结果：normal（强制，检测到工具调用意图）");
            return false;
        }

        // 携带引用日记时强制走深度分析（reasoning 模型），确保 AI 聚焦引用内容做精准回应
        if (refs != null && !refs.isEmpty()) {
            log.info("聊天路由结果：reasoning（强制，因携带 {} 条引用日记）", refs.size());
            if (conversationId != null) {
                markReasoning(conversationId);
            }
            return true;
        }

        // 强惯性锁定：5分钟内曾触发推理模型的会话直接锁定，避免模型频繁横跳
        if (conversationId != null && hasRecentReasoning(conversationId)) {
            log.info("聊天路由结果：reasoning（惯性锁定）");
            markReasoning(conversationId);
            return true;
        }

        Boolean llmResult = trySemanticRoute(message);
        if (llmResult != null) {
            if (llmResult && conversationId != null) {
                markReasoning(conversationId);
            }
            return llmResult;
        }

        return fallbackRoutingStrategy(message, refs, memoryBackground);
    }

    /**
     * 大模型语义路由：超时 2 秒，异常即返回 null 表示降级。
     */
    private Boolean trySemanticRoute(String message) {
        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
            try {
                String response = analysisChatClient.prompt()
                        .system(CLASSIFIER_SYSTEM_PROMPT)
                        .user(message)
                        .call()
                        .content();
                if (response != null) {
                    return response.trim().toLowerCase().contains("true");
                }
            } catch (Exception e) {
                log.debug("LLM 意图分类调用失败: {}", e.getMessage());
                throw new RuntimeException(e);
            }
            return false;
        }, aiExecutor);

        try {
            return future.get(LLM_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            log.debug("LLM 意图分类超时 ({}ms)，降级为规则路由", LLM_TIMEOUT.toMillis());
            return null;
        } catch (Exception e) {
            log.debug("LLM 意图分类异常，降级为规则路由: {}", e.getMessage());
            return null;
        }
    }

    private boolean hasRecentReasoning(Long conversationId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(INTENT_CACHE_PREFIX + conversationId));
        } catch (Exception e) {
            return false;
        }
    }

    private void markReasoning(Long conversationId) {
        try {
            redisTemplate.opsForValue().set(INTENT_CACHE_PREFIX + conversationId, "1", CACHE_TTL);
        } catch (Exception e) {
            log.debug("标记 reasoning 缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 降级规则路由：基于字数、关键词、标点等启发式规则打分。
     */
    boolean fallbackRoutingStrategy(String message, List<String> refs, String memoryBackground) {
        String normalized = message.trim();

        int score = 0;

        if (normalized.length() >= 80) {
            score++;
        }
        if (normalized.length() >= 160) {
            score++;
        }

        String lower = normalized.toLowerCase();
        if (containsAny(lower, List.of("为什么", "怎么", "如何", "原因", "分析", "总结", "梳理", "对比", "区别", "推演", "复盘", "深入", "详细",
                "规划", "建议", "判断"))) {
            score += 2;
        }

        if (containsAny(lower, List.of("帮我想", "帮我分析", "帮我总结", "给我建议", "帮我梳理", "帮我看看", "帮我判断"))) {
            score += 2;
        }

        long questionMarks = normalized.chars().filter(ch -> ch == '？' || ch == '?').count();
        if (questionMarks >= 2) {
            score++;
        }

        int sentenceCount = countSentences(normalized);
        if (sentenceCount >= 3) {
            score++;
        }

        if (refs != null && !refs.isEmpty() && normalized.length() >= 60) {
            score++;
        }

        if (memoryBackground != null && !memoryBackground.isBlank() && normalized.length() >= 100) {
            score++;
        }

        return score >= 2;
    }

    /**
     * 检测用户消息是否包含需要调用后台数据工具的意图关键词。
     * 这些请求必须走常规模型分支（挂载了 functions），否则 AI 会编造数据或拒绝查询。
     */
    private boolean hasToolCallingIntent(String message) {
        String lower = message.trim().toLowerCase();
        return containsAny(lower, List.of(
                "报告", "总结", "回顾", "统计数据", "数据总结", "趋势",
                "看看我的", "最近表现", "最近情况", "我的记录", "历史记录",
                "查询", "查一下", "调取", "帮我查"));
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private int countSentences(String text) {
        int count = 0;
        for (char ch : text.toCharArray()) {
            if (ch == '。' || ch == '！' || ch == '？' || ch == '.' || ch == '!' || ch == '?') {
                count++;
            }
        }
        return count;
    }
}
