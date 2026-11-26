package com.moodcopilot.onboarding;

import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

public enum OnboardingEventType {
    WELCOME_STARTED,
    WELCOME_COMPLETED,
    ANALYSIS_VIEWED,
    MEMORY_CENTER_VISITED,
    THEME_EXPLORED,
    HINT_SEEN;

    public static OnboardingEventType parseRequired(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "引导事件类型不能为空");
        }
        try {
            return OnboardingEventType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(BAD_REQUEST, "不支持的引导事件类型");
        }
    }
}
