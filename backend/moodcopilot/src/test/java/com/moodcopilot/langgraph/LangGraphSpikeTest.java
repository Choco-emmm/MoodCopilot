package com.moodcopilot.langgraph;

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
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.state.StateSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeActionWithConfig.node_async;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 迁 loop 之前，先用一张玩具图把三件「必须为真」的事钉死。
 * <p>
 * 全部读源码得出的结论都不算数 —— 尤其是第 3 条：参考项目恢复时传的是 {@code null}，
 * 而我们要传「用户批准/拒绝」这个决策，传非空 Map 到底是从断点续跑还是从头重跑，
 * 只有跑一次才知道。
 */
class LangGraphSpikeTest {

    private static final String AGENT = "agentNode";
    private static final String TOOLS = "toolsNode";
    private static final String APPROVAL = "approval";
    private static final String THREAD = "spike-thread";

    /** 跑过的节点次数：用来区分「真的从断点续跑」和「从头重跑」。 */
    private final AtomicInteger agentRuns = new AtomicInteger();
    private final AtomicInteger toolRuns = new AtomicInteger();

    private InMemoryCheckpointSaver saver;
    private CompiledGraph<SpikeState> graph;

    @BeforeEach
    void setUp() throws Exception {
        agentRuns.set(0);
        toolRuns.set(0);
        saver = new InMemoryCheckpointSaver();
        graph = buildGraph();
    }

    // ---------- 假设 1：InterruptableAction 真的能按次中断 ----------

    @Test
    void anInterruptableActionStopsTheGraphBeforeTheNodeBodyRuns() {
        runToPause();

        assertEquals(1, agentRuns.get(), "只该跑完 agentNode 就停");
        assertEquals(0, toolRuns.get(), "被拦下的节点体不该执行");
    }

    // ---------- 假设 2：中断后能看出停在哪个节点、metadata 读得出来 ----------

    @Test
    void thePauseIsReadableAsAPendingNodePlusAttachedMetadata() throws Exception {
        AsyncGenerator<NodeOutput<SpikeState>> generator = runToPause();

        StateSnapshot<SpikeState> snapshot = graph.getState(config());
        assertEquals(TOOLS, snapshot.next(), "getState().next() 该指向待执行的节点");
        assertEquals(AGENT, snapshot.node(), "快照的 node() 是最后跑完的那个节点");

        Object result = AsyncGenerator.resultValue(generator).orElseThrow();
        assertInstanceOf(InterruptionMetadata.class, result);

        @SuppressWarnings("unchecked")
        InterruptionMetadata<SpikeState> metadata = (InterruptionMetadata<SpikeState>) result;
        assertEquals(TOOLS, metadata.nodeId());
        assertTrue(metadata.metadata(APPROVAL).isPresent(), "预览数据要通过 metadata 带出来");
    }

    // ---------- 假设 3：决策 Map 真的能并进 state（核心） ----------

    @Test
    void resumingWithGraphInputResumeCarriesTheDecisionIntoState() throws Exception {
        runToPause();

        graph.stream(GraphInput.resume(Map.of("approved", true)), config()).stream().toList();

        assertEquals(2, toolRuns.get(), "恢复后该轮到 toolsNode 执行");
        assertEquals(List.of("agent", "tool", "agent", "tool", "agent"), trace(),
                "应当从 toolsNode 续跑：重跑 agentNode 会在开头多出一个 agent");
        assertEquals(Boolean.TRUE, saver.get(config()).orElseThrow().getState().get("approved"),
                "决策 Map 要并进 state，toolsNode 才看得到用户批没批");
    }

    /**
     * 这是全案最大的坑，写成测试防止以后踩回去：
     * {@code stream(非空Map, cfg)} 并不会「从断点续跑 + 带上决策」，它会走 GraphArgs 分支，
     * 从 START 重新入图（state 取自检查点但执行从头开始）。
     * 对我们的 loop 来说这意味着重新调一次模型 —— 而那时 assistant 的 tool_calls 还没有
     * 配对的 tool 消息，模型会直接报错。
     */
    @Test
    void passingTheDecisionAsPlainInputRestartsFromStartInsteadOfResuming() throws Exception {
        runToPause();

        graph.stream(Map.of("approved", true), config()).stream().toList();

        assertEquals(List.of("agent", "agent", "tool", "agent"), trace(),
                "传普通 Map 会从 START 重跑 agentNode，而不是从 toolsNode 续跑");
    }

    // ---------- 辅助 ----------

    private RunnableConfig config() {
        return RunnableConfig.builder().threadId(THREAD).build();
    }

    /** 生成器是惰性的：不消费它，图一步都不会跑。 */
    private AsyncGenerator<NodeOutput<SpikeState>> runToPause() {
        AsyncGenerator<NodeOutput<SpikeState>> generator = graph.stream(initialData(), config());
        generator.stream().toList();
        return generator;
    }

    private List<String> trace() {
        Object trace = saver.get(config()).orElseThrow().getState().get("trace");
        return trace instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    private static Map<String, Object> initialData() {
        Map<String, Object> data = new HashMap<>();
        data.put("loopCount", 0);
        data.put("trace", new ArrayList<String>());
        data.put("pendingApproval", true);
        return data;
    }

    private CompiledGraph<SpikeState> buildGraph() throws Exception {
        // 默认的 appender 会去重，而这里要看得就是「第几次」进的节点，所以允许重复
        Map<String, Channel<?>> channels = Map.of("trace", Channels.<String>appenderWithDuplicate(ArrayList::new));
        StateGraph<SpikeState> graph = new StateGraph<>(channels, SpikeState::new);

        graph.addNode(AGENT, node_async(agentAction()));
        graph.addNode(TOOLS, node_async(new ToolsAction()));

        graph.addEdge(START, AGENT);
        graph.addConditionalEdges(AGENT, edge_async(state ->
                        state.loopCount() >= 3 ? "end" : "use_tool"),
                Map.of("use_tool", TOOLS, "end", END));
        graph.addEdge(TOOLS, AGENT);

        return graph.compile(CompileConfig.builder().checkpointSaver(saver).build());
    }

    private NodeActionWithConfig<SpikeState> agentAction() {
        return (state, config) -> {
            agentRuns.incrementAndGet();
            Map<String, Object> update = new HashMap<>();
            update.put("loopCount", state.loopCount() + 1);
            update.put("trace", List.of("agent"));
            update.put("needTool", true);
            return update;
        };
    }

    /** 只在这一个节点上做「按次中断」—— 与我们要落在 toolsNode 上的东西同构。 */
    private class ToolsAction implements NodeActionWithConfig<SpikeState>, InterruptableAction<SpikeState> {

        @Override
        public Optional<InterruptionMetadata<SpikeState>> interrupt(String nodeId, SpikeState state,
                                                                    RunnableConfig config) {
            boolean awaitingDecision = state.pendingApproval() && !state.approved();
            return awaitingDecision
                    ? Optional.of(InterruptionMetadata.<SpikeState>builder(nodeId, state)
                            .putMetadata(APPROVAL, Map.of(
                                    "attributeKey", "讨厌的食物",
                                    "oldValue", "",
                                    "newValue", "番茄"))
                            .build())
                    : Optional.empty();
        }

        @Override
        public Map<String, Object> apply(SpikeState state, RunnableConfig config) {
            toolRuns.incrementAndGet();
            return Map.of("trace", List.of("tool"), "pendingApproval", false);
        }
    }

    public static class SpikeState extends AgentState {

        SpikeState(Map<String, Object> initData) {
            super(initData);
        }

        int loopCount() {
            Object value = data().get("loopCount");
            return value instanceof Number number ? number.intValue() : 0;
        }

        boolean pendingApproval() {
            return Boolean.TRUE.equals(data().get("pendingApproval"));
        }

        boolean approved() {
            return Boolean.TRUE.equals(data().get("approved"));
        }
    }

}
