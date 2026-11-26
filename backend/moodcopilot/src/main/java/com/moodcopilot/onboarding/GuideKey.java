package com.moodcopilot.onboarding;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum GuideKey {
    WELCOME_V1(Category.WELCOME, GuideScope.GLOBAL, false),
    STARTER_CHECKLIST_V1(Category.CHECKLIST, GuideScope.GLOBAL, false),

    FIRST_DIARY(Category.TASK, GuideScope.GLOBAL, true),
    FIRST_ANALYSIS_VIEWED(Category.TASK, GuideScope.GLOBAL, true),
    FIRST_CHAT_REPLY(Category.TASK, GuideScope.GLOBAL, true),
    MEMORY_CENTER_VISITED(Category.TASK, GuideScope.GLOBAL, true),
    THEME_EXPLORED(Category.TASK, GuideScope.GLOBAL, false),

    ANALYSIS_READY(Category.HINT, GuideScope.GLOBAL, false),
    CHAT_CITATION(Category.HINT, GuideScope.GLOBAL, false),
    MEMORY_CANDIDATE(Category.HINT, GuideScope.GLOBAL, false),
    RELATION_GRAPH(Category.HINT, GuideScope.GLOBAL, false),
    REPORT_AVAILABLE(Category.HINT, GuideScope.GLOBAL, false),
    LIFE_EVENT(Category.HINT, GuideScope.GLOBAL, false),
    LIFE_CHAPTER(Category.HINT, GuideScope.GLOBAL, false),
    MODEL_PRO(Category.HINT, GuideScope.GLOBAL, false),
    PUBLIC_DIARY_WEB(Category.HINT, GuideScope.WEB, false),
    TOOL_APPROVAL_WEB(Category.HINT, GuideScope.WEB, false);

    public enum Category {
        WELCOME,
        CHECKLIST,
        TASK,
        HINT
    }

    public static final List<GuideKey> ALL_TASKS = List.of(
            FIRST_DIARY,
            FIRST_ANALYSIS_VIEWED,
            FIRST_CHAT_REPLY,
            MEMORY_CENTER_VISITED,
            THEME_EXPLORED
    );

    public static final List<GuideKey> CORE_TASKS = List.of(
            FIRST_DIARY,
            FIRST_ANALYSIS_VIEWED,
            FIRST_CHAT_REPLY,
            MEMORY_CENTER_VISITED
    );

    public static final List<GuideKey> ALL_HINTS = Arrays.stream(values())
            .filter(key -> key.category == Category.HINT)
            .toList();

    private final Category category;
    private final GuideScope scope;
    private final boolean coreTask;

    GuideKey(Category category, GuideScope scope, boolean coreTask) {
        this.category = category;
        this.scope = scope;
        this.coreTask = coreTask;
    }

    public Category category() {
        return category;
    }

    public GuideScope scope() {
        return scope;
    }

    public boolean isCoreTask() {
        return coreTask;
    }

    public static GuideKey parseOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return GuideKey.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
