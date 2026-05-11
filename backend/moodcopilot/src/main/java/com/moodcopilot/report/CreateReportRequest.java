package com.moodcopilot.report;

public record CreateReportRequest(
        String targetType,
        Long targetId,
        String reason
) {
}
