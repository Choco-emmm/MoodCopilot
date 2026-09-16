package com.moodcopilot.ai.tool.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodcopilot.ai.DiaryImageAnalysisFunctionSupport;
import com.moodcopilot.ai.DiaryImageAnalysisFunctionSupport.DiaryImageAnalysisResult;
import com.moodcopilot.ai.DiaryImageAnalysisRequest;
import com.moodcopilot.ai.VisionService;
import com.moodcopilot.ai.tool.ChatTool;
import com.moodcopilot.ai.tool.ToolExecutionContext;
import com.moodcopilot.ai.tool.ToolSnippets;
import com.moodcopilot.common.RateLimitException;
import com.moodcopilot.entity.DiaryEntity;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.security.RateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DiaryImageAnalysisTool extends ChatTool<DiaryImageAnalysisRequest> {

    private static final Logger log = LoggerFactory.getLogger(DiaryImageAnalysisTool.class);

    private static final String DEFAULT_PROMPT = "请详细描述图片中的关键内容、文字、人物、物品、环境和与用户问题相关的细节。";

    private final DiaryMapper diaryMapper;
    private final VisionService visionService;
    private final RateLimitService rateLimitService;

    public DiaryImageAnalysisTool(DiaryMapper diaryMapper, VisionService visionService, RateLimitService rateLimitService) {
        this.diaryMapper = diaryMapper;
        this.visionService = visionService;
        this.rateLimitService = rateLimitService;
    }

    @Override
    public String name() {
        return DiaryImageAnalysisFunctionSupport.NAME;
    }

    @Override
    public String description() {
        return "调用视觉大模型对用户日记中的图片进行深度分析。触发条件：1. 当默认的简短图片描述无法回答提问（如问具体价格、文字细节等）时；"
                + "2. 当用户明确要求'详细看看'、'还有别的吗'、'列出全部'等表明图片内还有未提及的隐藏信息时，必须强制调用此工具。"
                + "注意：此功能随用户等级有每日使用限额。";
    }

    @Override
    public Class<DiaryImageAnalysisRequest> requestType() {
        return DiaryImageAnalysisRequest.class;
    }

    @Override
    public LinkedHashMap<String, Object> properties() {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put("diaryIds", Map.of(
                "type", "array",
                "items", Map.of("type", "integer"),
                "description", "要深度分析图片的日记 ID 列表"));
        props.put("prompt", Map.of("type", "string", "description", "发给视觉模型的提示词。你必须把你已知的上下文（如日记中提到的特定物品名称、用户的具体疑问、需要核实的关键点）整理后写入此提示词中，指导视觉模型带着背景知识去识图，避免它在没有上下文的情况下盲目猜测。"));
        return props;
    }

    @Override
    public List<String> required() {
        return List.of("diaryIds", "prompt");
    }

    @Override
    public Object execute(DiaryImageAnalysisRequest request, ToolExecutionContext context) {
        UserEntity user = context.user();
        long userId = user.getId();
        String prompt = request.prompt() == null || request.prompt().isBlank()
                ? DEFAULT_PROMPT
                : request.prompt().trim();

        log.info("触发图片深度分析(VLM)工具 userId={}, diaryIds={}, promptLength={}", userId, request.diaryIds(), prompt.length());
        try {
            rateLimitService.tryAcquire(user, RateLimitService.AiApiType.IMAGE_ANALYSIS);
        } catch (RateLimitException e) {
            log.warn("图片深度分析(VLM)额度不足，拦截请求 userId={}", userId);
            return new DiaryImageAnalysisResult("由于今日图片深度分析次数已达限额，无法分析图片，请明日再试。");
        }
        if (request.diaryIds() == null || request.diaryIds().isEmpty()) {
            log.info("图片深度分析(VLM)失败：未提供日记ID userId={}", userId);
            return new DiaryImageAnalysisResult("未提供日记ID，无法分析");
        }

        List<DiaryEntity> diaries = diaryMapper.selectList(new LambdaQueryWrapper<DiaryEntity>()
                .in(DiaryEntity::getId, request.diaryIds())
                .eq(DiaryEntity::getAuthorUserId, userId)
                .eq(DiaryEntity::getIsDeleted, false));
        List<String> images = new ArrayList<>();
        StringBuilder diaryContentBuilder = new StringBuilder();
        for (DiaryEntity diary : diaries) {
            if (diary.getImages() != null) {
                images.addAll(diary.getImages());
            }
            if (diary.getContent() != null && !diary.getContent().isBlank()) {
                diaryContentBuilder.append(diary.getContent()).append("\n");
            }
        }
        if (images.isEmpty()) {
            log.info("图片深度分析(VLM)失败：选定的日记中没有图片 userId={}", userId);
            return new DiaryImageAnalysisResult("选定的日记中没有包含任何图片");
        }

        log.info("图片深度分析(VLM)准备请求视觉大模型 userId={}, 图片数量={}", userId, images.size());
        String analysis = visionService.analyzeImageDetails(images, prompt, diaryContentBuilder.toString().trim());
        log.info("图片深度分析(VLM)完成 userId={}", userId);
        return new DiaryImageAnalysisResult(analysis);
    }

    @Override
    public List<Map<String, String>> references(Object result) {
        if (!(result instanceof DiaryImageAnalysisResult analysisResult)) {
            return List.of();
        }
        return List.of(Map.of(
                "type", "image_analysis",
                "snippet", ToolSnippets.compact(analysisResult.analysisResult()),
                "toolName", displayName()));
    }
}
