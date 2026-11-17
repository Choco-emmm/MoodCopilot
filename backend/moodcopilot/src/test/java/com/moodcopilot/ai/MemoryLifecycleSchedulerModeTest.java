package com.moodcopilot.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 开关的失败安全：无法识别的配置必须退回 {@code off}，而不是默认执行。
 * 这里依赖 {@code off} 分支在访问任何 mapper 之前返回，因此可以传 null 依赖。
 */
class MemoryLifecycleSchedulerModeTest {

    private static MemoryLifecycleScheduler scheduler() {
        return new MemoryLifecycleScheduler(null, null, null, null, null, "Asia/Shanghai");
    }

    @Test
    void unknownModeFallsBackToOffAndDoesNoWork() {
        assertEquals(0, scheduler().runOnce("bogus").written());
        assertEquals(0, scheduler().runOnce(null).written());
        assertEquals(0, scheduler().runOnce("").written());
        assertEquals(0, scheduler().runOnce("OFF").written());
    }

    @Test
    void offModeReportsEmptyResult() {
        MemoryLifecycleScheduler.RunResult result = scheduler().runOnce("off");
        assertEquals(0, result.users());
        assertEquals(0, result.scanned());
        assertEquals(0, result.written());
        assertEquals(0, result.archived());
    }
}
