package com.moodcopilot.onboarding;

import java.util.Map;

public record AdminOnboardingMetricsView(
        int guideVersion,
        long eligibleUserCount,
        long welcomeCompletedCount,
        long welcomeSkippedCount,
        double welcomeSkipRate,
        long firstDiaryCompletedCount,
        double firstDiaryCompletionRate,
        long firstAnalysisViewedCount,
        long firstChatReplyCompletedCount,
        double firstChatSuccessRate,
        long memoryCenterVisitedCount,
        long themeExploredCount,
        long allCoreCompletedCount,
        double coreCompletionRate,
        long crossPlatformHintUserCount,
        Map<String, Long> scopeBreakdown
) {
}
