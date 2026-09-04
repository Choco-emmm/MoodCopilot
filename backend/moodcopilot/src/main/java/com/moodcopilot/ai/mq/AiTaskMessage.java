package com.moodcopilot.ai.mq;

import java.io.Serializable;

/** RabbitMQ 只传任务索引，不携带日记正文、聊天内容或其他私人数据。 */
public record AiTaskMessage(
        String taskId,
        Long userId,
        String taskType,
        String aggregateId
) implements Serializable {
    public static final String TYPE_DIARY_ANALYSIS = "DIARY_ANALYSIS";
    public static final String TYPE_MEMORY_EXTRACTION = "MEMORY_EXTRACTION";
    public static final String TYPE_LIFE_EVENT_EXTRACTION = "LIFE_EVENT_EXTRACTION";
    public static final String TYPE_GRAPH_EXTRACTION = "GRAPH_EXTRACTION";
    public static final String TYPE_DIARY_RAG_INDEX = "DIARY_RAG_INDEX";
    public static final String TYPE_GRAPH_RAG_INDEX = "GRAPH_RAG_INDEX";
    public static final String TYPE_MEMORY_RAG_INDEX = "MEMORY_RAG_INDEX";
    public static final String TYPE_REPORT_INVALIDATION = "REPORT_INVALIDATION";
    public static final String TYPE_NOTIFICATION = "NOTIFICATION";
    public static final String TYPE_LIFE_CHAPTER_REFRESH = "LIFE_CHAPTER_REFRESH";
}
