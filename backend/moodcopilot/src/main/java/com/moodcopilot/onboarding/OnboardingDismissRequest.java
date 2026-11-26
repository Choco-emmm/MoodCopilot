package com.moodcopilot.onboarding;

public record OnboardingDismissRequest(
        String target,
        String key,
        String scope,
        Integer guideVersion
) {
}
