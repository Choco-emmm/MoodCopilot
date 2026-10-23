package com.moodcopilot.ai;

/** Reads current and legacy profile source IDs without dropping their user boundary. */
public final class MemorySourceId {
    private MemorySourceId() {}
    public static Long parse(String sourceId, long userId) {
        if (sourceId == null) return null;
        String[] parts = sourceId.split(":");
        try {
            if (parts.length == 1) return Long.valueOf(parts[0]);
            if (!"profile".equals(parts[0])) return null;
            if (parts.length == 2) return Long.valueOf(parts[1]);
            if (parts.length == 3 && Long.parseLong(parts[1]) == userId) return Long.valueOf(parts[2]);
        } catch (NumberFormatException ignored) { }
        return null;
    }
}
