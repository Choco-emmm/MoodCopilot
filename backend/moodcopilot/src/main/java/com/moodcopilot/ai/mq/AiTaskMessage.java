package com.moodcopilot.ai.mq;

import java.io.Serializable;

/**
 * 封装投递到 Redis Stream 的 AI 任务消息
 */
public record AiTaskMessage(
        String taskType,
        Long diaryId,
        Long userId
) implements Serializable {
    public static final String TYPE_DIARY_ANALYSIS = "DIARY_ANALYSIS";
}
