package com.moodcopilot.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemorySafetyPolicyTest {
    @Test
    void safetySignalsAreShortTermAndNeverCore() {
        assertTrue(MemorySafetyPolicy.isSafetyState("心理状态", "有自杀意念，需要关注"));
        assertEquals("short_term_state",
                MemorySafetyPolicy.normalizeType("pattern", "心理状态", "有自杀意念，需要关注"));
        assertFalse(MemorySafetyPolicy.allowCore("pattern", "心理状态", "有自杀意念，需要关注"));
    }

    @Test
    void ordinaryProfileFactsKeepTheirRequestedType() {
        assertFalse(MemorySafetyPolicy.isSafetyState("社交偏好", "喜欢和熟悉的人单独相处"));
        assertEquals("preference",
                MemorySafetyPolicy.normalizeType("preference", "社交偏好", "喜欢和熟悉的人单独相处"));
        assertTrue(MemorySafetyPolicy.allowCore("preference", "社交偏好", "喜欢和熟悉的人单独相处"));
    }
}
