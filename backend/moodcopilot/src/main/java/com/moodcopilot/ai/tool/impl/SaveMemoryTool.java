package com.moodcopilot.ai.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.ai.MemoryExtractionService;
import com.moodcopilot.ai.MemoryOrchestrator;
import com.moodcopilot.ai.tool.ChatTool;
import com.moodcopilot.ai.tool.ToolExecutionContext;
import com.moodcopilot.entity.UserProfileMemoryEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把用户明确要求记住的事实写进长期记忆。
 * <p>
 * 这是第一个 {@link #requiresApproval()} 为 true 的工具：模型决定要写，但**落库前必须由用户过目**。
 * 用户在弹框里看到「原值 → 新值」才能判断模型有没有理解错，拒绝时给出的理由会作为工具结果
 * 回给模型，让它换个方向重写或者收手。
 * <p>
 * 批准即写正式记忆（{@code sourceType = "explicit"}）—— 用户的确认动作本身就是明确声明。
 * 敏感数据拦截与类型归一仍由 {@link MemoryOrchestrator#processExtractedMemories} 把关，
 * 本类不重复实现那套策略。
 */
public class SaveMemoryTool extends ChatTool<SaveMemoryTool.SaveMemoryRequest> {

    public static final String NAME = "saveMemoryFunction";

    private final MemoryOrchestrator orchestrator;
    private final MemoryExtractionService memoryExtractionService;

    public SaveMemoryTool(MemoryOrchestrator orchestrator, MemoryExtractionService memoryExtractionService) {
        this.orchestrator = orchestrator;
        this.memoryExtractionService = memoryExtractionService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "把用户明确要求记住的长期事实、偏好、习惯或身份特征写入长期记忆库。"
                + "只在用户主动要求时调用（例如「帮我记一下我不吃香菜」）。"
                + "【绝对禁止】：如果用户要求记录的是带有【具体时间点】、【行程】或【待办】性质的事情（例如“记下我下周去日本”、“明天要开会”），哪怕用户用了“记住”这个词，也【绝对禁止】使用本工具！必须使用 createEventFunction 工具！本工具只能用于不受时间限制的长期属性（如性格、喜好、病史等）。"
                + "注意：系统会自动让用户确认，若返回成功说明已落库，无需再口头告诉用户“去点击确认”。"
                + "evidence 必须逐字摘录用户本轮的原话片段，不要自己改写或总结。";
    }

    @Override
    public Class<SaveMemoryRequest> requestType() {
        return SaveMemoryRequest.class;
    }

    @Override
    public boolean requiresApproval() {
        return true;
    }

    @Override
    public LinkedHashMap<String, Object> properties() {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put("attributeKey", Map.of("type", "string",
                "description", "记忆的键，例如「讨厌的食物」"));
        props.put("attributeValue", Map.of("type", "string",
                "description", "记忆的值，例如「番茄」"));
        props.put("memoryType", Map.of("type", "string",
                "enum", List.of("preference", "habit", "goal", "trait", "relation", "short_term_state", "other"),
                "description", "记忆类别"));
        props.put("evidence", Map.of("type", "string",
                "description", "用户本轮原话中支持这条记忆的逐字片段"));
        return props;
    }

    @Override
    public List<String> required() {
        return List.of("attributeKey", "attributeValue", "memoryType", "evidence");
    }

    /**
     * 弹框内容：这条记忆会覆盖掉什么。
     * <p>
     * 旧值取自当前生效的记忆；取不到就是新增。查询失败不该挡住中断流程 ——
     * 由 {@code ChatToolRegistry.approvalPreview} 兜住异常，退成「只说工具名」。
     */
    @Override
    public Map<String, Object> approvalPreview(ObjectMapper mapper, String argumentsJson,
            ToolExecutionContext context) throws Exception {
        SaveMemoryRequest request = mapper.readValue(
                argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson, requestType());
        String oldValue = currentValue(context.userId(), request.attributeKey());

        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("attributeKey", request.attributeKey());
        preview.put("oldValue", oldValue == null ? "" : oldValue);
        preview.put("newValue", request.attributeValue());
        preview.put("kind", oldValue == null ? "added" : "updated");
        return preview;
    }

    @Override
    public Object execute(SaveMemoryRequest request, ToolExecutionContext context) {
        Long userId = context.userId();
        if (userId == null) {
            return new SaveMemoryResult(false, request.attributeKey(), request.attributeValue(),
                    "没有登录状态，无法写入记忆");
        }
        if (isBlank(request.attributeKey()) || isBlank(request.attributeValue())) {
            return new SaveMemoryResult(false, request.attributeKey(), request.attributeValue(),
                    "记忆的键和值都不能为空");
        }

        orchestrator.processExtractedMemories(userId,
                List.of(new MemoryExtractionService.MemoryAttribute(
                        request.attributeKey(), request.attributeValue(), Boolean.FALSE,
                        request.memoryType(), "explicit", 1.0, request.evidence(), null, null)),
                "explicit", null, context.conversationId(),
                // 证据只认用户本轮的原话与引用；模型给的 evidence 若落空，这里兜底
                memoryExtractionService.buildChatUserEvidence(context.userMessage(), context.userReferences()),
                null);

        return new SaveMemoryResult(true, request.attributeKey(), request.attributeValue(), null);
    }

    private String currentValue(Long userId, String attributeKey) {
        if (userId == null || isBlank(attributeKey)) {
            return null;
        }
        for (UserProfileMemoryEntity memory : orchestrator.current(userId)) {
            if (attributeKey.equals(memory.getAttributeKey())) {
                return memory.getAttributeValue();
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record SaveMemoryRequest(String attributeKey, String attributeValue, String memoryType, String evidence) {
    }

    public record SaveMemoryResult(boolean success, String attributeKey, String attributeValue, String note) {
    }
}
