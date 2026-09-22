package com.moodcopilot.ai.tool.impl;

import com.moodcopilot.ai.tool.ChatTool;
import com.moodcopilot.ai.tool.ToolExecutionContext;
import com.moodcopilot.event.LifeEventService;
import com.moodcopilot.event.LifeEventService.LifeEventUpsertRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CreateEventTool extends ChatTool<LifeEventUpsertRequest> {

    private static final Logger log = LoggerFactory.getLogger(CreateEventTool.class);
    private final LifeEventService lifeEventService;

    public CreateEventTool(LifeEventService lifeEventService) {
        this.lifeEventService = lifeEventService;
    }

    @Override
    public Class<LifeEventUpsertRequest> requestType() {
        return LifeEventUpsertRequest.class;
    }

    @Override
    public String name() {
        return "createEventFunction";
    }

    @Override
    public String description() {
        return "新建或记录用户的重要人生事件（如买房、看病、入职、旅行等）。"
                + "请根据用户提供的信息或日记内容，提取出事件的标题、描述、发生日期等要素。";
    }

    @Override
    public LinkedHashMap<String, Object> properties() {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put("title", Map.of("type", "string", "description", "事件标题，简明扼要概括（如'看牙医'、'提车'、'办理签证'）。最大128字符。"));
        props.put("description", Map.of("type", "string", "description", "事件的详细背景、起因和经过。尽可能保留用户的原话细节和情绪。"));
        props.put("targetDate", Map.of("type", "string", "description", "事件的开始日期，格式 yyyy-MM-dd。"));
        props.put("endDate", Map.of("type", "string", "description", "事件的结束日期（如果跨天），格式 yyyy-MM-dd。若为单日事件可为空。"));
        props.put("startTime", Map.of("type", "string", "description", "事件的开始具体时间，格式 HH:mm:ss，如 09:30:00。可为空。"));
        props.put("endTime", Map.of("type", "string", "description", "事件的结束具体时间，格式 HH:mm:ss。可为空。"));
        props.put("followUpNote", Map.of("type", "string", "description", "对于该事件的后续跟进提示或备忘。可为空。"));
        props.put("diaryIds", Map.of("type", "array", "items", Map.of("type", "integer"), "description", "关联的日记ID列表。如果该事件是从某篇或某几篇具体日记中提取的，必须传入这些日记的ID。可为空。"));
        return props;
    }

    @Override
    public List<String> required() {
        return List.of("title", "targetDate");
    }

    @Override
    public boolean requiresApproval() {
        return true;
    }

    @Override
    public Map<String, Object> approvalPreview(com.fasterxml.jackson.databind.ObjectMapper mapper, String argumentsJson,
            ToolExecutionContext context) throws Exception {
        LifeEventUpsertRequest req = mapper.readValue(argumentsJson, LifeEventUpsertRequest.class);
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("attributeKey", req.title());
        
        StringBuilder sb = new StringBuilder();
        if (req.targetDate() != null) {
            sb.append("日期: ").append(req.targetDate());
            if (req.startTime() != null) sb.append(" ").append(req.startTime());
            if (req.endDate() != null) {
                sb.append(" 至 ").append(req.endDate());
                if (req.endTime() != null) sb.append(" ").append(req.endTime());
            }
            sb.append("\n");
        }
        if (req.description() != null && !req.description().isBlank()) {
            sb.append("描述: ").append(req.description());
        }
        
        preview.put("oldValue", null);
        preview.put("newValue", sb.toString());
        return preview;
    }

    @Override
    public Object execute(LifeEventUpsertRequest request, ToolExecutionContext ctx) {
        if (request.title() == null || request.title().isBlank()) {
            return Map.of("success", false, "message", "事件标题(title)不能为空");
        }
        if (request.targetDate() == null || request.targetDate().isBlank()) {
            return Map.of("success", false, "message", "事件开始日期(targetDate)不能为空");
        }
        try {
            var created = lifeEventService.createEvent(ctx.userId(), request);
            return Map.of(
                    "success", true,
                    "eventId", created.id(),
                    "message", "事件创建成功"
            );
        } catch (IllegalArgumentException e) {
            log.warn("调用事件创建方法参数错误: {}", e.getMessage());
            return Map.of("success", false, "message", "事件参数错误: " + e.getMessage());
        } catch (Exception e) {
            log.error("调用事件创建方法失败: {}", e.getMessage());
            return Map.of("success", false, "message", "事件创建失败: " + e.getMessage());
        }
    }
}
