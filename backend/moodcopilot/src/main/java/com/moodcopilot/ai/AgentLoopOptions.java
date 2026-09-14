package com.moodcopilot.ai;

/**
 * 一次 Agent Loop 的模型参数。所有聊天模型共用同一个循环，差异全在这里。
 *
 * @param model           模型名（flash / pro）
 * @param maxTokens       输出预算（含思考过程）
 * @param temperature     null 表示不发送该字段，沿用端点默认
 * @param reasoningEffort null 表示不发送；pro 传 "high"
 * @param maxDepth        工具调用递归深度上限
 * @param exposeReasoning 是否把 [[REASONING]] 分片透传给客户端
 * @param logType         AiCallTiming 的日志类型
 * @param modelLabel      AiCallTiming / 日志里的模型标签
 */
public record AgentLoopOptions(
        String model,
        Integer maxTokens,
        Double temperature,
        String reasoningEffort,
        int maxDepth,
        boolean exposeReasoning,
        String logType,
        String modelLabel) {
}
