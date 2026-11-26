package com.moodcopilot.onboarding;

public record OnboardingEventRequest(
        String eventType,
        Long diaryId,
        String hintKey,
        String scope,
        Integer guideVersion
) {
}
