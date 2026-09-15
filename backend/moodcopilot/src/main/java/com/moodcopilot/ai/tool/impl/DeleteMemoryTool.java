package com.moodcopilot.ai.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.ai.MemoryOrchestrator;
import com.moodcopilot.ai.tool.ChatTool;
import com.moodcopilot.ai.tool.ToolExecutionContext;
import com.moodcopilot.entity.UserProfileMemoryEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按「键」彻底删除一条长期记忆。
 * <p>
 * 与 {@link SaveMemoryTool} 相对：那个是「笔」，这个是「橡皮」。用户在对话里说「删掉我的心理状态」
 * 时，模型以前只能回答「我没这个功能」，把人推去记忆中心 —— 现在可以直接做，但要先过审批。
 * <p>
 * **删的是整个键，不是当前值**：该键下所有版本（含 superseded / rejected / expired）一起物理清除，
 * 界面上和库里都不剩。想要保留历史应该走编辑而不是删除。删完由
 * {@link MemoryOrchestrator#purgeByKey} 补一条按键封印，防止抽取器明天又从日记里把它推导回来。
 * <p>
 * 一次只删一个键：删多条就多次调用。这不是限制，而是审批弹框按「一次调用 = 一页」分页的前提 ——
 * 一次调用里塞多个键的话，用户就没法逐条接受/拒绝了。
 */
public class DeleteMemoryTool extends ChatTool<DeleteMemoryTool.DeleteMemoryRequest> {

    public static final String NAME = "deleteMemoryFunction";

    private final MemoryOrchestrator orchestrator;

    public DeleteMemoryTool(MemoryOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "彻底删除用户长期记忆里的某一个键，连同该键的所有历史版本一起清除，不可恢复。"
                + "只在用户明确要求删除或忘记某条记忆时调用（例如「删掉我的心理状态」「别再记着这件事了」）。"
                + "attributeKey 必须是 memoryQueryFunction 返回过的键名，照抄，不要自己改写或翻译；"
                + "如果找不到对应的键或用户指代不清，先问清楚再调用。"
                + "删除后 180 天内系统不会再自动推导出这个键，但用户之后明确说要记仍然可以重新建立。"
                + "执行前会请用户确认，用户可以选择拒绝并说明理由。";
    }

    @Override
    public Class<DeleteMemoryRequest> requestType() {
        return DeleteMemoryRequest.class;
    }

    @Override
    public boolean requiresApproval() {
        return true;
    }

    @Override
    public LinkedHashMap<String, Object> properties() {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put("attributeKey", Map.of("type", "string",
                "description", "要删除的记忆的键，必须是 memoryQueryFunction 返回过的原样键名"));
        return props;
    }

    @Override
    public List<String> required() {
        return List.of("attributeKey");
    }

    /**
     * 弹框内容：即将被删掉的那条记忆。
     * <p>
     * 放进 {@code oldValue} 而不是新造一个字段，是为了直接复用弹框里的红色删除线渲染
     * （{@code − 值}）。{@code newValue} 留空，前端据此不渲染 {@code +} 那一行。
     * <p>
     * 键不存在时 oldValue 为空 —— 不做特殊处理，让 {@link #execute} 去回执「没找到」，
     * 免得预览这里和真正执行时的判断分叉成两套。
     */
    @Override
    public Map<String, Object> approvalPreview(ObjectMapper mapper, String argumentsJson,
            ToolExecutionContext context) throws Exception {
        DeleteMemoryRequest request = mapper.readValue(
                argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson, requestType());
        String oldValue = currentValue(context.userId(), request.attributeKey());

        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("attributeKey", request.attributeKey());
        preview.put("oldValue", oldValue == null ? "" : oldValue);
        preview.put("newValue", "");
        preview.put("kind", "deleted");
        return preview;
    }

    @Override
    public Object execute(DeleteMemoryRequest request, ToolExecutionContext context) {
        Long userId = context.userId();
        if (userId == null) {
            return new DeleteMemoryResult(false, request.attributeKey(), 0, "没有登录状态，无法删除记忆");
        }
        if (isBlank(request.attributeKey())) {
            return new DeleteMemoryResult(false, request.attributeKey(), 0, "要删除的记忆键不能为空");
        }

        int removed = orchestrator.purgeByKey(userId, request.attributeKey());
        if (removed == 0) {
            return new DeleteMemoryResult(false, request.attributeKey(), 0,
                    "没有找到这个键的记忆，可能已经删过了");
        }
        return new DeleteMemoryResult(true, request.attributeKey(), removed,
                "已彻底删除，含 " + removed + " 个历史版本");
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

    public record DeleteMemoryRequest(String attributeKey) {
    }

    public record DeleteMemoryResult(boolean success, String attributeKey, int removedVersions, String note) {
    }
}
