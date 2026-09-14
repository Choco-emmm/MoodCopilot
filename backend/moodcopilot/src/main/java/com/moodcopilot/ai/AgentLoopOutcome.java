package com.moodcopilot.ai;

import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 一次 Agent Loop 的结果视图。
 * <p>
 * 「什么算正文」只有这里一份实现：{@link #reply()} 按构造不含推理分片，
 * 所以调用方无论走哪条路径、读哪个累加器，都不会把 [[REASONING]] 写进历史。
 * 累加发生在流结束后才可读。
 */
public final class AgentLoopOutcome {

    /** DeepSeekClient 用这个前缀把 reasoning_content 复用成 TextChunk。 */
    static final String REASONING_MARKER = "[[REASONING]]";

    private final StringBuilder reply = new StringBuilder();
    private final StringBuilder reasoning = new StringBuilder();
    private final List<Map<String, String>> toolReferences = Collections.synchronizedList(new ArrayList<>());
    private volatile int toolCallCount;
    private Flux<String> chunks = Flux.empty();

    void attachChunks(Flux<String> chunks) {
        this.chunks = chunks == null ? Flux.empty() : chunks;
    }

    void appendText(String chunk) {
        if (chunk == null) {
            return;
        }
        if (chunk.startsWith(REASONING_MARKER)) {
            reasoning.append(chunk.substring(REASONING_MARKER.length()));
        } else {
            reply.append(chunk);
        }
    }

    void recordToolCalls(int count, List<Map<String, String>> references) {
        toolCallCount += count;
        if (references != null && !references.isEmpty()) {
            toolReferences.addAll(references);
        }
    }

    /** 原始分片，保留 [[REASONING]] 标记 —— 只给 SSE 客户端消费。 */
    public Flux<String> chunks() {
        return chunks;
    }

    /** 剥离标记后的正文，给持久化与记忆抽取消费。 */
    public String reply() {
        return reply.toString();
    }

    public String reasoning() {
        return reasoning.toString();
    }

    public int toolCallCount() {
        return toolCallCount;
    }

    public List<Map<String, String>> toolReferences() {
        return List.copyOf(toolReferences);
    }
}
