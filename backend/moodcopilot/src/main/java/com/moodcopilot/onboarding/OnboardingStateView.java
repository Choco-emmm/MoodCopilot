package com.moodcopilot.onboarding;

import java.util.Map;

public record OnboardingStateView(
        int enrolledVersion,
        int guideVersion,
        String welcomeStatus,
        String checklistStatus,
        Map<String, String> tasks,
        Map<String, String> hints
) {
}
