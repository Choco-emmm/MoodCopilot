package com.moodcopilot.onboarding;

import com.moodcopilot.mapper.UserOnboardingMapper;
import com.moodcopilot.mapper.UserOnboardingMapper.KeyStatusCountRow;
import com.moodcopilot.mapper.UserOnboardingMapper.ScopeUserCountRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class AdminOnboardingMetricsService {

    private final UserOnboardingMapper userOnboardingMapper;

    public AdminOnboardingMetricsService(UserOnboardingMapper userOnboardingMapper) {
        this.userOnboardingMapper = userOnboardingMapper;
    }

    @Transactional(readOnly = true)
    public AdminOnboardingMetricsView getMetrics(Integer requestedGuideVersion) {
        int guideVersion = resolveAndValidateGuideVersion(requestedGuideVersion);
        long eligibleUserCount = zeroIfNull(userOnboardingMapper.countEligibleUsers(guideVersion));

        Map<String, Long> scopeBreakdown = emptyScopeBreakdown();
        if (eligibleUserCount <= 0L) {
            return new AdminOnboardingMetricsView(
                    guideVersion,
                    0L,
                    0L,
                    0L,
                    0.0,
                    0L,
                    0.0,
                    0L,
                    0L,
                    0.0,
                    0L,
                    0L,
                    0L,
                    0.0,
                    0L,
                    Collections.unmodifiableMap(scopeBreakdown)
            );
        }

        EnumMap<GuideKey, EnumMap<GuideStatus, Long>> counts = new EnumMap<>(GuideKey.class);
        List<KeyStatusCountRow> keyStatusRows = userOnboardingMapper.aggregateKeyStatusCountsForEligibleUsers(guideVersion);
        if (keyStatusRows != null) {
            for (KeyStatusCountRow row : keyStatusRows) {
                if (row == null) {
                    continue;
                }
                GuideKey key = GuideKey.parseOrNull(row.getGuideKey());
                GuideStatus status = GuideStatus.parseOrNull(row.getStatus());
                if (key == null || status == null) {
                    continue;
                }
                counts.computeIfAbsent(key, ignored -> new EnumMap<>(GuideStatus.class))
                        .put(status, zeroIfNull(row.getUserCount()));
            }
        }

        List<ScopeUserCountRow> scopeRows = userOnboardingMapper.aggregateScopeCountsForEligibleUsers(guideVersion);
        if (scopeRows != null) {
            for (ScopeUserCountRow row : scopeRows) {
                if (row == null || row.getScope() == null) {
                    continue;
                }
                String normalizedScope = row.getScope().trim().toUpperCase(java.util.Locale.ROOT);
                if (scopeBreakdown.containsKey(normalizedScope)) {
                    scopeBreakdown.put(normalizedScope, zeroIfNull(row.getUserCount()));
                }
            }
        }

        long welcomeCompletedCount = countOf(counts, GuideKey.WELCOME_V1, GuideStatus.COMPLETED);
        long welcomeSkippedCount = countOf(counts, GuideKey.WELCOME_V1, GuideStatus.SKIPPED);
        long firstDiaryCompletedCount = countOf(counts, GuideKey.FIRST_DIARY, GuideStatus.COMPLETED);
        long firstAnalysisViewedCount = countOf(counts, GuideKey.FIRST_ANALYSIS_VIEWED, GuideStatus.COMPLETED);
        long firstChatReplyCompletedCount = countOf(counts, GuideKey.FIRST_CHAT_REPLY, GuideStatus.COMPLETED);
        long memoryCenterVisitedCount = countOf(counts, GuideKey.MEMORY_CENTER_VISITED, GuideStatus.COMPLETED);
        long themeExploredCount = countOf(counts, GuideKey.THEME_EXPLORED, GuideStatus.COMPLETED);
        long allCoreCompletedCount = zeroIfNull(userOnboardingMapper.countAllCoreTasksCompletedForEligibleUsers(guideVersion));
        long crossPlatformHintUserCount = zeroIfNull(userOnboardingMapper.countCrossPlatformHintUsers(guideVersion));

        return new AdminOnboardingMetricsView(
                guideVersion,
                eligibleUserCount,
                welcomeCompletedCount,
                welcomeSkippedCount,
                safeRate(welcomeSkippedCount, eligibleUserCount),
                firstDiaryCompletedCount,
                safeRate(firstDiaryCompletedCount, eligibleUserCount),
                firstAnalysisViewedCount,
                firstChatReplyCompletedCount,
                safeRate(firstChatReplyCompletedCount, eligibleUserCount),
                memoryCenterVisitedCount,
                themeExploredCount,
                allCoreCompletedCount,
                safeRate(allCoreCompletedCount, eligibleUserCount),
                crossPlatformHintUserCount,
                Collections.unmodifiableMap(scopeBreakdown)
        );
    }

    private static int resolveAndValidateGuideVersion(Integer requestedGuideVersion) {
        if (requestedGuideVersion == null) {
            return OnboardingService.CURRENT_GUIDE_VERSION;
        }
        if (requestedGuideVersion <= 0 || requestedGuideVersion != OnboardingService.CURRENT_GUIDE_VERSION) {
            throw new ResponseStatusException(BAD_REQUEST, "不支持的引导版本");
        }
        return requestedGuideVersion;
    }

    private static Map<String, Long> emptyScopeBreakdown() {
        Map<String, Long> breakdown = new LinkedHashMap<>();
        for (GuideScope scope : GuideScope.values()) {
            breakdown.put(scope.name(), 0L);
        }
        return breakdown;
    }

    private static long countOf(EnumMap<GuideKey, EnumMap<GuideStatus, Long>> counts, GuideKey key, GuideStatus status) {
        EnumMap<GuideStatus, Long> statusMap = counts.get(key);
        if (statusMap == null) {
            return 0L;
        }
        return statusMap.getOrDefault(status, 0L);
    }

    private static long zeroIfNull(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }

    static double safeRate(long numerator, long denominator) {
        if (denominator <= 0L || numerator <= 0L) {
            return 0.0;
        }
        double clamped = Math.min((double) numerator / (double) denominator, 1.0);
        return BigDecimal.valueOf(clamped)
                .setScale(4, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
