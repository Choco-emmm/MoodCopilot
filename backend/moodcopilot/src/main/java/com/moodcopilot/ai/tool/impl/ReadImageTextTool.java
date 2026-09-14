package com.moodcopilot.ai.tool.impl;

import com.moodcopilot.ai.VisionService;
import com.moodcopilot.ai.tool.ChatTool;
import com.moodcopilot.ai.tool.ToolExecutionContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 读取本轮用户附带图片里的文字（OCR）。
 * <p>
 * 默认的图片描述只走视觉模型（快），刻意不读文字。需要原文时 —— 截图里的数字、
 * 表单、聊天记录、海报标题 —— 由模型自己调用这个工具触发 OCR，因为 OCR 一张
 * 文字密集的图可能要几十秒，不该让每张图都付这个代价。
 * <p>
 * 图片 URL 来自 {@link ToolExecutionContext}，模型不接触 URL。
 */
public class ReadImageTextTool extends ChatTool<ReadImageTextTool.ReadImageTextRequest> {

    public static final String NAME = "readImageTextFunction";

    private final VisionService visionService;

    public ReadImageTextTool(VisionService visionService) {
        this.visionService = visionService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "读取用户本轮发送的图片中的文字（OCR 逐字提取）。"
                + "默认图片描述只讲画面、不读文字；当用户问图片里写了什么、或者需要截图/表单/聊天记录里的具体文字时，必须调用本工具。"
                + "不需要自己指定图片，工具会用本轮用户附带的所有图片。";
    }

    @Override
    public Class<ReadImageTextRequest> requestType() {
        return ReadImageTextRequest.class;
    }

    @Override
    public LinkedHashMap<String, Object> properties() {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put("focus", Map.of("type", "string",
                "description", "想重点确保被提取的内容；不需要特别指定就传空字符串"));
        return props;
    }

    @Override
    public List<String> required() {
        return List.of("focus");
    }

    @Override
    public Object execute(ReadImageTextRequest request, ToolExecutionContext context) {
        List<String> imageUrls = context.imageUrls();
        if (imageUrls == null || imageUrls.isEmpty()) {
            return new ReadImageTextResult(false, null,
                    "本轮对话没有附带图片，无法进行文字提取。");
        }
        String text = visionService.extractText(imageUrls, request.focus());
        if (text == null || text.isBlank()) {
            return new ReadImageTextResult(false, null,
                    "这些图片里没有识别到文字，或者视觉服务暂时不可用。");
        }
        return new ReadImageTextResult(true, text, null);
    }

    public record ReadImageTextRequest(String focus) {
    }

    public record ReadImageTextResult(boolean success, String text, String note) {
    }
}
