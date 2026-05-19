package com.moodcopilot.growth;

import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.mapper.UserMapper;
import com.moodcopilot.notification.NotificationService;
import com.moodcopilot.security.RateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class UserGrowthService {

    private static final Logger log = LoggerFactory.getLogger(UserGrowthService.class);
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int[] LEVEL_THRESHOLDS = {0, 150, 500, 1500, 4000, 10000};

    private final StringRedisTemplate redis;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    public UserGrowthService(StringRedisTemplate redis, UserMapper userMapper,
            NotificationService notificationService) {
        this.redis = redis;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
    }

    /**
     * 根据总 EXP 计算等级。
     */
    public static int getLevelForExp(int exp) {
        for (int i = LEVEL_THRESHOLDS.length - 1; i >= 0; i--) {
            if (exp >= LEVEL_THRESHOLDS[i]) {
                return i + 1;
            }
        }
        return 1;
    }

    /**
     * 获取升到下一级所需 EXP（满级返回 -1）。
     */
    public static int getExpToNextLevel(int level) {
        if (level >= LEVEL_THRESHOLDS.length) {
            return -1;
        }
        return LEVEL_THRESHOLDS[level];
    }

    // ── 签到 ──

    /**
     * 每日签到。返回本次获得的 EXP，如果已签到返回 0。
     */
    public int checkIn(Long userId) {
        LocalDate today = LocalDate.now();
        String bitmapKey = checkinBitmapKey(userId, today);

        int dayOfMonth = today.getDayOfMonth();
        Boolean alreadyChecked = redis.opsForValue().getBit(bitmapKey, dayOfMonth - 1);
        if (Boolean.TRUE.equals(alreadyChecked)) {
            return 0;
        }

        // 检查 Redis Hash 是否已记录今日签到（双重防护）
        String dailyKey = dailyKey(userId, today);
        String current = (String) redis.opsForHash().get(dailyKey, "checkin");
        if ("1".equals(current)) {
            return 0;
        }

        int streak = countStreakBeforeToday(userId, today);
        int consecutiveDays = streak + 1; // 含今天
        int exp = consecutiveDays >= 7 ? 25 : 10 + (consecutiveDays - 1) * 2;

        redis.opsForValue().setBit(bitmapKey, dayOfMonth - 1, true);
        redis.opsForHash().put(dailyKey, "checkin", "1");
        long secondsUntilMidnight = LocalDateTime.now().until(
                today.plusDays(1).atStartOfDay(), ChronoUnit.SECONDS);
        redis.expire(dailyKey, Duration.ofSeconds(secondsUntilMidnight));

        applyExp(userId, exp);
        log.info("签到成功，userId={}，streak={}，exp={}", userId, consecutiveDays, exp);
        return exp;
    }

    /**
     * 获取本月签到位图（day-of-month → boolean）。
     */
    public boolean[] getMonthCheckins(Long userId) {
        LocalDate today = LocalDate.now();
        String bitmapKey = checkinBitmapKey(userId, today);
        int daysInMonth = today.lengthOfMonth();
        boolean[] result = new boolean[daysInMonth];
        for (int i = 0; i < daysInMonth; i++) {
            result[i] = Boolean.TRUE.equals(redis.opsForValue().getBit(bitmapKey, i));
        }
        return result;
    }

    private int countStreakBeforeToday(Long userId, LocalDate today) {
        int streak = 0;
        LocalDate cursor = today.minusDays(1);
        // 限制最多回溯 62 天（两个完整月份），防止异常情况下的无限循环
        for (int i = 0; i < 62; i++) {
            String key = checkinBitmapKey(userId, cursor);
            Boolean checked = redis.opsForValue().getBit(key, cursor.getDayOfMonth() - 1);
            if (Boolean.TRUE.equals(checked)) {
                streak++;
                cursor = cursor.minusDays(1);
            } else {
                break;
            }
        }
        return streak;
    }

    /**
     * 使用 BITFIELD 统计本月签到总次数。
     */
    private int countMonthCheckins(Long userId, LocalDate today) {
        String key = checkinBitmapKey(userId, today);
        int dayOfMonth = today.getDayOfMonth();

        List<Long> result = redis.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0));

        if (result == null || result.isEmpty()) {
            return 0;
        }
        Long num = result.getFirst();
        if (num == null || num == 0) {
            return 0;
        }
        return Long.bitCount(num);
    }

    private String checkinBitmapKey(Long userId, LocalDate date) {
        return "moodcopilot:checkin:" + userId + ":" + date.format(YM);
    }

    // ── 经验值 ──

    /**
     * 增加经验值（带每日上限和自动升级）。
     * 返回本次实际获得的 EXP（被上限截断或已满则返回 0）。
     *
     * @param context 附加上下文：DIARY 时为日记内容字数；CHAT 时可为 null
     */
    @Transactional
    public int addExp(Long userId, ExpAction action, Integer context) {
        LocalDate today = LocalDate.now();
        String dailyKey = dailyKey(userId, today);

        // 检查每日各项行为次数
        String field = action.name().toLowerCase();
        String currentVal = (String) redis.opsForHash().get(dailyKey, field);
        int currentCount = currentVal == null ? 0 : Integer.parseInt(currentVal);

        int maxPerDay = action.getMaxPerDay();
        if (currentCount >= maxPerDay) {
            return 0;
        }

        int exp = action.getBaseExp();
        if (action == ExpAction.DIARY && context != null && context > 100) {
            exp += 10;
        }

        redis.opsForHash().increment(dailyKey, field, 1);
        long secondsUntilMidnight = LocalDateTime.now().until(
                today.plusDays(1).atStartOfDay(), ChronoUnit.SECONDS);
        redis.expire(dailyKey, Duration.ofSeconds(secondsUntilMidnight));

        applyExp(userId, exp);
        return exp;
    }

    private void applyExp(Long userId, int exp) {
        if (exp <= 0) {
            return;
        }
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            log.warn("applyExp 用户不存在 userId={}", userId);
            return;
        }

        int oldExp = user.getExp() != null ? user.getExp() : 0;
        int oldLevel = user.getLevel() != null ? user.getLevel() : 1;
        int newExp = oldExp + exp;
        int newLevel = getLevelForExp(newExp);

        user.setExp(newExp);
        user.setLevel(newLevel);
        userMapper.updateById(user);

        if (newLevel > oldLevel) {
            log.info("用户升级 userId={}，Lv.{} → Lv.{}，exp={}", userId, oldLevel, newLevel, newExp);
            try {
                String msg = buildLevelUpMessage(newLevel, user);
                notificationService.notifyDailyFollowUp(userId, msg);
            } catch (Exception e) {
                log.warn("升级通知发送失败 userId={}", userId, e);
            }
        }
    }

    private String buildLevelUpMessage(int level, UserEntity user) {
        int chatLimit = RateLimitService.getDynamicLimit(RateLimitService.AiApiType.CHAT, level, false);
        int resonanceLimit = RateLimitService.getDynamicLimit(RateLimitService.AiApiType.RESONANCE, level, false);

        StringBuilder sb = new StringBuilder();
        sb.append("🎉 恭喜升级！你已达到 **Lv.").append(level).append("**\n\n");
        sb.append("- 每日聊天次数：**").append(chatLimit).append("** 次\n");
        if (resonanceLimit > 0) {
            sb.append("- 已解锁共鸣检索功能（").append(resonanceLimit).append("次/天）\n");
        }
        if (level >= 6) {
            sb.append("- 报告生成已解锁无限额度\n");
        }
        sb.append("\n继续记录心情，解锁更多能力～");
        return sb.toString();
    }

    private String dailyKey(Long userId, LocalDate date) {
        return "moodcopilot:exp:daily:" + userId + ":" + date.format(YMD);
    }

    // ── 查询 ──

    public record GrowthStatus(int exp, int level, int expToNextLevel, int streak, int monthCheckins, boolean checkedInToday) {}

    public record DailyExpBar(String label, String field, int current, int max, int expPerAction) {}

    /**
     * 获取今日各行为的经验进度。
     */
    public List<DailyExpBar> getTodayProgress(Long userId) {
        LocalDate today = LocalDate.now();
        String dailyKey = dailyKey(userId, today);
        List<DailyExpBar> bars = new ArrayList<>();

        for (ExpAction action : ExpAction.values()) {
            String field = action.name().toLowerCase();
            String val = (String) redis.opsForHash().get(dailyKey, field);
            int current = val == null ? 0 : Integer.parseInt(val);
            bars.add(new DailyExpBar(
                    action.getLabel(),
                    field,
                    current,
                    action.getMaxPerDay(),
                    action.getBaseExp()));
        }
        return bars;
    }

    /**
     * 获取用户成长状态（等级、经验、连续签到天数、今日是否已签到）。
     */
    public GrowthStatus getGrowthStatus(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            return new GrowthStatus(0, 1, LEVEL_THRESHOLDS[1], 0, 0, false);
        }

        int exp = user.getExp() != null ? user.getExp() : 0;
        int level = user.getLevel() != null ? user.getLevel() : 1;
        int expToNext = getExpToNextLevel(level);

        LocalDate today = LocalDate.now();
        String bitmapKey = checkinBitmapKey(userId, today);
        Boolean checkedIn = redis.opsForValue().getBit(bitmapKey, today.getDayOfMonth() - 1);
        int streak = countStreakBeforeToday(userId, today);
        int monthCheckins = countMonthCheckins(userId, today);

        return new GrowthStatus(exp, level, expToNext, streak, monthCheckins, Boolean.TRUE.equals(checkedIn));
    }
}
