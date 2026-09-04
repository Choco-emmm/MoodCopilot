package com.moodcopilot.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryCandidateMergePolicyTest {
    @Test
    void mergesSupportiveSameKeyExpressions() {
        assertTrue(MemoryCandidateMergePolicy.compatible(
                "对QG工作室有强烈向往，多次表达希望加入，并以此鞭策自己努力",
                "对QG工作室表现出强烈向往，希望加入其中"));
        assertTrue(MemoryCandidateMergePolicy.compatible("喜欢咖啡", "喜欢喝咖啡"));
    }

    @Test
    void keepsDifferentOrOppositeValuesSeparate() {
        assertFalse(MemoryCandidateMergePolicy.compatible("喜欢猫", "喜欢狗"));
        assertFalse(MemoryCandidateMergePolicy.compatible("喜欢咖啡", "不喜欢咖啡"));
    }
}
