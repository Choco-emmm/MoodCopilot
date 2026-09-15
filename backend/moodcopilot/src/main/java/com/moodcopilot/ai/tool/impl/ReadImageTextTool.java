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
                + "默认的图片描述只讲画面、不读文字。只在用户明确问图片上的文字时调用（如「图里写了什么」「截图上的数字是多少」）。"
                + "不要为了让答案更完整就自行去读：店名、价格、型号这类信息读不到就直接说不确定。"
                + "这个工具一次要等几十秒，不要主动调用。不需要自己指定图片，工具会用本轮用户附带的所有图片。";
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
        // 「没配视觉服务」是真故障，「图里没文字」是正常结果 —— 混在一句里
        // 只会让模型转述出一句用户看不懂的话。
        // 残余：视觉服务已配置但调用中途失败时，仍会落到「没有文字」这一支。
        if (!visionService.isConfigured()) {
            return new ReadImageTextResult(false, null, "视觉服务暂时不可用，这次没能读图上的文字。");
        }
        String text = visionService.extractText(imageUrls, request.focus());
        if (text == null || text.isBlank()) {
            return new ReadImageTextResult(false, null, "这几张图片里没有可读的文字。");
        }
        return new ReadImageTextResult(true, text, null);
    }

    public record ReadImageTextRequest(String focus) {
    }

    public record ReadImageTextResult(boolean success, String text, String note) {
    }
}
