package com.moodcopilot.ai;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ChatIntentRouter {

    public boolean shouldUseReasoning(String message, List<String> refs, String memoryBackground) {
        // 这是一个轻量路由器，不追求语义完美，只负责把“明显复杂”的问题分流到思考模型。
        if (message == null) {
            return false;
        }

        String normalized = message.trim();
        if (normalized.isEmpty()) {
            return false;
        }

        int score = 0;

        if (normalized.length() >= 80) {
            score++;
        }
        if (normalized.length() >= 160) {
            score++;
        }

        String lower = normalized.toLowerCase();
        // 这些关键词通常意味着用户在要分析、总结、对比或推理。
        if (containsAny(lower, List.of("为什么", "怎么", "如何", "原因", "分析", "总结", "梳理", "对比", "区别", "推演", "复盘", "深入", "详细",
                "规划", "建议", "判断"))) {
            score += 2;
        }

        // 这类表达通常是在明确要求“帮我想一想”，比普通闲聊更适合走 reasoning。
        if (containsAny(lower, List.of("帮我想", "帮我分析", "帮我总结", "给我建议", "帮我梳理", "帮我看看", "帮我判断"))) {
            score += 2;
        }

        long questionMarks = normalized.chars().filter(ch -> ch == '？' || ch == '?').count();
        if (questionMarks >= 2) {
            score++;
        }

        int sentenceCount = countSentences(normalized);
        if (sentenceCount >= 3) {
            score++;
        }

        if (refs != null && !refs.isEmpty() && normalized.length() >= 60) {
            score++;
        }

        // 用户给了较长的长期画像背景时，往往说明这次对话需要更多上下文推理。
        if (memoryBackground != null && !memoryBackground.isBlank() && normalized.length() >= 100) {
            score++;
        }

        // 分值达到阈值就认为是复杂问题，交给思考模型处理。
        return score >= 2;
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private int countSentences(String text) {
        int count = 0;
        for (char ch : text.toCharArray()) {
            if (ch == '。' || ch == '！' || ch == '？' || ch == '.' || ch == '!' || ch == '?') {
                count++;
            }
        }
        return count;
    }
}