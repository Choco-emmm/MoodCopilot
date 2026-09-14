package com.moodcopilot.ai.tool;

/** 引用帧摘要的长度约束：只影响前端面板，不影响完整工具结果返回给模型。 */
public final class ToolSnippets {

    private static final int MAX_CHARS = 160;

    private ToolSnippets() {
    }

    public static String compact(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > MAX_CHARS ? normalized.substring(0, MAX_CHARS) + "…" : normalized;
    }
}
