package com.moodcopilot.ai;

import java.util.List;

public record GraphSearchResult(
        int count,
        List<GraphItem> items,
        String note) {

    public record GraphItem(
            String content,
            String date,
            Long diaryId) {
    }
}
