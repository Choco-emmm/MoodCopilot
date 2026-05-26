package com.moodcopilot.ai;

public record MemoryQueryRequest(
        String keyword,
        Integer limit) {
}
