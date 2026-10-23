package com.moodcopilot.ai;

/** Safe, content-free write receipt. status is authoritative; success is for older clients. */
public record MemoryWriteResult(String status, String reasonCode, Long memoryId, boolean executed, boolean success) {
    public static MemoryWriteResult applied(Long id) { return new MemoryWriteResult("APPLIED", null, id, true, true); }
    public static MemoryWriteResult unchanged(Long id) { return new MemoryWriteResult("UNCHANGED", null, id, false, true); }
    public static MemoryWriteResult rejected(String reason) { return new MemoryWriteResult("REJECTED", reason, null, false, false); }
    public static MemoryWriteResult stale() { return new MemoryWriteResult("STALE", "TARGET_CHANGED", null, false, false); }
}
