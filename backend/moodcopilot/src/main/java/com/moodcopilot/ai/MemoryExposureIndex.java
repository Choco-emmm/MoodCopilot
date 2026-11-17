package com.moodcopilot.ai;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 记忆生命周期的曝光索引：把一个用户的内容时间戳预先排序，供该用户全部记忆复用。
 *
 * <p>解决的核心问题是<b>量纲对齐</b>：日记与聊天的产出频率能差几个数量级，
 * 直接加权等于把「谁的计数大」错当成「谁更能代表活跃」。这里先把每个来源的计数
 * 除以该用户自己的典型速率，折算成同量纲的「等效活跃周数」，再做融合。
 *
 * <p>融合方式为 {@code max(a, b) + FUSION_WEIGHT * min(a, b)}，保证
 * {@code max(a,b) <= weeks <= (1 + FUSION_WEIGHT) * max(a,b)}——
 * 任一来源最多把总曝光主导到 1.5 倍，且该约束相对于双方实际规模，
 * 在任何时间尺度上都成立（绝对上限会被长期记忆一直顶满而失效）。
 *
 * <p>本类只做纯计算，不访问数据库、不读写状态，可在测试中直接构造。
 */
public final class MemoryExposureIndex {

    private final LocalDateTime[] diary;
    private final LocalDateTime[] conversation;
    private final LocalDateTime now;

    public MemoryExposureIndex(List<LocalDateTime> diary, List<LocalDateTime> conversation, LocalDateTime now) {
        this.diary = sortedCopy(diary);
        this.conversation = sortedCopy(conversation);
        this.now = now == null ? LocalDateTime.now() : now;
    }

    /**
     * 计算自 {@code ref} 以来的等效活跃周数。
     *
     * <p>{@code ref} 早于最早内容时按最早内容处理——在任何内容产生之前不存在
     * 「错过的重新提及机会」，否则一条早于全部内容的记忆会被一次性判定为高度陈旧。
     */
    public double weeksSince(LocalDateTime ref) {
        LocalDateTime contentStart = earliestContent();
        if (contentStart == null) {
            return 0.0;
        }
        LocalDateTime from = ref == null || ref.isBefore(contentStart) ? contentStart : ref;
        if (!from.isBefore(now)) {
            return 0.0;
        }

        double spanWeeks = spanWeeks(contentStart);
        LocalDateTime windowStart = now.minusMinutes((long) (spanWeeks * 7 * 24 * 60));
        double rateDiary = ratePerWeek(diary, windowStart, now, spanWeeks);
        double rateConversation = ratePerWeek(conversation, windowStart, now, spanWeeks);

        double weeksDiary = countAfter(diary, from) / rateDiary;
        double weeksConversation = countAfter(conversation, from) / rateConversation;

        double strong = Math.max(weeksDiary, weeksConversation);
        double weak = Math.min(weeksDiary, weeksConversation);
        return strong + MemoryLifecyclePolicy.FUSION_WEIGHT * weak;
    }

    /** 曝光单位数，仅供日志与落库展示使用。 */
    public double exposureUnits(LocalDateTime ref) {
        return weeksSince(ref) * MemoryLifecyclePolicy.STEPS_PER_WEEK;
    }

    /** 该用户内容跨度（周），上限 {@link MemoryLifecyclePolicy#RATE_WINDOW_WEEKS}。 */
    private double spanWeeks(LocalDateTime contentStart) {
        double weeks = Duration.between(contentStart, now).toMinutes() / (7.0 * 24 * 60);
        return Math.max(1.0, Math.min(MemoryLifecyclePolicy.RATE_WINDOW_WEEKS, weeks));
    }

    /**
     * 某来源的典型速率（单位/周）。用真实内容跨度做分母而不是固定 52 周：
     * 新用户注册一个月写 30 篇，固定窗口会把速率压到 0.58 篇/周，
     * 把「一个月」误算成「一年」并触发过度归档。
     *
     * @param windowStart 速率统计窗口起点；跨度不足 52 周时等于最早内容时间
     */
    private static double ratePerWeek(LocalDateTime[] timestamps, LocalDateTime windowStart,
                                      LocalDateTime windowEnd, double spanWeeks) {
        double observed = countBetween(timestamps, windowStart, windowEnd) / spanWeeks;
        return Math.max(MemoryLifecyclePolicy.MIN_RATE_PER_WEEK, observed);
    }

    private LocalDateTime earliestContent() {
        LocalDateTime firstDiary = diary.length == 0 ? null : diary[0];
        LocalDateTime firstConversation = conversation.length == 0 ? null : conversation[0];
        if (firstDiary == null) {
            return firstConversation;
        }
        if (firstConversation == null) {
            return firstDiary;
        }
        return firstDiary.isBefore(firstConversation) ? firstDiary : firstConversation;
    }

    /** 严格晚于 {@code ref} 的元素个数。 */
    private static int countAfter(LocalDateTime[] timestamps, LocalDateTime ref) {
        return timestamps.length - upperBound(timestamps, ref);
    }

    /** 位于 {@code [from, to]} 区间内的元素个数；{@code to} 为 null 表示到无穷。 */
    private static int countBetween(LocalDateTime[] timestamps, LocalDateTime from, LocalDateTime to) {
        int lower = lowerBound(timestamps, from);
        int upper = to == null ? timestamps.length : upperBound(timestamps, to);
        return Math.max(0, upper - lower);
    }

    /** 第一个大于 {@code key} 的下标。 */
    private static int upperBound(LocalDateTime[] timestamps, LocalDateTime key) {
        int low = 0;
        int high = timestamps.length;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (timestamps[mid].compareTo(key) <= 0) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    /** 第一个大于等于 {@code key} 的下标。 */
    private static int lowerBound(LocalDateTime[] timestamps, LocalDateTime key) {
        int low = 0;
        int high = timestamps.length;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (timestamps[mid].compareTo(key) < 0) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    private static LocalDateTime[] sortedCopy(List<LocalDateTime> timestamps) {
        if (timestamps == null || timestamps.isEmpty()) {
            return new LocalDateTime[0];
        }
        LocalDateTime[] copy = timestamps.stream().filter(java.util.Objects::nonNull)
                .toArray(LocalDateTime[]::new);
        Arrays.sort(copy);
        return copy;
    }
}
