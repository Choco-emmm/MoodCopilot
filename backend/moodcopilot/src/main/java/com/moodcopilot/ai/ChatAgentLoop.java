package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.ai.tool.ChatToolRegistry;
import com.moodcopilot.ai.tool.ToolExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 唯一的 Agent Loop：flash 与 pro 都走这里，模型差异由 {@link AgentLoopOptions} 参数化。
 * <p>
 * 之所以自研而不复用 Spring AI 的 ChatClient，是为了直接拿到 reasoning_content
 * （见 {@link DeepSeekClient} 的说明）。工具 schema 与执行都来自 {@link ChatToolRegistry}。
 * <p>
 * 推理分片的剥离在 {@link AgentLoopOutcome} 内部完成，本类只负责驱动与转发。
 */
@Component
public class ChatAgentLoop {

    private static final Logger log = LoggerFactory.getLogger(ChatAgentLoop.class);

    private static final int MAX_ERROR_MESSAGE_CHARS = 200;

    private final DeepSeekClient deepSeekClient;
    private final ChatToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    public ChatAgentLoop(DeepSeekClient deepSeekClient, ChatToolRegistry toolRegistry, ObjectMapper objectMapper) {
        this.deepSeekClient = deepSeekClient;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * 驱动一次完整的对话（含工具调用往返）。
     * <p>
     * 返回的 outcome 其 {@code reply()} / {@code reasoning()} 要在订阅并消费完 {@code chunks()} 之后才可读。
     * sseSink 为 null 时（非流式路径）不产生引用帧。
     */
    public AgentLoopOutcome run(List<Map<String, Object>> messages, Authentication auth,
                                Sinks.Many<String> sseSink, AgentLoopOptions options,
                                List<String> imageUrls) {
        AgentLoopOutcome outcome = new AgentLoopOutcome();
        AtomicBoolean firstTokenLogged = new AtomicBoolean();
        long startedAt = AiCallTiming.start();
        // 工具上下文整轮构造一次：认证、SSE 出口、本轮附件都随它下发
        ToolExecutionContext context = new ToolExecutionContext(auth, sseSink,
                imageUrls == null ? List.of() : imageUrls);
        outcome.attachChunks(process(messages, context, options, 0, outcome, firstTokenLogged, startedAt));
        return outcome;
    }

    private Flux<String> process(List<Map<String, Object>> messages, ToolExecutionContext context,
            AgentLoopOptions options, int depth,
            AgentLoopOutcome outcome, AtomicBoolean firstTokenLogged, long startedAt) {

        if (depth > options.maxDepth()) {
            log.warn("Agent Loop 递归深度达到上限 depth={} model={}，终止递归", depth, options.modelLabel());
            return Flux.empty();
        }
        if (depth > 0) {
            log.info("Agent Loop 递归 depth={} model={}，messages 数量={}", depth, options.modelLabel(),
                    messages.size());
        }

        return Flux.defer(() -> {
            List<DeepSeekStreamEvent.ToolCallReady> toolCalls = new ArrayList<>();
            StringBuilder turnReasoning = new StringBuilder();
            StringBuilder turnContent = new StringBuilder();

            return deepSeekClient.stream(messages, toolRegistry.schemas(), options)
                    .doOnNext(event -> {
                        if (event instanceof DeepSeekStreamEvent.ToolCallReady tool) {
                            toolCalls.add(tool);
                        } else if (event instanceof DeepSeekStreamEvent.TextChunk text) {
                            String chunk = text.text();
                            if (chunk.startsWith(AgentLoopOutcome.REASONING_MARKER)) {
                                turnReasoning.append(chunk.substring(AgentLoopOutcome.REASONING_MARKER.length()));
                            } else {
                                turnContent.append(chunk);
                            }
                        }
                    })
                    .filter(event -> event instanceof DeepSeekStreamEvent.TextChunk)
                    .map(event -> ((DeepSeekStreamEvent.TextChunk) event).text())
                    // 累加在过滤之前：即使不向客户端暴露思考过程，reasoning() 仍然完整
                    .doOnNext(outcome::appendText)
                    .doOnNext(chunk -> {
                        if (firstTokenLogged.compareAndSet(false, true)) {
                            log.info("AI首字节到达 type={} model={} elapsedMs={}", options.logType(),
                                    options.modelLabel(), AiCallTiming.elapsedMs(startedAt));
                        }
                    })
                    .filter(chunk -> options.exposeReasoning()
                            || !chunk.startsWith(AgentLoopOutcome.REASONING_MARKER))
                    .concatWith(Flux.defer(() -> {
                        if (toolCalls.isEmpty()) {
                            return Flux.<String>empty();
                        }
                        appendToolCallMessages(messages, toolCalls, turnContent, turnReasoning, context, options, outcome);
                        return process(messages, context, options, depth + 1, outcome, firstTokenLogged, startedAt);
                    }));
        });
    }

    private void appendToolCallMessages(List<Map<String, Object>> messages,
            List<DeepSeekStreamEvent.ToolCallReady> toolCalls,
            StringBuilder turnContent, StringBuilder turnReasoning,
            ToolExecutionContext context, AgentLoopOptions options,
            AgentLoopOutcome outcome) {

        List<Map<String, Object>> toolCallsArray = new ArrayList<>();
        for (DeepSeekStreamEvent.ToolCallReady tool : toolCalls) {
            toolCallsArray.add(Map.of(
                    "id", tool.toolCallId(),
                    "type", "function",
                    "function", Map.of(
                            "name", tool.functionName(),
                            "arguments", tool.argumentsJson())));
        }

        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", turnContent.toString());
        assistantMsg.put("reasoning_content", turnReasoning.toString());
        assistantMsg.put("tool_calls", toolCallsArray);
        messages.add(assistantMsg);

        for (DeepSeekStreamEvent.ToolCallReady tool : toolCalls) {
            log.info("Agent Loop 执行工具调用: {} id={} argsLen={} model={}", tool.functionName(),
                    tool.toolCallId(), tool.argumentsJson().length(), options.modelLabel());

            Map<String, Object> toolMsg = new LinkedHashMap<>();
            toolMsg.put("role", "tool");
            toolMsg.put("tool_call_id", tool.toolCallId());

            try {
                Object result = toolRegistry.execute(tool.functionName(), tool.argumentsJson(), context);
                toolMsg.put("content", objectMapper.writeValueAsString(result));
                outcome.recordToolCalls(1, toolRegistry.emit(tool.functionName(), result, context.sseSink()));
            } catch (Exception e) {
                // DeepSeek 期望每个 tool_call_id 都有对应的 tool 消息；缺失会导致模型卡住或重复调用。
                log.error("工具调用执行失败: {} model={}", e.getMessage(), options.modelLabel());
                toolMsg.put("content", errorPayload(e));
            }
            messages.add(toolMsg);
        }
    }

    /** 只回传已脱敏的短消息，不带堆栈或 SQL。 */
    private String errorPayload(Exception e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        message = message.replaceAll("\\s+", " ").trim();
        if (message.length() > MAX_ERROR_MESSAGE_CHARS) {
            message = message.substring(0, MAX_ERROR_MESSAGE_CHARS);
        }
        try {
            return objectMapper.writeValueAsString(Map.of("error", message));
        } catch (Exception serializationFailure) {
            return "{\"error\":\"tool execution failed\"}";
        }
    }
}
