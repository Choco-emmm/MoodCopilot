package com.moodcopilot.ai.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.ai.MemoryExtractionService;
import com.moodcopilot.ai.MemoryOrchestrator;
import com.moodcopilot.ai.tool.ChatTool;
import com.moodcopilot.ai.tool.ToolExecutionContext;
import com.moodcopilot.entity.UserProfileMemoryEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把用户指定的几条记忆合并成一条。
 * <p>
 * 场景是「用户指定要合并哪些 → 模型生成合并后的键和值 → 调用这个工具」。**一次调用合并一组**：
 * 要合并好几组就调好几次，每组各自一页审批。
 * <p>
 * 顺序上有一处必须小心：**先写目标、再清源**。反过来做的话，如果 targetKey 恰好也在 sourceKeys 里，
 * 就会把刚写进去的那条又删掉；而写在前也保证了源清完之后目标已经存在，中间不会出现「两头都空」的窗口。
 * 代码里干脆把 targetKey 从源里剔掉，从源头杜绝这个陷阱。
 * <p>
 * 目标的旧值走正常的 supersede（出现在「历史版本」里）—— 目标是被**编辑**不是被删，
 * 符合「编辑留历史、删除不留痕」。源才是被删的，所以它们跟 {@link DeleteMemoryTool} 一样彻底清除 + 封印。
 */
public class MergeMemoryTool extends ChatTool<MergeMemoryTool.MergeMemoryRequest> {

    public static final String NAME = "mergeMemoryFunction";

    private final MemoryOrchestrator orchestrator;
    private final MemoryExtractionService memoryExtractionService;

    public MergeMemoryTool(MemoryOrchestrator orchestrator, MemoryExtractionService memoryExtractionService) {
        this.orchestrator = orchestrator;
        this.memoryExtractionService = memoryExtractionService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "把用户指定的几条长期记忆合并成一条：写入合并后的 targetKey / targetValue，并彻底删除 sourceIds 里的源记忆。"
                + "只在用户明确要求合并、去重或整理某几条记忆时调用（例如「把这两条合并一下」「这俩重复了」）。"
                + "sourceIds 必须是 memoryQueryFunction 返回过的记忆 ID (id 字段)，不要自己捏造；"
                + "调用前先弄清楚用户要合并的是哪几条，指代不清就先问。"
                + "一次调用只合并一组，要合并多组就多次调用，用户会逐条确认。"
                + "注意：你只需直接调用此工具，系统会在工具执行期间自动让用户确认。若工具返回成功，说明用户已确认且记忆已被合并并落入长期记忆，你无需再告诉用户“去待确认列表点击确认”。";
    }

    @Override
    public Class<MergeMemoryRequest> requestType() {
        return MergeMemoryRequest.class;
    }

    @Override
    public boolean requiresApproval() {
        return true;
    }

    @Override
    public LinkedHashMap<String, Object> properties() {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put("sourceIds", Map.of("type", "array", "items", Map.of("type", "integer"),
                "description", "要被合并掉的记忆 ID 列表，必须是 memoryQueryFunction 返回过的 id"));
        props.put("targetKey", Map.of("type", "string",
                "description", "合并后保留的记忆键；可以与某个源键相同，也可以是新键"));
        props.put("targetValue", Map.of("type", "string",
                "description", "合并后的记忆值，应当涵盖所有源记忆里仍然成立的内容"));
        props.put("memoryType", Map.of("type", "string",
                "enum", List.of("preference", "habit", "goal", "trait", "relation", "short_term_state", "other"),
                "description", "合并后这条记忆的类别"));
        props.put("evidence", Map.of("type", "string",
                "description", "用户本轮原话中支持这次合并的逐字片段"));
        return props;
    }

    @Override
    public List<String> required() {
        return List.of("sourceIds", "targetKey", "targetValue", "memoryType", "evidence");
    }

    @Override
    public Map<String, Object> approvalPreview(ObjectMapper mapper, String argumentsJson,
            ToolExecutionContext context) throws Exception {
        MergeMemoryRequest request = mapper.readValue(
                argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson, requestType());
        Long userId = context.userId();

        List<Map<String, Object>> sources = new ArrayList<>();
        List<Long> sIds = request.sourceIds() == null ? List.of() : request.sourceIds();
        for (Long id : sIds) {
            Map<String, Object> source = new LinkedHashMap<>();
            UserProfileMemoryEntity memory = orchestrator.current(userId).stream()
                    .filter(m -> id.equals(m.getId())).findFirst().orElse(null);
            if (memory != null) {
                source.put("attributeKey", memory.getAttributeKey());
                source.put("value", memory.getAttributeValue());
                sources.add(source);
            }
        }

        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("attributeKey", request.targetKey());
        preview.put("oldValue", sources.isEmpty() ? "" : joinSources(sources));
        preview.put("newValue", request.targetValue());
        preview.put("kind", "merged");
        preview.put("sources", sources);
        return preview;
    }

    @Override
    public Object execute(MergeMemoryRequest request, ToolExecutionContext context) {
        Long userId = context.userId();
        if (userId == null) {
            return new MergeMemoryResult(false, request.targetKey(), List.of(), "没有登录状态，无法合并记忆");
        }
        if (isBlank(request.targetKey()) || isBlank(request.targetValue())) {
            return new MergeMemoryResult(false, request.targetKey(), List.of(), "合并后的键和值都不能为空");
        }

        String targetKey = request.targetKey().trim();
        List<Long> sourceIds = request.sourceIds() == null ? List.of() : request.sourceIds();

        orchestrator.mergeMemoriesByIds(userId, sourceIds, targetKey, request.targetValue(), request.memoryType(),
                false, request.evidence(), context.conversationId());

        String note = sourceIds.isEmpty()
                ? "已直接写入记忆（未指定要合并掉的源记忆 ID）"
                : null;
        return new MergeMemoryResult(true, targetKey, sourceIds, note);
    }

    private static String joinSources(List<Map<String, Object>> sources) {
        return sources.stream()
                .map(source -> source.get("attributeKey") + "：" + source.get("value"))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record MergeMemoryRequest(List<Long> sourceIds, String targetKey, String targetValue,
            String memoryType, String evidence) {
    }

    public record MergeMemoryResult(boolean success, String targetKey, List<Long> purgedIds, String note) {
    }
}
