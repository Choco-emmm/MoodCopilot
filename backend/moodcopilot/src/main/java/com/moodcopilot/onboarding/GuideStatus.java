package com.moodcopilot.onboarding;

public enum GuideStatus {
    NOT_STARTED(0),
    IN_PROGRESS(1),
    SKIPPED(2),
    COMPLETED(3);

    private final int rank;

    GuideStatus(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public static GuideStatus parseOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return GuideStatus.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
