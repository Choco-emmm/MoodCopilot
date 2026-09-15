package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.ai.tool.ChatToolRegistry;
import com.moodcopilot.ai.tool.ToolExecutionContext;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.NodeActionWithConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeActionWithConfig.node_async;

/**
 * 唯一的 Agent Loop：flash 与 pro 都走这里，模型差异由 {@link AgentLoopOptions} 参数化。
 * <p>
 * 之所以自研而不复用 Spring AI 的 ChatClient，是为了直接拿到 reasoning_content
 * （见 {@link DeepSeekClient} 的说明）。工具 schema 与执行都来自 {@link ChatToolRegistry}。
 * <p>
 * 循环本身是一张显式的状态图 —— 此前它是「递归 Flux + 深度守卫 + 条件续跑」，
 * 隐式地长成同一种形状。画出来是为了让「跑到一半要等人」这类状态有个正式的落脚点：
 *
 * <pre>
 * START → agentNode ──有 tool_calls──→ toolsNode ──还有预算──→ agentNode
 *              └──无 tool_calls──→ END          └──超预算──→ END
 * </pre>
 *
 * 推理分片的剥离在 {@link AgentLoopOutcome} 内部完成，本类只负责驱动与转发。
 * <p>
 * 本批次**不挂检查点、不中断**：先把隐式图换成显式图，行为逐项对齐。
 */
@Component
public class ChatAgentLoop {

    private static final Logger log = LoggerFactory.getLogger(ChatAgentLoop.class);

    private static final int MAX_ERROR_MESSAGE_CHARS = 200;

    private static final String AGENT_NODE = "agentNode";
    private static final String TOOLS_NODE = "toolsNode";
    private static final String ROUTE_TOOLS = "tools";
    private static final String ROUTE_END = "end";

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
        // 工具上下文整轮构造一次：认证、SSE 出口、本轮附件都随它下发
        ToolExecutionContext context = new ToolExecutionContext(auth, sseSink,
                imageUrls == null ? List.of() : imageUrls);

        // 图是同步迭代的，分片靠这个 sink 推给调用方 —— chunks() 保持惰性：
        // 不订阅就不跑图，订阅了才一边跑一边推。
        outcome.attachChunks(Flux.create(sink -> {
            try {
                drive(messages, context, options, outcome, sink);
                sink.complete();
            } catch (Throwable failure) {
                log.warn("Agent Loop 执行失败 model={} reason={}", options.modelLabel(), failure.getMessage());
                sink.error(failure);
            }
        }));
        return outcome;
    }

    private void drive(List<Map<String, Object>> messages, ToolExecutionContext context,
                       AgentLoopOptions options, AgentLoopOutcome outcome, FluxSink<String> sink)
            throws Exception {

        AtomicBoolean firstTokenLogged = new AtomicBoolean();
        long startedAt = AiCallTiming.start();

        CompiledGraph<ChatLoopState> graph = compileGraph(context, options, outcome, sink,
                firstTokenLogged, startedAt);

        List<Map<String, Object>> latest = messages;
        for (NodeOutput<ChatLoopState> output : graph.stream(initialData(messages), RunnableConfig.builder().build())) {
            latest = output.state().messages();
        }

        // 节点拿到的是 state 的深拷贝（图在每个节点前 cloneState），所以回灌的 assistant / tool
        // 消息只活在图里。调用方持有的那一份要同步过来 —— 这是「消息回灌」这个对外行为的落点。
        if (latest != messages && latest != null) {
            messages.clear();
            messages.addAll(latest);
        }
    }

    private CompiledGraph<ChatLoopState> compileGraph(ToolExecutionContext context, AgentLoopOptions options,
            AgentLoopOutcome outcome, FluxSink<String> sink, AtomicBoolean firstTokenLogged, long startedAt)
            throws Exception {

        StateGraph<ChatLoopState> graph = new StateGraph<>(ChatLoopState::new);
        graph.addNode(AGENT_NODE, node_async(agentAction(context, options, outcome, sink,
                firstTokenLogged, startedAt)));
        graph.addNode(TOOLS_NODE, node_async(toolsAction(context, options, outcome)));

        graph.addEdge(START, AGENT_NODE);
        graph.addConditionalEdges(AGENT_NODE,
                edge_async(state -> state.pendingToolCalls().isEmpty() ? ROUTE_END : ROUTE_TOOLS),
                Map.of(ROUTE_TOOLS, TOOLS_NODE, ROUTE_END, END));
        // 深度守卫：loopCount 是已调用模型的次数，与原先「depth > maxDepth 就停」等价 ——
        // 工具该跑还得跑完，只是不再拿它的结果去问模型。
        graph.addConditionalEdges(TOOLS_NODE,
                edge_async(state -> {
                    if (state.loopCount() > options.maxDepth()) {
                        log.warn("Agent Loop 递归深度达到上限 depth={} model={}，终止递归",
                                state.loopCount(), options.modelLabel());
                        return ROUTE_END;
                    }
                    return AGENT_NODE;
                }),
                Map.of(AGENT_NODE, AGENT_NODE, ROUTE_END, END));

        return graph.compile();
    }

    /** 调一次模型，把分片推给客户端，并留下「这轮要不要用工具」。 */
    private NodeActionWithConfig<ChatLoopState> agentAction(ToolExecutionContext context,
            AgentLoopOptions options, AgentLoopOutcome outcome, FluxSink<String> sink,
            AtomicBoolean firstTokenLogged, long startedAt) {

        return (state, config) -> {
            int round = state.loopCount();
            if (round > 0) {
                log.info("Agent Loop 递归 depth={} model={}，messages 数量={}", round, options.modelLabel(),
                        state.messages().size());
            }

            List<Map<String, Object>> messages = new ArrayList<>(state.messages());
            List<DeepSeekStreamEvent.ToolCallReady> toolCalls = new ArrayList<>();
            StringBuilder turnReasoning = new StringBuilder();
            StringBuilder turnContent = new StringBuilder();

            for (DeepSeekStreamEvent event : deepSeekClient.stream(messages, toolRegistry.schemas(), options)
                    .toIterable()) {
                if (event instanceof DeepSeekStreamEvent.ToolCallReady tool) {
                    toolCalls.add(tool);
                    continue;
                }
                if (!(event instanceof DeepSeekStreamEvent.TextChunk text)) {
                    continue;
                }
                String chunk = text.text();
                if (chunk.startsWith(AgentLoopOutcome.REASONING_MARKER)) {
                    turnReasoning.append(chunk.substring(AgentLoopOutcome.REASONING_MARKER.length()));
                } else {
                    turnContent.append(chunk);
                }

                // 累加在过滤之前：即使不向客户端暴露思考过程，reasoning() 仍然完整
                outcome.appendText(chunk);
                if (firstTokenLogged.compareAndSet(false, true)) {
                    log.info("AI首字节到达 type={} model={} elapsedMs={}", options.logType(),
                            options.modelLabel(), AiCallTiming.elapsedMs(startedAt));
                }
                if (options.exposeReasoning() || !chunk.startsWith(AgentLoopOutcome.REASONING_MARKER)) {
                    sink.next(chunk);
                }
            }

            Map<String, Object> update = new LinkedHashMap<>();
            update.put(ChatLoopState.LOOP_COUNT, round + 1);
            if (!toolCalls.isEmpty()) {
                messages.add(assistantToolCallMessage(toolCalls, turnContent, turnReasoning));
                update.put(ChatLoopState.PENDING_TOOL_CALLS, pendingToolCalls(toolCalls));
            }
            update.put(ChatLoopState.MESSAGES, messages);
            return update;
        };
    }

    /** 执行上一轮留下的工具调用，每个 tool_call_id 都必须有回音。 */
    private NodeActionWithConfig<ChatLoopState> toolsAction(ToolExecutionContext context,
            AgentLoopOptions options, AgentLoopOutcome outcome) {

        return (state, config) -> {
            List<Map<String, Object>> messages = new ArrayList<>(state.messages());

            for (Map<String, Object> call : state.pendingToolCalls()) {
                String name = (String) call.get("name");
                String toolCallId = (String) call.get("id");
                String arguments = (String) call.get("arguments");
                log.info("Agent Loop 执行工具调用: {} id={} argsLen={} model={}", name, toolCallId,
                        arguments == null ? 0 : arguments.length(), options.modelLabel());

                Map<String, Object> toolMsg = new LinkedHashMap<>();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", toolCallId);
                try {
                    Object result = toolRegistry.execute(name, arguments, context);
                    toolMsg.put("content", objectMapper.writeValueAsString(result));
                    outcome.recordToolCalls(1, toolRegistry.emit(name, result, context.sseSink()));
                } catch (Exception e) {
                    // DeepSeek 期望每个 tool_call_id 都有对应的 tool 消息；缺失会导致模型卡住或重复调用。
                    log.error("工具调用执行失败: {} model={}", e.getMessage(), options.modelLabel());
                    toolMsg.put("content", errorPayload(e));
                }
                messages.add(toolMsg);
            }

            Map<String, Object> update = new LinkedHashMap<>();
            update.put(ChatLoopState.MESSAGES, messages);
            update.put(ChatLoopState.PENDING_TOOL_CALLS, List.of());
            return update;
        };
    }

    private static Map<String, Object> initialData(List<Map<String, Object>> messages) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(ChatLoopState.MESSAGES, messages);
        data.put(ChatLoopState.PENDING_TOOL_CALLS, List.of());
        data.put(ChatLoopState.LOOP_COUNT, 0);
        return data;
    }

    private static Map<String, Object> assistantToolCallMessage(
            List<DeepSeekStreamEvent.ToolCallReady> toolCalls,
            StringBuilder turnContent, StringBuilder turnReasoning) {

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
        return assistantMsg;
    }

    private static List<Map<String, Object>> pendingToolCalls(List<DeepSeekStreamEvent.ToolCallReady> toolCalls) {
        List<Map<String, Object>> pending = new ArrayList<>(toolCalls.size());
        for (DeepSeekStreamEvent.ToolCallReady tool : toolCalls) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", tool.toolCallId());
            entry.put("name", tool.functionName());
            entry.put("arguments", tool.argumentsJson());
            pending.add(entry);
        }
        return pending;
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
