package com.moodcopilot.onboarding;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodcopilot.entity.DiaryAnalysisEntity;
import com.moodcopilot.entity.DiaryEntity;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.entity.UserOnboardingEntity;
import com.moodcopilot.mapper.DiaryAnalysisMapper;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.mapper.UserMapper;
import com.moodcopilot.mapper.UserOnboardingMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class OnboardingService {

    public static final int CURRENT_GUIDE_VERSION = 1;

    private static final Logger log = LoggerFactory.getLogger(OnboardingService.class);
    private static final Set<String> NON_COMPLETE_ANALYSIS_STATUSES = Set.of(
            "analyzing",
            "skipped_user",
            "skipped_quota",
            "failed",
            "failed_limit"
    );

    private final UserOnboardingMapper userOnboardingMapper;
    private final UserMapper userMapper;
    private final DiaryMapper diaryMapper;
    private final DiaryAnalysisMapper diaryAnalysisMapper;

    public OnboardingService(UserOnboardingMapper userOnboardingMapper,
                             UserMapper userMapper,
                             DiaryMapper diaryMapper,
                             DiaryAnalysisMapper diaryAnalysisMapper) {
        this.userOnboardingMapper = userOnboardingMapper;
        this.userMapper = userMapper;
        this.diaryMapper = diaryMapper;
        this.diaryAnalysisMapper = diaryAnalysisMapper;
    }

    public OnboardingStateView getState(Long userId, String rawScope) {
        requireUserId(userId);
        GuideScope platformFilter = GuideScope.parseOptionalPlatform(rawScope);
        return buildState(userId, platformFilter);
    }

    public OnboardingStateView recordEvent(Long userId, OnboardingEventRequest request) {
        requireUserId(userId);
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "请求参数不能为空");
        }
        validateGuideVersion(request.guideVersion());
        GuideScope requestedScope = GuideScope.parseOptional(request.scope());
        OnboardingEventType eventType = OnboardingEventType.parseRequired(request.eventType());

        switch (eventType) {
            case WELCOME_STARTED -> upsertStatus(userId, GuideKey.WELCOME_V1, GuideStatus.IN_PROGRESS);
            case WELCOME_COMPLETED -> upsertStatus(userId, GuideKey.WELCOME_V1, GuideStatus.COMPLETED);
            case ANALYSIS_VIEWED -> {
                validateDiaryAnalysisViewed(userId, request.diaryId());
                upsertStatus(userId, GuideKey.FIRST_ANALYSIS_VIEWED, GuideStatus.COMPLETED);
            }
            case MEMORY_CENTER_VISITED -> upsertStatus(userId, GuideKey.MEMORY_CENTER_VISITED, GuideStatus.COMPLETED);
            case THEME_EXPLORED -> upsertStatus(userId, GuideKey.THEME_EXPLORED, GuideStatus.COMPLETED);
            case HINT_SEEN -> {
                GuideKey hintKey = requireHintKey(request.hintKey());
                validateHintScope(hintKey, requestedScope);
                upsertStatus(userId, hintKey, GuideStatus.COMPLETED);
            }
        }

        GuideScope platformFilter = (requestedScope == GuideScope.WEB || requestedScope == GuideScope.MINI_PROGRAM)
                ? requestedScope
                : null;
        return buildState(userId, platformFilter);
    }

    public OnboardingStateView dismiss(Long userId, OnboardingDismissRequest request) {
        requireUserId(userId);
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "请求参数不能为空");
        }
        validateGuideVersion(request.guideVersion());
        GuideScope requestedScope = GuideScope.parseOptional(request.scope());
        OnboardingDismissTarget target = OnboardingDismissTarget.parseRequired(request.target());

        switch (target) {
            case WELCOME -> upsertStatus(userId, GuideKey.WELCOME_V1, GuideStatus.SKIPPED);
            case CHECKLIST -> upsertStatus(userId, GuideKey.STARTER_CHECKLIST_V1, GuideStatus.SKIPPED);
            case TASK -> {
                GuideKey taskKey = requireTaskKey(request.key());
                upsertStatus(userId, taskKey, GuideStatus.SKIPPED);
            }
            case HINT -> {
                GuideKey hintKey = requireHintKey(request.key());
                validateHintScope(hintKey, requestedScope);
                upsertStatus(userId, hintKey, GuideStatus.SKIPPED);
            }
        }

        GuideScope platformFilter = (requestedScope == GuideScope.WEB || requestedScope == GuideScope.MINI_PROGRAM)
                ? requestedScope
                : null;
        return buildState(userId, platformFilter);
    }

    /**
     * Server-side only entry point for real business task completion (e.g. FIRST_DIARY, FIRST_CHAT_REPLY).
     */
    public void recordBusinessTaskCompleted(Long userId, GuideKey taskKey) {
        if (userId == null || taskKey == null || taskKey.category() != GuideKey.Category.TASK) {
            return;
        }
        upsertStatus(userId, taskKey, GuideStatus.COMPLETED);
    }

    private OnboardingStateView buildState(Long userId, GuideScope platformFilter) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "登录状态已失效");
        }
        int enrolledVersion = user.getOnboardingEnrolledVersion() == null ? 0 : user.getOnboardingEnrolledVersion();

        List<UserOnboardingEntity> rows = userOnboardingMapper.selectList(
                new LambdaQueryWrapper<UserOnboardingEntity>()
                        .eq(UserOnboardingEntity::getUserId, userId)
                        .eq(UserOnboardingEntity::getGuideVersion, CURRENT_GUIDE_VERSION));

        EnumMap<GuideKey, GuideStatus> statusByKey = new EnumMap<>(GuideKey.class);
        if (rows != null) {
            for (UserOnboardingEntity row : rows) {
                GuideKey key = GuideKey.parseOrNull(row.getGuideKey());
                GuideStatus status = GuideStatus.parseOrNull(row.getStatus());
                if (key == null || status == null) {
                    continue;
                }
                if (!key.scope().name().equals(row.getScope())) {
                    continue;
                }
                GuideStatus existing = statusByKey.get(key);
                if (existing == null || status.rank() > existing.rank()) {
                    statusByKey.put(key, status);
                }
            }
        }

        if (statusByKey.get(GuideKey.FIRST_DIARY) != GuideStatus.COMPLETED) {
            backfillFirstDiaryIfPresent(userId, statusByKey);
        }

        GuideStatus welcomeStatus = statusByKey.getOrDefault(GuideKey.WELCOME_V1, GuideStatus.NOT_STARTED);

        Map<String, String> tasks = new LinkedHashMap<>();
        for (GuideKey taskKey : GuideKey.ALL_TASKS) {
            tasks.put(taskKey.name(), statusByKey.getOrDefault(taskKey, GuideStatus.NOT_STARTED).name());
        }

        boolean allCoreTasksCompleted = GuideKey.CORE_TASKS.stream()
                .allMatch(task -> statusByKey.get(task) == GuideStatus.COMPLETED);
        GuideStatus checklistStatus = allCoreTasksCompleted
                ? GuideStatus.COMPLETED
                : statusByKey.getOrDefault(GuideKey.STARTER_CHECKLIST_V1, GuideStatus.NOT_STARTED);

        Map<String, String> hints = new LinkedHashMap<>();
        for (GuideKey hintKey : GuideKey.ALL_HINTS) {
            if (!matchesPlatformFilter(hintKey, platformFilter)) {
                continue;
            }
            GuideStatus hintStatus = statusByKey.get(hintKey);
            if (hintStatus != null && hintStatus != GuideStatus.NOT_STARTED) {
                hints.put(hintKey.name(), hintStatus.name());
            }
        }

        return new OnboardingStateView(
                enrolledVersion,
                CURRENT_GUIDE_VERSION,
                welcomeStatus.name(),
                checklistStatus.name(),
                tasks,
                hints
        );
    }

    private void backfillFirstDiaryIfPresent(Long userId, EnumMap<GuideKey, GuideStatus> statusByKey) {
        Integer exists = userOnboardingMapper.existsActiveDiaryForUser(userId);
        if (exists == null) {
            return;
        }
        statusByKey.put(GuideKey.FIRST_DIARY, GuideStatus.COMPLETED);
        try {
            upsertStatus(userId, GuideKey.FIRST_DIARY, GuideStatus.COMPLETED);
        } catch (Exception e) {
            log.warn("回填首次日记引导状态失败 userId={} errorType={}", userId, e.getClass().getSimpleName());
        }
    }

    private void validateDiaryAnalysisViewed(Long userId, Long diaryId) {
        if (diaryId == null || diaryId <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "diaryId 不能为空或无效");
        }
        DiaryEntity diary = diaryMapper.selectById(diaryId);
        if (diary == null || Boolean.TRUE.equals(diary.getIsDeleted())) {
            throw new ResponseStatusException(NOT_FOUND, "日记不存在");
        }
        if (!userId.equals(diary.getAuthorUserId())) {
            throw new ResponseStatusException(FORBIDDEN, "无权上报他人日记的分析查看事件");
        }
        DiaryAnalysisEntity analysis = diaryAnalysisMapper.selectById(diaryId);
        String analysisStatus = diary.getAnalysisStatus();
        boolean nonCompleteStatus = analysisStatus != null
                && NON_COMPLETE_ANALYSIS_STATUSES.contains(analysisStatus.trim());
        if (analysis == null || nonCompleteStatus) {
            throw new ResponseStatusException(BAD_REQUEST, "该日记尚无有效的 AI 分析结果");
        }
    }

    private void upsertStatus(Long userId, GuideKey key, GuideStatus status) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime completedAt = status == GuideStatus.COMPLETED ? now : null;
        userOnboardingMapper.upsertMonotonic(
                userId,
                key.name(),
                CURRENT_GUIDE_VERSION,
                key.scope().name(),
                status.name(),
                completedAt,
                now
        );
    }

    private void validateGuideVersion(Integer guideVersion) {
        if (guideVersion != null && guideVersion != CURRENT_GUIDE_VERSION) {
            throw new ResponseStatusException(BAD_REQUEST, "不支持的引导版本");
        }
    }

    private GuideKey requireTaskKey(String rawKey) {
        GuideKey key = GuideKey.parseOrNull(rawKey);
        if (key == null || key.category() != GuideKey.Category.TASK) {
            throw new ResponseStatusException(BAD_REQUEST, "无效的任务标识");
        }
        return key;
    }

    private GuideKey requireHintKey(String rawKey) {
        GuideKey key = GuideKey.parseOrNull(rawKey);
        if (key == null || key.category() != GuideKey.Category.HINT) {
            throw new ResponseStatusException(BAD_REQUEST, "无效的提示标识");
        }
        return key;
    }

    private void validateHintScope(GuideKey hintKey, GuideScope requestedScope) {
        if (requestedScope == null || hintKey.scope() == GuideScope.GLOBAL) {
            return;
        }
        if (requestedScope != hintKey.scope()) {
            throw new ResponseStatusException(BAD_REQUEST, "提示作用域与服务端定义不匹配");
        }
    }

    private boolean matchesPlatformFilter(GuideKey hintKey, GuideScope platformFilter) {
        if (platformFilter == null) {
            return true;
        }
        return hintKey.scope() == GuideScope.GLOBAL || hintKey.scope() == platformFilter;
    }

    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "登录状态已失效");
        }
    }
}
