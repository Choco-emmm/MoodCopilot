package com.moodcopilot.common;

public class TextSnippetUtil {

    public static String generateSnippet(String content, int maxLen) {
        if (content == null || content.isBlank()) {
            return "";
        }

        // 1. Remove markdown images and links
        String clean = content.replaceAll("!\\[.*?\\]\\(.*?\\)", "[图片]")
                .replaceAll("\\[.*?\\]\\(.*?\\)", "[链接]")
                .replaceAll("[#*_~`>]", "");

        // 2. Normalize whitespace and newlines
        clean = clean.replaceAll("\\s+", " ").trim();

        int codePointCount = clean.codePointCount(0, clean.length());
        if (codePointCount <= maxLen) {
            return clean;
        }

        // 3. Robust truncation avoiding surrogate pair splitting
        int endIndex = clean.offsetByCodePoints(0, maxLen);
        return clean.substring(0, endIndex) + "...";
    }
}
