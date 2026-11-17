package com.moodcopilot.ai;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 曝光索引的核心断言：量纲对齐（三类用户同量级）、相对上限（单来源最多主导 1.5 倍）、
 * 低频来源不被放大、真实跨度做分母。
 *
 * <p>所有用例把日记与会话对齐到同一起点 {@link #BASE}，否则合并索引的
 * {@code contentStart} 会前移、两个来源的速率基准不再一致，1.5 倍上限的对照就失去意义。
 */
class MemoryExposureIndexTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 1, 0, 0);
    private static final LocalDateTime BASE = NOW.minusDays(240);
    private static final LocalDateTime REF = NOW.minusDays(60);

    /** 240 天连续日记，速率约 7 篇/周。 */
    private static List<LocalDateTime> dailyDiaries() {
        List<LocalDateTime> list = new ArrayList<>();
        for (int i = 0; i < 240; i++) {
            list.add(BASE.plusDays(i));
        }
        return list;
    }

    /** 240 天 480 个会话（每 12 小时一次），速率约 14 个/周。 */
    private static List<LocalDateTime> frequentConversations() {
        List<LocalDateTime> list = new ArrayList<>();
        for (int i = 0; i < 480; i++) {
            list.add(BASE.plusHours(12L * i));
        }
        return list;
    }

    /** 聊天型用户的稀疏日记：历史两篇在参照点之前，沉默期只有一篇。 */
    private static List<LocalDateTime> chatHeavyDiaries() {
        return List.of(BASE.plusDays(10), BASE.plusDays(30), NOW.minusDays(50));
    }

    /** 日记型用户的稀疏会话：沉默期只有两次。 */
    private static List<LocalDateTime> diaryHeavyConversations() {
        return List.of(NOW.minusDays(55), NOW.minusDays(20));
    }

    private static MemoryExposureIndex index(List<LocalDateTime> diary, List<LocalDateTime> conversation) {
        return new MemoryExposureIndex(diary, conversation, NOW);
    }

    @Test
    void noContentMeansNoDecay() {
        assertEquals(0.0, index(List.of(), List.of()).weeksSince(REF), 1e-9);
    }

    @Test
    void referenceAfterNowMeansNoDecay() {
        assertEquals(0.0, index(dailyDiaries(), List.of()).weeksSince(NOW.plusDays(1)), 1e-9);
    }

    @Test
    void referenceBeforeFirstContentIsClampedToContentStart() {
        LocalDateTime contentStart = dailyDiaries().get(0);
        double clamped = index(dailyDiaries(), List.of()).weeksSince(BASE.minusDays(200));
        double fromStart = index(dailyDiaries(), List.of()).weeksSince(contentStart);
        assertEquals(fromStart, clamped, 1e-9);
    }

    @Test
    void diaryHeavyUser() {
        // 日记 59 篇 / 7 篇每周 ≈ 8.43 周；会话被 MIN_RATE 压到 2 周 → 8.43 + 0.5*2
        assertEquals(9.43, index(dailyDiaries(), diaryHeavyConversations()).weeksSince(REF), 0.2);
    }

    @Test
    void chatHeavyUser() {
        // 会话 119 / 14 ≈ 8.5 周；日记 1 篇被 MIN_RATE 压到 1 周 → 8.5 + 0.5*1
        assertEquals(9.0, index(chatHeavyDiaries(), frequentConversations()).weeksSince(REF), 0.2);
    }

    @Test
    void bothHeavyUser() {
        assertEquals(12.71, index(dailyDiaries(), frequentConversations()).weeksSince(REF), 0.2);
    }

    @Test
    void threeUserShapesLandInTheSameOrderOfMagnitude() {
        double diaryHeavy = MemoryLifecyclePolicy.freshness(
                index(dailyDiaries(), diaryHeavyConversations()).weeksSince(REF));
        double chatHeavy = MemoryLifecyclePolicy.freshness(
                index(chatHeavyDiaries(), frequentConversations()).weeksSince(REF));
        double bothHeavy = MemoryLifecyclePolicy.freshness(
                index(dailyDiaries(), frequentConversations()).weeksSince(REF));

        double max = Math.max(diaryHeavy, chatHeavy);
        double min = Math.min(diaryHeavy, chatHeavy);
        assertTrue(max / min < 1.2, "日记型与聊天型新鲜度应接近，实际 " + diaryHeavy + " / " + chatHeavy);
        assertTrue(bothHeavy < min, "双来源用户衰减应更快");
        assertTrue(bothHeavy > 0.15 && diaryHeavy < 0.45, "三类用户应位于 decaying 区间附近");
    }

    @Test
    void singleSourceDominanceIsCappedAtOnePointFive() {
        List<LocalDateTime> diary = dailyDiaries();
        List<LocalDateTime> conversation = frequentConversations();
        double diaryOnly = index(diary, List.of()).weeksSince(REF);
        double chatOnly = index(List.of(), conversation).weeksSince(REF);
        double combined = index(diary, conversation).weeksSince(REF);

        double strongest = Math.max(diaryOnly, chatOnly);
        assertTrue(combined >= strongest, "合并曝光不应低于任一单来源");
        assertTrue(combined <= (1 + MemoryLifecyclePolicy.FUSION_WEIGHT) * strongest + 1e-6,
                "任一来源最多主导 " + (1 + MemoryLifecyclePolicy.FUSION_WEIGHT) + " 倍"
                        + " diaryOnly=" + diaryOnly + " chatOnly=" + chatOnly + " combined=" + combined);
    }

    @Test
    void lowFrequencySourceIsNotAmplifiedByItsOwnLowRate() {
        // 240 天只有 5 次会话（速率 0.02/周），沉默 60 天内 2 次。
        // 不做 MIN_RATE 约束时 weeksC = 2 / 0.02 = 100 周，会直接压垮一切。
        List<LocalDateTime> sparse = List.of(NOW.minusDays(58), NOW.minusDays(30),
                BASE.plusDays(40), BASE.plusDays(90), BASE.plusDays(140));
        assertEquals(2.0, index(List.of(), sparse).weeksSince(REF), 0.05);
    }

    @Test
    void newUserRateUsesRealSpanInsteadOfAFixedYear() {
        // 注册一个月写 30 篇：固定 52 周窗口会把速率压到 0.58，把一个月误算成一年。
        LocalDateTime start = NOW.minusDays(30);
        List<LocalDateTime> diary = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            diary.add(start.plusDays(i));
        }
        double weeks = index(diary, List.of()).weeksSince(start.plusDays(20));
        assertTrue(weeks > 0.8 && weeks < 3.0,
                "一个月内 9 篇新日记应折算为约 1.3 周，而不是 52 周，实际 " + weeks);
    }
}
