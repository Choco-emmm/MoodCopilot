package com.moodcopilot.ai;

import com.moodcopilot.entity.UserProfileMemoryEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryLifecyclePolicyTest {

    private static UserProfileMemoryEntity memory(String type, String source, boolean core, Double confidence) {
        UserProfileMemoryEntity memory = new UserProfileMemoryEntity();
        memory.setMemoryType(type);
        memory.setSourceType(source);
        memory.setIsCore(core);
        memory.setConfidence(confidence);
        memory.setStatus("active");
        memory.setLifecycleState(MemoryLifecyclePolicy.STATE_ACTIVE);
        return memory;
    }

    private static UserProfileMemoryEntity decayable() {
        return memory("preference", "diary_inferred", false, 0.5);
    }

    @Test
    void archivesOnlyWhenAllConditionsHold() {
        assertEquals(MemoryLifecyclePolicy.STATE_ARCHIVED,
                MemoryLifecyclePolicy.evaluate(decayable(), 120, 200));
    }

    @Test
    void calendarGuardBlocksArchiveButStillDegradesToDecaying() {
        // 曝光可以因批量补录一天暴涨，但日历护栏必须先走满 90 天。
        assertEquals(MemoryLifecyclePolicy.STATE_DECAYING,
                MemoryLifecyclePolicy.evaluate(decayable(), 120, 30));
    }

    @Test
    void highConfidenceMemoryNeverArchives() {
        UserProfileMemoryEntity memory = memory("preference", "diary_inferred", false, 0.9);
        assertEquals(MemoryLifecyclePolicy.STATE_DECAYING, MemoryLifecyclePolicy.evaluate(memory, 120, 200));
    }

    @Test
    void userConfirmedSourceIsExempt() {
        assertNull(MemoryLifecyclePolicy.evaluate(memory("preference", "explicit", false, 0.3), 500, 900));
    }

    @Test
    void coreMemoryIsExempt() {
        assertNull(MemoryLifecyclePolicy.evaluate(memory("preference", "diary_inferred", true, 0.3), 500, 900));
    }

    @Test
    void nonDecayingTypesAreExempt() {
        for (String type : new String[] { "relationship", "event", "short_term_state" }) {
            assertNull(MemoryLifecyclePolicy.evaluate(memory(type, "diary_inferred", false, 0.3), 500, 900),
                    type + " 不应参与衰减");
        }
    }

    @Test
    void explicitValidUntilIsExempt() {
        UserProfileMemoryEntity memory = decayable();
        memory.setValidUntil(LocalDate.of(2030, 1, 1));
        assertNull(MemoryLifecyclePolicy.evaluate(memory, 500, 900));
    }

    @Test
    void nonActiveStatusIsExempt() {
        UserProfileMemoryEntity memory = decayable();
        memory.setStatus("superseded");
        assertNull(MemoryLifecyclePolicy.evaluate(memory, 500, 900));
    }

    @Test
    void alreadyArchivedIsNoOp() {
        UserProfileMemoryEntity memory = decayable();
        memory.setLifecycleState(MemoryLifecyclePolicy.STATE_ARCHIVED);
        assertNull(MemoryLifecyclePolicy.evaluate(memory, 500, 900));
    }

    @Test
    void unchangedStateReturnsNullSoNoUpdateIsIssued() {
        assertNull(MemoryLifecyclePolicy.evaluate(decayable(), 0, 0));
        UserProfileMemoryEntity decaying = decayable();
        decaying.setLifecycleState(MemoryLifecyclePolicy.STATE_DECAYING);
        // 10 周 → freshness 约 0.35，落在 decaying 区间，状态不变。
        assertNull(MemoryLifecyclePolicy.evaluate(decaying, 10, 70));
    }

    @Test
    void freshnessDecaysAndClampsAtFloor() {
        assertEquals(1.0, MemoryLifecyclePolicy.freshness(0), 1e-9);
        assertEquals(0.9, MemoryLifecyclePolicy.freshness(1), 1e-9);
        assertEquals(0.20, MemoryLifecyclePolicy.freshness(107.0 / MemoryLifecyclePolicy.STEPS_PER_WEEK), 0.005);
        assertEquals(0.05, MemoryLifecyclePolicy.freshness(500), 1e-9);
        assertEquals(1.0, MemoryLifecyclePolicy.freshness(-1), 1e-9);
    }

    @Test
    void passLifecycleOnlyRejectsArchived() {
        assertFalse(MemoryLifecyclePolicy.passLifecycle(null));
        assertTrue(MemoryLifecyclePolicy.passLifecycle(decayable()));
        assertTrue(MemoryLifecyclePolicy.passLifecycle(memoryWithState(null)));
        assertTrue(MemoryLifecyclePolicy.passLifecycle(memoryWithState("decaying")));
        assertFalse(MemoryLifecyclePolicy.passLifecycle(memoryWithState(" ARCHIVED ")));
    }

    private static UserProfileMemoryEntity memoryWithState(String state) {
        UserProfileMemoryEntity memory = decayable();
        memory.setLifecycleState(state);
        return memory;
    }
}
