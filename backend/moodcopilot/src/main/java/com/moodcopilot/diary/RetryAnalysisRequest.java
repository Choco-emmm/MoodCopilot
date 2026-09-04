package com.moodcopilot.diary;

public record RetryAnalysisRequest(Boolean useReasoning) {
    public boolean isUseReasoning() {
        return Boolean.TRUE.equals(useReasoning);
    }
}
