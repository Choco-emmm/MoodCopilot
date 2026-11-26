package com.moodcopilot.onboarding;

import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

public enum GuideScope {
    GLOBAL,
    WEB,
    MINI_PROGRAM;

    public static GuideScope parseOptional(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return GuideScope.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(BAD_REQUEST, "无效的引导作用域");
        }
    }

    public static GuideScope parseOptionalPlatform(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        GuideScope scope = parseOptional(raw);
        if (scope != WEB && scope != MINI_PROGRAM) {
            throw new ResponseStatusException(BAD_REQUEST, "查询作用域仅支持 WEB 或 MINI_PROGRAM");
        }
        return scope;
    }
}
