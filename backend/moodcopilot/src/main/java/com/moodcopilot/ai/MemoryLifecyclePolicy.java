package com.moodcopilot.ai;

import com.moodcopilot.entity.UserProfileMemoryEntity;

import java.util.Locale;
import java.util.Set;

/**
 * 记忆生命周期策略：把「长期未被强化」翻译成可判定、可回滚的状态转移。
 *
 * <p>与 {@link MemoryWritePolicy}、{@link MemorySafetyPolicy} 并列，本类只回答两个问题：
 * 一条正式记忆能不能进入上下文，以及它当前处于哪个生命周期状态。
 *
 * <p><b>新鲜度不是可信度。</b> {@code confidence} 只随证据上升，{@code freshness} 只随时间下降，
 * 二者不可合并：一条两年没被再次提及的事实依然是事实，只是不该再抢占上下文预算。
 *
 * <p><b>归档不等于删除。</b> 归档只写 {@code lifecycle_state}，不改 {@code status}，
 * 因此 {@code status='active'} 的唯一槽位（{@code uk_memory_one_active_key}）继续被占住，
 * 记忆抽取按属性查找仍能命中归档行——新证据到达时复活是免费的字段回写。
 *
 * <p>判定依据 {@code docs/plans/2026-11-17-memory-lifecycle-decay.md}。
 */
public final class MemoryLifecyclePolicy {

    public static final String STATE_ACTIVE = "active";
    public static final String STATE_DECAYING = "decaying";
    public static final String STATE_ARCHIVED = "archived";

    public static final String REASON_STALE_INFERRED = "STALE_INFERRED";
    public static final String REASON_REVIVED_BY_EVIDENCE = "REVIVED_BY_EVIDENCE";
    public static final String REASON_RESTORED_BY_USER = "RESTORED_BY_USER";

    /** 一个「活跃周」对应的曝光单位数。7 = 每天写一篇日记，用于对齐日历语义。 */
    public static final int STEPS_PER_WEEK = 7;
    /** 弱来源的补充系数；单一来源对总曝光的主导上限为 (1 + FUSION_WEIGHT) 倍。 */
    public static final double FUSION_WEIGHT = 0.5;
    /** 来源速率下限（单位/周），防止低频来源的小样本被放大成大量「周」。 */
    public static final double MIN_RATE_PER_WEEK = 1.0;
    /** 速率统计窗口上限（周）。超过部分按最近 52 周估算习惯。 */
    public static final int RATE_WINDOW_WEEKS = 52;

    public static final double DECAYING_BELOW = 0.45;
    public static final double ARCHIVE_BELOW = 0.20;
    public static final double MAX_ARCHIVE_CONFIDENCE = 0.65;
    /** 归档的日历护栏：无论曝光多大，未满该天数的记忆不会被归档。 */
    public static final int MIN_CALENDAR_DAYS = 90;

    /**
     * 参与衰减的记忆类型。刻意用白名单：新增类型默认不衰减，
     * 避免「忘了登记」导致用户记忆被静默归档。
     *
     * <p>排除项及理由：{@code relationship} 关系不因沉默而消失，失效必须有否定证据；
     * {@code event} 与 {@code short_term_state} 已有 {@code validUntil} 机制，重复语义会互相干扰。
     */
    public static final Set<String> DECAYING_MEMORY_TYPES =
            Set.of("preference", "habit", "pattern");

    /**
     * 允许被自动衰减的来源类型。同样用白名单：{@code explicit} 表示用户亲口确认、
     * 审批通过或手动编辑过的记忆，永久豁免自动归档。
     */
    public static final Set<String> DECAYING_SOURCE_TYPES =
            Set.of("diary_inferred", "chat_candidate", "system");

    private MemoryLifecyclePolicy() {
    }

    /**
     * 上下文与索引的准入谓词：只判归档，不判有效期，其余条件由调用方保留。
     */
    public static boolean passLifecycle(UserProfileMemoryEntity memory) {
        return memory != null && !STATE_ARCHIVED.equalsIgnoreCase(stateOf(memory));
    }

    /** 归一化读取生命周期状态，空值视为 {@code active}（兼容迁移前的历史行）。 */
    public static String stateOf(UserProfileMemoryEntity memory) {
        if (memory == null || memory.getLifecycleState() == null) {
            return STATE_ACTIVE;
        }
        String state = memory.getLifecycleState().trim().toLowerCase(Locale.ROOT);
        return state.isEmpty() ? STATE_ACTIVE : state;
    }

    /**
     * 等效活跃周数 → 新鲜度。纯函数，无时间与外部依赖，便于表驱动测试。
     *
     * <p>下限取 0.05 而非 0：避免展示层出现「新鲜度 0%」，也避免排序时大量行挤成同一档。
     */
    public static double freshness(double weeks) {
        if (Double.isNaN(weeks) || weeks <= 0) {
            return 1.0;
        }
        return Math.max(0.05, Math.min(1.0, Math.pow(0.9, weeks)));
    }

    /**
     * 判定目标状态。返回 {@code null} 表示维持现状（调用方应跳过 UPDATE）。
     *
     * @param weeks        自上次证据以来的等效活跃周数，见 {@link MemoryExposureIndex#weeksSince}
     * @param calendarDays 自上次证据以来的日历天数（业务时区）
     */
    public static String evaluate(UserProfileMemoryEntity memory, double weeks, long calendarDays) {
        if (memory == null) {
            return null;
        }
        String current = stateOf(memory);
        if (STATE_ARCHIVED.equals(current)) {
            return null;
        }
        if (!eligibleForDecay(memory)) {
            return null;
        }
        double freshness = freshness(weeks);
        String target;
        if (freshness < ARCHIVE_BELOW
                && calendarDays >= MIN_CALENDAR_DAYS
                && confidenceOf(memory) < MAX_ARCHIVE_CONFIDENCE) {
            target = STATE_ARCHIVED;
        } else if (freshness < DECAYING_BELOW) {
            target = STATE_DECAYING;
        } else {
            target = STATE_ACTIVE;
        }
        return target.equals(current) ? null : target;
    }

    /** 是否具备被自动衰减的资格（与当前状态机位置无关的静态条件）。 */
    public static boolean eligibleForDecay(UserProfileMemoryEntity memory) {
        if (memory == null || Boolean.TRUE.equals(memory.getIsCore())) {
            return false;
        }
        if (!"active".equalsIgnoreCase(memory.getStatus())) {
            return false;
        }
        if (memory.getValidUntil() != null) {
            return false;
        }
        String memoryType = lower(memory.getMemoryType());
        if (!DECAYING_MEMORY_TYPES.contains(memoryType)) {
            return false;
        }
        return DECAYING_SOURCE_TYPES.contains(lower(memory.getSourceType()));
    }

    public static double confidenceOf(UserProfileMemoryEntity memory) {
        Double confidence = memory == null ? null : memory.getConfidence();
        return confidence == null || Double.isNaN(confidence) ? 0.0 : confidence;
    }

    private static String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
