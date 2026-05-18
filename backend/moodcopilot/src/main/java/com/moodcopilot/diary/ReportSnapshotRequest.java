package com.moodcopilot.diary;

public record ReportSnapshotRequest(
        String period,
        Integer offset) {
}
