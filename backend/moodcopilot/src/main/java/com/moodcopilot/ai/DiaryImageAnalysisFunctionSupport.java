package com.moodcopilot.ai;

import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class DiaryImageAnalysisFunctionSupport implements Function<DiaryImageAnalysisRequest, DiaryImageAnalysisFunctionSupport.DiaryImageAnalysisResult> {

    public static final String NAME = "diaryImageAnalysisFunction";

    // 这个 Function 本身是个占位符，实际的逻辑在 ChatService 中被接管执行
    @Override
    public DiaryImageAnalysisResult apply(DiaryImageAnalysisRequest request) {
        return new DiaryImageAnalysisResult("工具执行被代理");
    }

    public record DiaryImageAnalysisResult(String analysisResult) {}
}
