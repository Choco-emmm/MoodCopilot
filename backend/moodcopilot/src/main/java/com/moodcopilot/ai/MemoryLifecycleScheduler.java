package com.moodcopilot.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.moodcopilot.entity.ChatConversationEntity;
import com.moodcopilot.entity.DiaryEntity;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.entity.UserProfileMemoryEntity;
import com.moodcopilot.mapper.ChatConversationMapper;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.mapper.UserMapper;
import com.moodcopilot.mapper.UserProfileMemoryMapper;
import com.moodcopilot.service.SystemConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 记忆生命周期维护任务：把长期未被强化的正式记忆推进到 decaying / archived。
 *
 * <p>三档开关存放在 {@code system_config} 的 {@code memory.lifecycle.mode}：
 * <ul>
 *   <li>{@code off}（默认）— 完全不运行，零副作用；</li>
 *   <li>{@code shadow} — 完整计算，只写 {@code freshness}，不改 {@code lifecycle_state}，
 *       用于观察「如果生效会归档多少条」；</li>
 *   <li>{@code enforce} — 按 {@link MemoryLifecyclePolicy#evaluate} 实际改状态。</li>
 * </ul>
 *
 * <p>幂等性由 {@code updated_at} 上的乐观锁保证：任何并发写入（例如抽取追加证据触发的复活）
 * 都会让本次 CAS 落空并跳过，下次任务重算，因此不需要分布式锁。
 */
@Component
public class MemoryLifecycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(MemoryLifecycleScheduler.class);

    static final String MODE_CONFIG_KEY = "memory.lifecycle.mode";
    static final String MODE_OFF = "off";
    static final String MODE_SHADOW = "shadow";
    static final String MODE_ENFORCE = "enforce";

    private static final int USER_BATCH_SIZE = 500;
    /** freshness 变化小于该阈值时不写库，避免每天为全部记忆制造无意义 UPDATE。 */
    private static final double FRESHNESS_WRITE_EPSILON = 0.01;

    private final UserMapper userMapper;
    private final UserProfileMemoryMapper memoryMapper;
    private final DiaryMapper diaryMapper;
    private final ChatConversationMapper chatConversationMapper;
    private final SystemConfigService systemConfigService;
    private final ZoneId businessTimeZone;

    public MemoryLifecycleScheduler(UserMapper userMapper,
            UserProfileMemoryMapper memoryMapper,
            DiaryMapper diaryMapper,
            ChatConversationMapper chatConversationMapper,
            SystemConfigService systemConfigService,
            @Value("${moodcopilot.time-zone:Asia/Shanghai}") String timeZoneId) {
        this.userMapper = userMapper;
        this.memoryMapper = memoryMapper;
        this.diaryMapper = diaryMapper;
        this.chatConversationMapper = chatConversationMapper;
        this.systemConfigService = systemConfigService;
        this.businessTimeZone = parseZoneId(timeZoneId);
    }

    @Scheduled(cron = "${moodcopilot.memory-lifecycle.cron:0 30 4 * * *}")
    public void runDaily() {
        runOnce(resolveMode());
    }

    /** 按指定模式执行一轮，返回统计结果。测试可直接调用，不依赖调度器。 */
    public RunResult runOnce(String mode) {
        String normalized = normalizeMode(mode);
        if (MODE_OFF.equals(normalized)) {
            log.debug("记忆生命周期任务未启用，跳过");
            return new RunResult(0, 0, 0, 0);
        }
        boolean shadow = MODE_SHADOW.equals(normalized);
        long users = 0;
        long scanned = 0;
        long written = 0;
        long archived = 0;

        long cursor = 0;
        while (true) {
            List<Long> batch = nextUserBatch(cursor);
            if (batch.isEmpty()) {
                break;
            }
            for (Long userId : batch) {
                users++;
                try {
                    UserOutcome outcome = processUser(userId, shadow);
                    scanned += outcome.scanned();
                    written += outcome.written();
                    archived += outcome.archived();
                } catch (Exception e) {
                    log.warn("记忆生命周期处理失败 userId={} errorType={}", userId, e.getClass().getSimpleName());
                }
            }
            cursor = batch.get(batch.size() - 1);
            if (batch.size() < USER_BATCH_SIZE) {
                break;
            }
        }

        log.info("记忆生命周期任务完成 mode={} users={} scanned={} written={} archived={}",
                normalized, users, scanned, written, archived);
        return new RunResult(users, scanned, written, archived);
    }

    private UserOutcome processUser(long userId, boolean shadow) {
        List<UserProfileMemoryEntity> candidates = memoryMapper.selectList(
                new LambdaQueryWrapper<UserProfileMemoryEntity>()
                        .eq(UserProfileMemoryEntity::getUserId, userId)
                        .eq(UserProfileMemoryEntity::getStatus, "active")
                        .eq(UserProfileMemoryEntity::getIsCore, false)
                        .in(UserProfileMemoryEntity::getMemoryType, MemoryLifecyclePolicy.DECAYING_MEMORY_TYPES)
                        .in(UserProfileMemoryEntity::getSourceType, MemoryLifecyclePolicy.DECAYING_SOURCE_TYPES)
                        .isNull(UserProfileMemoryEntity::getValidUntil)
                        .ne(UserProfileMemoryEntity::getLifecycleState, MemoryLifecyclePolicy.STATE_ARCHIVED));
        if (candidates.isEmpty()) {
            return UserOutcome.EMPTY;
        }

        LocalDateTime now = LocalDateTime.now(businessTimeZone);
        MemoryExposureIndex index = loadExposure(userId, now);

        int written = 0;
        int archived = 0;
        for (UserProfileMemoryEntity memory : candidates) {
            LocalDateTime reference = referenceTime(memory);
            if (reference == null || reference.isAfter(now)) {
                continue;
            }
            double weeks = index.weeksSince(reference);
            double freshness = MemoryLifecyclePolicy.freshness(weeks);
            long calendarDays = Duration.between(reference, now).toDays();
            String target = shadow ? null : MemoryLifecyclePolicy.evaluate(memory, weeks, calendarDays);

            Double current = memory.getFreshness();
            boolean freshnessMoved = current == null || Math.abs(current - freshness) >= FRESHNESS_WRITE_EPSILON;
            if (target == null && !freshnessMoved) {
                continue;
            }
            if (applyCas(memory, target, freshness, now, shadow)) {
                written++;
                if (MemoryLifecyclePolicy.STATE_ARCHIVED.equals(target)) {
                    archived++;
                    log.info("记忆归档 userId={} memoryId={} weeks={} calendarDays={} confidence={}",
                            userId, memory.getId(), round(weeks), calendarDays, memory.getConfidence());
                } else if (shadow && freshnessMoved) {
                    log.debug("记忆新鲜度观测 userId={} memoryId={} weeks={} freshness={}",
                            userId, memory.getId(), round(weeks), round(freshness));
                }
            }
        }
        return new UserOutcome(candidates.size(), written, archived);
    }

    /**
     * 条件更新。以 {@code updated_at} 作为乐观锁，并把它显式写回原值，
     * 避免每天刷 freshness 时触发 {@code ON UPDATE CURRENT_TIMESTAMP} 污染「更新时间」语义。
     */
    private boolean applyCas(UserProfileMemoryEntity memory, String target, double freshness,
            LocalDateTime now, boolean shadow) {
        LambdaUpdateWrapper<UserProfileMemoryEntity> update = new LambdaUpdateWrapper<UserProfileMemoryEntity>()
                .eq(UserProfileMemoryEntity::getId, memory.getId())
                .eq(UserProfileMemoryEntity::getStatus, "active")
                .eq(UserProfileMemoryEntity::getLifecycleState, MemoryLifecyclePolicy.stateOf(memory))
                .set(UserProfileMemoryEntity::getFreshness, round(freshness))
                .set(UserProfileMemoryEntity::getLifecycleUpdatedAt, now);
        if (memory.getUpdatedAt() != null) {
            update.eq(UserProfileMemoryEntity::getUpdatedAt, memory.getUpdatedAt())
                    .set(UserProfileMemoryEntity::getUpdatedAt, memory.getUpdatedAt());
        }
        if (!shadow && target != null) {
            update.set(UserProfileMemoryEntity::getLifecycleState, target);
            if (MemoryLifecyclePolicy.STATE_ARCHIVED.equals(target)) {
                update.set(UserProfileMemoryEntity::getLifecycleReason, MemoryLifecyclePolicy.REASON_STALE_INFERRED);
            }
        }
        return memoryMapper.update(null, update) > 0;
    }

    /**
     * 构建曝光索引：一次取该用户全部日记与聊天会话的时间戳，供其所有候选记忆复用。
     *
     * <p>不加时间下界——速率归一化需要完整历史来定位内容起点。{@code DiaryEntity} 上的
     * {@code @TableLogic} 会让 MyBatis-Plus 自动追加 {@code is_deleted = 0}，
     * 因此已删除日记不会被计入曝光。
     */
    private MemoryExposureIndex loadExposure(long userId, LocalDateTime now) {
        List<LocalDateTime> diaryTimes = diaryMapper.selectList(new LambdaQueryWrapper<DiaryEntity>()
                .select(DiaryEntity::getCreatedAt)
                .eq(DiaryEntity::getAuthorUserId, userId)
                .orderByAsc(DiaryEntity::getCreatedAt))
                .stream()
                .map(DiaryEntity::getCreatedAt)
                .filter(Objects::nonNull)
                .toList();
        List<LocalDateTime> conversationTimes = chatConversationMapper.selectList(
                new LambdaQueryWrapper<ChatConversationEntity>()
                        .select(ChatConversationEntity::getCreatedAt)
                        .eq(ChatConversationEntity::getUserId, userId)
                        .orderByAsc(ChatConversationEntity::getCreatedAt))
                .stream()
                .map(ChatConversationEntity::getCreatedAt)
                .filter(Objects::nonNull)
                .toList();
        return new MemoryExposureIndex(diaryTimes, conversationTimes, now);
    }

    private List<Long> nextUserBatch(long cursor) {
        return userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                .select(UserEntity::getId)
                .gt(UserEntity::getId, cursor)
                .orderByAsc(UserEntity::getId)
                .last("LIMIT " + USER_BATCH_SIZE))
                .stream()
                .map(UserEntity::getId)
                .filter(Objects::nonNull)
                .toList();
    }

    /** 参照时间：证据时间优先，其次写入时间。 */
    private static LocalDateTime referenceTime(UserProfileMemoryEntity memory) {
        if (memory.getLastEvidenceAt() != null) {
            return memory.getLastEvidenceAt();
        }
        if (memory.getUpdatedAt() != null) {
            return memory.getUpdatedAt();
        }
        return memory.getUpdateTime();
    }

    /** 配置读取失败一律退回 {@code off}：状态未知时不做任何写入。 */
    private String resolveMode() {
        try {
            String raw = systemConfigService.getConfig(MODE_CONFIG_KEY, new TypeReference<String>() {
            }, MODE_OFF);
            return normalizeMode(raw);
        } catch (Exception e) {
            log.warn("记忆生命周期开关读取失败，按 off 处理 errorType={}", e.getClass().getSimpleName());
            return MODE_OFF;
        }
    }

    private static String normalizeMode(String mode) {
        if (mode == null) {
            return MODE_OFF;
        }
        String value = mode.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case MODE_SHADOW, MODE_ENFORCE -> value;
            default -> MODE_OFF;
        };
    }

    private static double round(double value) {
        return Math.round(value * 10000d) / 10000d;
    }

    private static ZoneId parseZoneId(String value) {
        try {
            return value == null || value.isBlank() ? ZoneId.of("Asia/Shanghai") : ZoneId.of(value.trim());
        } catch (RuntimeException e) {
            log.warn("记忆生命周期业务时区配置无效，使用 Asia/Shanghai: {}", value);
            return ZoneId.of("Asia/Shanghai");
        }
    }

    public record RunResult(long users, long scanned, long written, long archived) {
    }

    private record UserOutcome(int scanned, int written, int archived) {
        static final UserOutcome EMPTY = new UserOutcome(0, 0, 0);
    }
}
