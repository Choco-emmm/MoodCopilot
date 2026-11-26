package com.moodcopilot.onboarding;

import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

public enum OnboardingDismissTarget {
    WELCOME,
    CHECKLIST,
    TASK,
    HINT;

    public static OnboardingDismissTarget parseRequired(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "关闭目标不能为空");
        }
        try {
            return OnboardingDismissTarget.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(BAD_REQUEST, "不支持的关闭目标");
        }
    }
}
