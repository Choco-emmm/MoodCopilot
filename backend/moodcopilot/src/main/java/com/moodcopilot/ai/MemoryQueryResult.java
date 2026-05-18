package com.moodcopilot.ai;

import java.util.List;

public record MemoryQueryResult(
        int count,
        List<MemoryItem> items,
        String note) {

    public record MemoryItem(
            String attributeKey,
            String attributeValue,
            String updateTime) {
    }
}
