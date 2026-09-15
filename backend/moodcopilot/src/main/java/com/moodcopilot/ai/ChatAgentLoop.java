package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.ai.tool.ChatToolRegistry;
import com.moodcopilot.ai.tool.ToolExecutionContext;
import com.moodcopilot.langgraph.RedisCheckpointSaver;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.InterruptableAction;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.action.NodeActionWithConfig;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
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
import java.util.Optional;
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
 * 图挂了 {@link RedisCheckpointSaver}（threadId = runId），并把累加结果一并存进 state，
 * 因此「停在工具执行前等用户批准」是可恢复的：用户可能几分钟后才点，期间还可能刷新页面
 * 甚至换一次后端进程。恢复走 {@link #resume}，用 {@code GraphInput.resume(决定)} 回到断点。
 */
@Component
public class ChatAgentLoop {

    private static final Logger log = LoggerFactory.getLogger(ChatAgentLoop.class);

    private static final int MAX_ERROR_MESSAGE_CHARS = 200;

    private static final String AGENT_NODE = "agentNode";
    private static final String TOOLS_NODE = "toolsNode";
    private static final String ROUTE_TOOLS = "tools";
    private static final String ROUTE_END = "end";

    /** InterruptionMetadata 里放审批预览的键；恢复时也用它取回用户决定。 */
    static final String APPROVAL_KEY = "approval";

    private final DeepSeekClient deepSeekClient;
    private final ChatToolRegistry toolRegistry;
    /** 面向接口：生产用 {@link RedisCheckpointSaver}，测试塞内存实现即可，不必伺候 Redis。 */
    private final BaseCheckpointSaver checkpointSaver;
    private final ObjectMapper objectMapper;

    public ChatAgentLoop(DeepSeekClient deepSeekClient, ChatToolRegistry toolRegistry,
            BaseCheckpointSaver checkpointSaver, ObjectMapper objectMapper) {
        this.deepSeekClient = deepSeekClient;
        this.toolRegistry = toolRegistry;
        this.checkpointSaver = checkpointSaver;
        this.objectMapper = objectMapper;
    }

    /**
     * 一次 run 的线程标识。检查点按它落 Redis，恢复必须传同一个值 —— 故用 runId。
     */
    private static RunnableConfig config(String threadId) {
        return RunnableConfig.builder().threadId(threadId).build();
    }

    /**
     * 构造 {@link #resume} 要回填的决定。键名是图状态的一部分，调用方不该自己拼字符串。
     * <p>
     * 这条是不带 {@code toolCallId} 的通配决定：整批共用。只有一条待批准时用它就够了。
     */
    public static Map<String, Object> approvalDecision(boolean approved, String reason) {
        return approvalDecision(List.of(ApprovalChoice.forAll(approved, reason)));
    }

    /**
     * 逐条表态。多组工具调用（例如一次合并好几组）时，用户可以只批其中几组。
     * <p>
     * 没被点名的调用会落到通配决定上；连通配都没有的按**拒绝**处理 —— 没点头的事不该做。
     */
    public static Map<String, Object> approvalDecision(List<ApprovalChoice> choices) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (ApprovalChoice choice : choices) {
            Map<String, Object> entry = new LinkedHashMap<>();
            // 不带 toolCallId 的那条就是通配，别补 null 值把它变成逐条
            if (choice.toolCallId() != null) {
                entry.put("toolCallId", choice.toolCallId());
            }
            entry.put("approved", choice.approved());
            entry.put("reason", choice.reason() == null ? "" : choice.reason());
            payload.add(entry);
        }
        return Map.of(ChatLoopState.APPROVAL_DECISION, Map.of("decisions", payload));
    }

    /**
     * 驱动一次完整的对话（含工具调用往返）。
     * <p>
     * 返回的 outcome 其 {@code reply()} / {@code reasoning()} 要在订阅并消费完 {@code chunks()} 之后才可读。
     * sseSink 为 null 时（非流式路径）不产生引用帧。
     */
    public AgentLoopOutcome run(List<Map<String, Object>> messages, Authentication auth,
                                Sinks.Many<String> sseSink, AgentLoopOptions options, ChatTurn turn,
                                String threadId) {
        return run(messages, auth, sseSink, options, turn, threadId, true);
    }

    /**
     * @param approvalsInteractive 这条通道能否让用户当场确认。非流式（公网/移动端的 /reply）
     *                             不能 —— 那种情况下中断会变成「没人来点」，工具永远悬着。
     *                             所以预先写入一条带理由的拒绝，让模型直接向用户解释、而不是干等。
     */
    public AgentLoopOutcome run(List<Map<String, Object>> messages, Authentication auth,
                                Sinks.Many<String> sseSink, AgentLoopOptions options, ChatTurn turn,
                                String threadId, boolean approvalsInteractive) {
        AgentLoopOutcome outcome = new AgentLoopOutcome();
        return drive(messages, outcome, auth, sseSink, options, turn, threadId, null, approvalsInteractive);
    }

    /**
     * 从检查点续跑：用户批准/拒绝之后接着走。
     * <p>
     * 累加器要从检查点里重建 —— 暂停发生在 agentNode 跑完之后，那时正文已经累加进上一轮的
     * outcome 并推给了客户端；新建一个空 outcome 会让持久化下来的助手回复丢掉前半截。
     *
     * @param decision 回填进 state 的决定，形如 {@code {"approval": {"approved": true, "reason": ""}}}
     */
    public AgentLoopOutcome resume(Authentication auth, Sinks.Many<String> sseSink, AgentLoopOptions options,
                                   ChatTurn turn, String threadId, Map<String, Object> decision) {
        AgentLoopOutcome outcome = AgentLoopOutcome.restore(checkpointSaver.get(config(threadId))
                .map(Checkpoint::getState)
                .orElse(null));
        return drive(null, outcome, auth, sseSink, options, turn, threadId, decision, true);
    }

    /** 一次 run 或 resume 的公共骨架：建图、跑图、收尾同步。 */
    private AgentLoopOutcome drive(List<Map<String, Object>> messages, AgentLoopOutcome outcome,
                                   Authentication auth, Sinks.Many<String> sseSink, AgentLoopOptions options,
                                   ChatTurn turn, String threadId, Map<String, Object> resumeDecision,
                                   boolean approvalsInteractive) {

        ToolExecutionContext context = new ToolExecutionContext(auth, sseSink, turn.imageUrls(),
                turn.userMessage(), turn.conversationId(), turn.userReferences());

        // 图是同步迭代的，分片靠这个 sink 推给调用方 —— chunks() 保持惰性：
        // 不订阅就不跑图，订阅了才一边跑一边推。
        outcome.attachChunks(Flux.create(sink -> {
            try {
                List<Map<String, Object>> latest = iterateGraph(messages, context, options, outcome, sink,
                        threadId, resumeDecision, approvalsInteractive);
                // 节点拿到的是 state 的深拷贝（图在每个节点前 cloneState），所以回灌的 assistant / tool
                // 消息只活在图里。调用方持有的那一份要同步过来 —— 这是「消息回灌」这个对外行为的落点。
                if (messages != null && latest != null && latest != messages) {
                    messages.clear();
                    messages.addAll(latest);
                }
                sink.complete();
            } catch (Throwable failure) {
                log.warn("Agent Loop 执行失败 model={} reason={}", options.modelLabel(), failure.getMessage());
                sink.error(failure);
            }
        }));
        return outcome;
    }

    /** @return 图跑完后的 messages，供调用方同步回自己那份列表 */
    private List<Map<String, Object>> iterateGraph(List<Map<String, Object>> messages, ToolExecutionContext context,
            AgentLoopOptions options, AgentLoopOutcome outcome, FluxSink<String> sink, String threadId,
            Map<String, Object> resumeDecision, boolean approvalsInteractive) throws Exception {

        AtomicBoolean firstTokenLogged = new AtomicBoolean();
        long startedAt = AiCallTiming.start();
        RunnableConfig config = config(threadId);
        CompiledGraph<ChatLoopState> graph = compileGraph(context, options, outcome, sink,
                firstTokenLogged, startedAt);

        AsyncGenerator<NodeOutput<ChatLoopState>> generator = (resumeDecision == null)
                ? graph.stream(initialData(messages, approvalsInteractive), config)
                : graph.stream(GraphInput.resume(resumeDecision), config);

        List<Map<String, Object>> latest = messages;
        for (NodeOutput<ChatLoopState> output : generator) {
            latest = output.state().messages();
        }

        // 中断不是异常：图正常停了，停在待执行的节点前。中断数据只能从生成器的结果值取，
        // NodeOutput 上没有 —— 见 LangGraphSpikeTest 的结论。
        Object result = AsyncGenerator.resultValue(generator).orElse(null);
        if (result instanceof InterruptionMetadata<?> interruption) {
            outcome.pause(approvalMetadata(interruption));
        }
        return latest;
    }

    private static Map<String, Object> approvalMetadata(InterruptionMetadata<?> interruption) {
        Map<String, Object> approval = new LinkedHashMap<>();
        approval.put("nodeId", interruption.nodeId());
        interruption.metadata(APPROVAL_KEY).ifPresent(value -> approval.put(APPROVAL_KEY, value));
        return approval;
    }

    private CompiledGraph<ChatLoopState> compileGraph(ToolExecutionContext context, AgentLoopOptions options,
            AgentLoopOutcome outcome, FluxSink<String> sink, AtomicBoolean firstTokenLogged, long startedAt)
            throws Exception {

        StateGraph<ChatLoopState> graph = new StateGraph<>(ChatLoopState::new);
        graph.addNode(AGENT_NODE, node_async(agentAction(context, options, outcome, sink,
                firstTokenLogged, startedAt)));
        // toolsNode 是 InterruptableAction：只有它自己能按「这次要跑哪些工具」决定停不停，
        // 而不是 CompileConfig.interruptBefore 那种「进这个节点就一律停」的节点级拦截。
        graph.addNode(TOOLS_NODE, node_async(new ToolsAction(context, options, outcome)));

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

        // 挂上检查点：中断时 state（消息、待执行的工具、累加好的正文）必须落下来，
        // 用户批准可能发生在几分钟后，甚至跨一次重启。
        return graph.compile(CompileConfig.builder().checkpointSaver(checkpointSaver).build());
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
            // 累加结果也要落进 state：下一站若是 toolsNode，可能的暂停会把整份 state 存成检查点，
            // 恢复时正文得从这里拼回来
            update.putAll(outcome.snapshot());
            return update;
        };
    }

    /**
     * 执行上一轮留下的工具调用，每个 tool_call_id 都必须有回音。
     * <p>
     * 它同时是 {@link InterruptableAction}：这批调用里只要有工具声明了
     * {@link com.moodcopilot.ai.tool.ChatTool#requiresApproval()}，就在执行**之前**停下来等用户点头。
     * 用按次中断而不是 {@code CompileConfig.interruptBefore(toolsNode)} —— 后者是节点级的，
     * 会让每个工具都停，而只有记忆这类「会改用户东西」的工具才需要人过目。
     */
    private final class ToolsAction implements NodeActionWithConfig<ChatLoopState>, InterruptableAction<ChatLoopState> {

        private final ToolExecutionContext context;
        private final AgentLoopOptions options;
        private final AgentLoopOutcome outcome;

        ToolsAction(ToolExecutionContext context, AgentLoopOptions options, AgentLoopOutcome outcome) {
            this.context = context;
            this.options = options;
            this.outcome = outcome;
        }

        @Override
        public Optional<InterruptionMetadata<ChatLoopState>> interrupt(String nodeId, ChatLoopState state,
                RunnableConfig config) {
            // 没人能来点确认的通道永远不中断，直接走 apply 里的拒绝分支
            if (!state.approvalsInteractive()) {
                return Optional.empty();
            }
            // 用户已经表过态就放行：这次恢复的目的就是执行/否掉它
            if (state.hasApprovalDecision()) {
                return Optional.empty();
            }
            List<Map<String, Object>> items = approvalItems(state);
            if (items.isEmpty()) {
                return Optional.empty();
            }
            log.info("工具执行前等待用户批准，items={} model={}", items.size(), options.modelLabel());
            return Optional.of(InterruptionMetadata.<ChatLoopState>builder(nodeId, state)
                    .putMetadata(APPROVAL_KEY, Map.of("items", items))
                    .build());
        }

        @Override
        public Map<String, Object> apply(ChatLoopState state, RunnableConfig config) {
            List<Map<String, Object>> messages = new ArrayList<>(state.messages());

            for (Map<String, Object> call : state.pendingToolCalls()) {
                String name = (String) call.get("name");
                String toolCallId = (String) call.get("id");
                String arguments = (String) call.get("arguments");

                Map<String, Object> toolMsg = new LinkedHashMap<>();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", toolCallId);

                // 逐条取决定：用户可能只批了这批里的某几条，没被批到的必须逐个拒绝而不是整批处理。
                ApprovalChoice decision = state.decisionFor(toolCallId).orElse(null);
                boolean approved = decision != null && decision.approved();

                if (toolRegistry.requiresApproval(name) && !approved) {
                    // 拒绝不是错误：把理由作为工具结果回给模型，让它换个方向重写或者收手。
                    String reason = state.approvalsInteractive()
                            ? orDefaultReason(decision == null ? "" : decision.reason())
                            : NON_INTERACTIVE_REASON;
                    log.info("拒绝工具调用 {} id={} interactive={} reason={}", name, toolCallId,
                            state.approvalsInteractive(), reason);
                    toolMsg.put("content", rejectionPayload(reason));
                    messages.add(toolMsg);
                    continue;
                }

                log.info("Agent Loop 执行工具调用: {} id={} argsLen={} model={}", name, toolCallId,
                        arguments == null ? 0 : arguments.length(), options.modelLabel());
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

            outcome.clearPause();
            Map<String, Object> update = new LinkedHashMap<>();
            update.put(ChatLoopState.MESSAGES, messages);
            update.put(ChatLoopState.PENDING_TOOL_CALLS, List.of());
            // 置 null 会把键从 state 里摘掉（图自己的空值约定）：决定只用一次，
            // 不然下一轮再遇到要批准的工具时会被当成「已经批过了」
            update.put(ChatLoopState.APPROVAL_DECISION, null);
            update.putAll(outcome.snapshot());
            return update;
        }

        /** 只把需要批准的那几个挑出来给前端；不需要批准的工具照常静默执行。 */
        private List<Map<String, Object>> approvalItems(ChatLoopState state) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (Map<String, Object> call : state.pendingToolCalls()) {
                String name = (String) call.get("name");
                if (!toolRegistry.requiresApproval(name)) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("toolCallId", call.get("id"));
                item.put("toolName", toolRegistry.displayName(name));
                item.putAll(toolRegistry.approvalPreview(name, (String) call.get("arguments"), context));
                items.add(item);
            }
            return items;
        }

        private String rejectionPayload(String reason) {
            try {
                return objectMapper.writeValueAsString(Map.of("rejected", true, "reason", reason));
            } catch (Exception e) {
                return "{\"rejected\":true}";
            }
        }
    }

    /** 不能当场确认的通道给模型的可行动理由 —— 比让它对着一个没人点的弹框干等强。 */
    private static final String NON_INTERACTIVE_REASON =
            "当前通道无法请你确认这条改动。请直接告诉用户你打算记住什么，并请他在 App 里确认。";

    /**
     * 用户点了拒绝但没写理由。留空的话模型只看到一个空字符串，很容易原样再调一次同样的工具，
     * 于是又弹一次框 —— 那句话得说清楚「别再试了」。
     */
    private static final String UNSPECIFIED_REJECTION_REASON =
            "用户拒绝了这次改动，没有说明原因。不要再尝试写入这条，可以问问他为什么、或者换个方向继续。";

    private static String orDefaultReason(String reason) {
        return reason == null || reason.isBlank() ? UNSPECIFIED_REJECTION_REASON : reason;
    }

    private static Map<String, Object> initialData(List<Map<String, Object>> messages, boolean approvalsInteractive) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(ChatLoopState.MESSAGES, messages);
        data.put(ChatLoopState.PENDING_TOOL_CALLS, List.of());
        data.put(ChatLoopState.LOOP_COUNT, 0);
        // 记在 state 上而不是预置一条「已拒绝」：决定用一次就摘掉了，
        // 模型下一轮再调要批准的工具时会重新悬住，而标记不会。
        data.put(ChatLoopState.APPROVALS_INTERACTIVE, approvalsInteractive);
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
