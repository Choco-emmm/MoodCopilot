package com.moodcopilot.diary;

public record ReportSnapshotResult(
        String period,
        int offset,
        String label,
        int diaryCount,
        String dominantQuadrant,
        Integer positiveRatioPercent,
        Integer highEnergyRatioPercent,
        String generatedAt,
        String note) {
}
