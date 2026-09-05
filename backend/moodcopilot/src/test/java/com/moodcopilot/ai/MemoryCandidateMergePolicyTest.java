package com.moodcopilot.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryCandidateMergePolicyTest {
    @Test
    void mergesOnlyTheSameNormalizedValue() {
        assertTrue(MemoryCandidateMergePolicy.compatible("喜欢咖啡", "喜欢咖啡"));
        assertTrue(MemoryCandidateMergePolicy.compatible("喜欢  咖啡", "喜欢咖啡"));
    }

    @Test
    void doesNotMergeSemanticallySimilarButDifferentValues() {
        assertTrue(MemoryCandidateMergePolicy.compatible(
                "对QG工作室表现出强烈向往，希望加入其中",
                "对qg工作室表现出强烈向往，希望加入其中"));
        assertFalse(MemoryCandidateMergePolicy.compatible("喜欢咖啡", "喜欢喝咖啡"));
        assertFalse(MemoryCandidateMergePolicy.compatible(
                "决定坚持进入QG工作室", "过去向往QG但现在因竞争激烈而动摇"));
    }

    @Test
    void keepsDifferentOrOppositeValuesSeparate() {
        assertFalse(MemoryCandidateMergePolicy.compatible("喜欢猫", "喜欢狗"));
        assertFalse(MemoryCandidateMergePolicy.compatible("喜欢咖啡", "不喜欢咖啡"));
    }
}
