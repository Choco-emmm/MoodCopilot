package com.moodcopilot.diary;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.ai.AiAnalysisService;
import com.moodcopilot.common.ContentFilter;
import com.moodcopilot.common.RateLimitException;
import com.moodcopilot.security.RateLimitService;
import com.moodcopilot.entity.DiaryAnalysisEntity;
import com.moodcopilot.follow.FollowService;
import com.moodcopilot.entity.DiaryCommentEntity;
import com.moodcopilot.entity.DiaryEntity;
import com.moodcopilot.entity.DiaryResonanceEntity;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.mapper.DiaryAnalysisMapper;
import com.moodcopilot.mapper.DiaryCommentMapper;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.mapper.DiaryResonanceMapper;
import com.moodcopilot.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DiaryService {

    private final DiaryMapper diaryMapper;
    private final DiaryAnalysisMapper diaryAnalysisMapper;
    private final DiaryCommentMapper diaryCommentMapper;
    private final DiaryResonanceMapper diaryResonanceMapper;
    private static final Logger log = LoggerFactory.getLogger(DiaryService.class);

    private final AiAnalysisService aiAnalysisService;
    private final NotificationService notificationService;
    private final FollowService followService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RateLimitService rateLimitService;

    public DiaryService(DiaryMapper diaryMapper,
                        DiaryAnalysisMapper diaryAnalysisMapper,
                        DiaryCommentMapper diaryCommentMapper,
                        DiaryResonanceMapper diaryResonanceMapper,
                        AiAnalysisService aiAnalysisService,
                        NotificationService notificationService,
                        FollowService followService,
                        StringRedisTemplate redisTemplate,
                        ObjectMapper objectMapper,
                        RateLimitService rateLimitService) {
        this.diaryMapper = diaryMapper;
        this.diaryAnalysisMapper = diaryAnalysisMapper;
        this.diaryCommentMapper = diaryCommentMapper;
        this.diaryResonanceMapper = diaryResonanceMapper;
        this.aiAnalysisService = aiAnalysisService;
        this.notificationService = notificationService;
        this.followService = followService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.rateLimitService = rateLimitService;
    }

    @Transactional
    public DiaryView create(CreateDiaryRequest request) {
        String content = normalizeContent(request.content());
        DiaryVisibility visibility = parseVisibility(request.visibility());

        DiaryEntity diary = new DiaryEntity();
        UserEntity user = currentUser();
        diary.setAuthorUserId(user.getId());
        diary.setAuthorName(user.getDisplayName());
        diary.setContent(ContentFilter.filter(content));
        diary.setVisibility(visibility.name());
        diary.setResonanceCount(0);
        diary.setIsDeleted(false);
        diary.setCreatedAt(LocalDateTime.now());
        diary.setUpdatedAt(LocalDateTime.now());
        diaryMapper.insert(diary);

        evictUserCache();

        return DiaryView.from(diary, List.of());
    }

    @Async
    @Transactional
    public void runAiAnalysis(long diaryId, String content) {
        DiaryEntity diary = diaryMapper.selectById(diaryId);
        if (diary != null) {
            rateLimitService.tryAcquire(diary.getAuthorUserId(), RateLimitService.AiApiType.ANALYSIS);
        }
        DiaryAnalysis analysis = aiAnalysisService.analyze(content);

        DiaryAnalysisEntity analysisEntity = new DiaryAnalysisEntity();
        analysisEntity.setDiaryId(diaryId);
        analysisEntity.setMoodLabel(analysis.moodLabel());
        analysisEntity.setMoodIntensity(analysis.moodIntensity());
        analysisEntity.setTopicLabelsJson(analysis.topicLabels());
        analysisEntity.setSummary(analysis.summary());
        analysisEntity.setFeedback(analysis.feedback());
        analysisEntity.setCreatedAt(LocalDateTime.now());
        analysisEntity.setUpdatedAt(LocalDateTime.now());
        diaryAnalysisMapper.insert(analysisEntity);
    }

    public List<DiaryView> myDiaries(int page, int size) {
        size = Math.min(50, Math.max(1, size));
        Long userId = currentUser().getId();
        Page<DiaryEntity> result = diaryMapper.selectPage(
                Page.of(page, size),
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getAuthorUserId, userId)
                        .orderByDesc(DiaryEntity::getCreatedAt));
        List<DiaryEntity> diaries = result.getRecords();
        List<Long> ids = diaries.stream().map(DiaryEntity::getId).toList();
        Map<Long, DiaryAnalysisEntity> analysisMap = batchLoadAnalyses(ids);
        Map<Long, List<DiaryCommentEntity>> commentMap = batchLoadComments(ids);
        return diaries.stream()
                .map(d -> buildDiaryView(d, false, analysisMap, commentMap))
                .toList();
    }

    public Page<DiaryView> publicDiaries(int page, int size) {
        int cappedPage = Math.max(1, page);
        int cappedSize = Math.min(50, Math.max(1, size));
        String cacheKey = "public:diaries:%d:%d".formatted(cappedPage, cappedSize);

        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) return objectMapper.readValue(cached, new TypeReference<Page<DiaryView>>() {});
        } catch (Exception e) { log.debug("Cache miss {}", cacheKey); }

        Page<DiaryView> result = queryPublicDiaries(cappedPage, cappedSize);

        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result), Duration.ofMinutes(5));
        } catch (Exception e) { log.debug("Cache write failed"); }
        return result;
    }

    private Page<DiaryView> queryPublicDiaries(int page, int size) {
        Page<DiaryEntity> entityPage = diaryMapper.selectPage(
                Page.of(page, size),
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getVisibility, "PUBLIC")
                        .orderByDesc(DiaryEntity::getCreatedAt)
        );
        List<DiaryEntity> diaries = entityPage.getRecords();
        List<Long> ids = diaries.stream().map(DiaryEntity::getId).toList();
        Map<Long, DiaryAnalysisEntity> analysisMap = batchLoadAnalyses(ids);
        Map<Long, List<DiaryCommentEntity>> commentMap = batchLoadComments(ids);
        List<DiaryView> views = diaries.stream()
                .map(d -> buildDiaryView(d, true, analysisMap, commentMap))
                .toList();
        Page<DiaryView> viewPage = new Page<>(page, size, entityPage.getTotal());
        viewPage.setRecords(views);
        return viewPage;
    }

    public DiaryView get(long id) {
        DiaryEntity diary = findDiary(id);
        DiaryAnalysisEntity analysis = findAnalysis(id);
        List<DiaryCommentEntity> comments = findComments(id);
        boolean isOwner = diary.getAuthorUserId().equals(currentUser().getId());
        return isOwner ? DiaryView.from(diary, analysis, comments)
                       : DiaryView.fromPublic(diary, analysis, comments);
    }

    public List<DiaryView> similar(long id, int limit) {
        DiaryEntity source = findDiary(id);
        DiaryAnalysisEntity sourceAnalysis = findAnalysis(id);

        // 限定候选池大小：取最近 200 篇公开日记
        Page<DiaryEntity> candidates = diaryMapper.selectPage(
                Page.of(1, 200),
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getVisibility, "PUBLIC")
                        .ne(DiaryEntity::getId, id)
                        .orderByDesc(DiaryEntity::getCreatedAt));

        int cappedLimit = Math.max(1, Math.min(limit, 10));
        List<DiaryEntity> diaries = candidates.getRecords();

        // 批量加载分析
        List<Long> ids = diaries.stream().map(DiaryEntity::getId).toList();
        Map<Long, DiaryAnalysisEntity> analysisMap = batchLoadAnalyses(ids);

        // 相似度排序
        List<DiaryEntity> sorted = diaries.stream()
                .sorted(Comparator.comparingDouble((DiaryEntity d) ->
                        similarityScore(sourceAnalysis, analysisMap.get(d.getId()))).reversed())
                .toList();

        // 去重：每人最多 1 篇
        Set<Long> seenUsers = new HashSet<>();
        List<DiaryView> result = new ArrayList<>();
        for (DiaryEntity d : sorted) {
            if (seenUsers.add(d.getAuthorUserId())) {
                result.add(buildDiaryView(d, true, analysisMap, Map.of()));
                if (result.size() >= cappedLimit) break;
            }
        }
        return result;
    }

    public Page<DiaryView> followingDiaries(int page, int size) {
        Long userId = currentUser().getId();
        int cappedPage = Math.max(1, page);
        int cappedSize = Math.min(50, Math.max(1, size));
        String cacheKey = "following:%d:%d:%d".formatted(userId, cappedPage, cappedSize);

        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) return objectMapper.readValue(cached, new TypeReference<Page<DiaryView>>() {});
        } catch (Exception e) { log.debug("Cache miss {}", cacheKey); }

        Page<DiaryView> result = queryFollowingDiaries(userId, cappedPage, cappedSize);

        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result), Duration.ofMinutes(5));
        } catch (Exception e) { log.debug("Cache write failed"); }
        return result;
    }

    private Page<DiaryView> queryFollowingDiaries(long userId, int page, int size) {
        List<Long> followingIds = followService.getFollowingIds(userId);
        if (followingIds.isEmpty()) {
            Page<DiaryView> empty = new Page<>(page, size, 0);
            empty.setRecords(List.of());
            return empty;
        }
        Page<DiaryEntity> entityPage = diaryMapper.selectPage(
                Page.of(page, size),
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getVisibility, "PUBLIC")
                        .in(DiaryEntity::getAuthorUserId, followingIds)
                        .orderByDesc(DiaryEntity::getCreatedAt)
        );
        List<DiaryEntity> diaries = entityPage.getRecords();
        List<Long> ids = diaries.stream().map(DiaryEntity::getId).toList();
        Map<Long, DiaryAnalysisEntity> analysisMap = batchLoadAnalyses(ids);
        Map<Long, List<DiaryCommentEntity>> commentMap = batchLoadComments(ids);
        List<DiaryView> views = diaries.stream()
                .map(d -> buildDiaryView(d, true, analysisMap, commentMap))
                .toList();
        Page<DiaryView> viewPage = new Page<>(page, size, entityPage.getTotal());
        viewPage.setRecords(views);
        return viewPage;
    }

    // ── Monthly report ──

    public WeeklyReportView monthlyReport(int monthOffset) {
        Long userId = currentUser().getId();
        String cacheKey = "report:monthly:%d:%d".formatted(userId, monthOffset);

        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) return objectMapper.readValue(cached, WeeklyReportView.class);
        } catch (Exception e) {
            log.debug("Cache read failed for {}", cacheKey, e);
        }

        WeeklyReportView report = computeMonthlyReport(monthOffset, userId);

        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(report), Duration.ofMinutes(30));
        } catch (Exception e) {
            log.debug("Cache write failed for {}", cacheKey, e);
        }
        return report;
    }

    private WeeklyReportView computeMonthlyReport(int monthOffset, long userId) {
        LocalDate today = LocalDate.now();
        LocalDate firstOfMonth = today.withDayOfMonth(1).plusMonths(monthOffset);
        LocalDate lastOfMonth = firstOfMonth.withDayOfMonth(firstOfMonth.lengthOfMonth());

        LocalDateTime start = firstOfMonth.atStartOfDay();
        LocalDateTime end = lastOfMonth.atTime(LocalTime.MAX);

        List<DiaryEntity> diaries = diaryMapper.selectList(
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getAuthorUserId, userId)
                        .ge(DiaryEntity::getCreatedAt, start)
                        .le(DiaryEntity::getCreatedAt, end)
                        .orderByAsc(DiaryEntity::getCreatedAt)
        );

        // 批量加载分析
        List<Long> ids = diaries.stream().map(DiaryEntity::getId).toList();
        Map<Long, DiaryAnalysisEntity> analysisMap = batchLoadAnalyses(ids);

        List<WeeklyReportView.DailyMood> dailyMoods = new ArrayList<>();
        Map<String, Integer> topicCounts = new LinkedHashMap<>();
        List<String> contents = new ArrayList<>();
        List<DiaryAnalysis> analyses = new ArrayList<>();

        for (DiaryEntity diary : diaries) {
            contents.add(diary.getContent());
            DiaryAnalysisEntity analysisEntity = analysisMap.get(diary.getId());
            if (analysisEntity != null) {
                DiaryAnalysis analysis = new DiaryAnalysis(
                        analysisEntity.getMoodLabel(),
                        analysisEntity.getMoodIntensity(),
                        analysisEntity.getTopicLabelsJson(),
                        analysisEntity.getSummary(),
                        analysisEntity.getFeedback()
                );
                analyses.add(analysis);
                dailyMoods.add(new WeeklyReportView.DailyMood(
                        diary.getCreatedAt().toLocalDate(),
                        analysis.moodLabel(),
                        analysis.moodIntensity(),
                        List.of(diary.getId()),
                        snippet(diary.getContent())
                ));
                for (String topic : analysis.topicLabels()) {
                    topicCounts.merge(topic, 1, Integer::sum);
                }
            } else {
                analyses.add(null);
            }
        }

        var sortedTopics = topicCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy年M月");
        String monthLabel = firstOfMonth.format(fmt);

        rateLimitService.tryAcquire(userId, RateLimitService.AiApiType.REPORT);
        String aiSummary = aiAnalysisService.generateMonthlySummary(contents, analyses);

        return new WeeklyReportView(
                monthLabel,
                diaries.size(),
                dailyMoods,
                sortedTopics,
                aiSummary
        );
    }

    // ── Weekly report ──

    public WeeklyReportView weeklyReport(int weekOffset) {
        Long userId = currentUser().getId();
        String cacheKey = "report:%d:%d".formatted(userId, weekOffset);

        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) return objectMapper.readValue(cached, WeeklyReportView.class);
        } catch (Exception e) {
            log.debug("Cache read failed for {}", cacheKey, e);
        }

        WeeklyReportView report = computeWeeklyReport(weekOffset, userId);

        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(report), Duration.ofMinutes(30));
        } catch (Exception e) {
            log.debug("Cache write failed for {}", cacheKey, e);
        }
        return report;
    }

    private WeeklyReportView computeWeeklyReport(int weekOffset, long userId) {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(DayOfWeek.MONDAY).plusWeeks(weekOffset);
        LocalDate sunday = monday.plusDays(6);

        LocalDateTime start = monday.atStartOfDay();
        LocalDateTime end = sunday.atTime(LocalTime.MAX);

        List<DiaryEntity> diaries = diaryMapper.selectList(
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getAuthorUserId, userId)
                        .ge(DiaryEntity::getCreatedAt, start)
                        .le(DiaryEntity::getCreatedAt, end)
                        .orderByAsc(DiaryEntity::getCreatedAt)
        );

        // 批量加载分析
        List<Long> ids = diaries.stream().map(DiaryEntity::getId).toList();
        Map<Long, DiaryAnalysisEntity> analysisMap = batchLoadAnalyses(ids);

        List<WeeklyReportView.DailyMood> dailyMoods = new ArrayList<>();
        Map<String, Integer> topicCounts = new LinkedHashMap<>();
        List<String> contents = new ArrayList<>();
        List<DiaryAnalysis> analyses = new ArrayList<>();

        for (DiaryEntity diary : diaries) {
            contents.add(diary.getContent());
            DiaryAnalysisEntity analysisEntity = analysisMap.get(diary.getId());
            if (analysisEntity != null) {
                DiaryAnalysis analysis = new DiaryAnalysis(
                        analysisEntity.getMoodLabel(),
                        analysisEntity.getMoodIntensity(),
                        analysisEntity.getTopicLabelsJson(),
                        analysisEntity.getSummary(),
                        analysisEntity.getFeedback()
                );
                analyses.add(analysis);
                dailyMoods.add(new WeeklyReportView.DailyMood(
                        diary.getCreatedAt().toLocalDate(),
                        analysis.moodLabel(),
                        analysis.moodIntensity(),
                        List.of(diary.getId()),
                        snippet(diary.getContent())
                ));
                for (String topic : analysis.topicLabels()) {
                    topicCounts.merge(topic, 1, Integer::sum);
                }
            } else {
                analyses.add(null);
            }
        }

        var sortedTopics = topicCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("M/d");
        String weekLabel = monday.format(fmt) + " - " + sunday.format(fmt);

        rateLimitService.tryAcquire(userId, RateLimitService.AiApiType.REPORT);
        String aiSummary = aiAnalysisService.generateWeeklySummary(contents, analyses);

        return new WeeklyReportView(
                weekLabel,
                diaries.size(),
                dailyMoods,
                sortedTopics,
                aiSummary
        );
    }

    @Transactional
    public DiaryView addComment(long diaryId, CreateCommentRequest request) {
        DiaryEntity diary = findPublicDiary(diaryId);
        String content = normalizeContent(request.content());

        DiaryCommentEntity comment = new DiaryCommentEntity();
        UserEntity commenter = currentUser();
        comment.setDiaryId(diaryId);
        comment.setParentCommentId(request.parentCommentId());
        comment.setRootCommentId(resolveRootId(diaryId, request.parentCommentId()));
        comment.setAuthorUserId(commenter.getId());
        comment.setAuthorName(commenter.getDisplayName());
        comment.setContent(ContentFilter.filter(content));
        comment.setIsDeleted(false);
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        diaryCommentMapper.insert(comment);

        if (!commenter.getId().equals(diary.getAuthorUserId())) {
            String snippet = content.length() > 30 ? content.substring(0, 30) + "..." : content;
            notificationService.notifyComment(commenter, diaryId, diary.getAuthorUserId(),
                    comment.getId(), snippet);
        }

        DiaryAnalysisEntity analysis = findAnalysis(diaryId);
        List<DiaryCommentEntity> comments = findComments(diaryId);
        return DiaryView.fromPublic(diary, analysis, comments);
    }

    @Transactional
    public DiaryView resonate(long diaryId) {
        DiaryEntity diary = findPublicDiary(diaryId);
        UserEntity actor = currentUser();

        boolean exists = diaryResonanceMapper.exists(
                new LambdaQueryWrapper<DiaryResonanceEntity>()
                        .eq(DiaryResonanceEntity::getDiaryId, diaryId)
                        .eq(DiaryResonanceEntity::getUserId, actor.getId())
        );
        if (!exists) {
            DiaryResonanceEntity resonance = new DiaryResonanceEntity();
            resonance.setDiaryId(diaryId);
            resonance.setUserId(actor.getId());
            resonance.setCreatedAt(LocalDateTime.now());
            diaryResonanceMapper.insert(resonance);

            diary.setResonanceCount(diary.getResonanceCount() + 1);
            diary.setUpdatedAt(LocalDateTime.now());
            diaryMapper.updateById(diary);

            if (!actor.getId().equals(diary.getAuthorUserId())) {
                notificationService.notifyResonance(actor, diaryId, diary.getAuthorUserId());
            }
        }

        DiaryAnalysisEntity analysis = findAnalysis(diaryId);
        List<DiaryCommentEntity> comments = findComments(diaryId);
        return DiaryView.fromPublic(diary, analysis, comments);
    }

    private DiaryView toDiaryView(DiaryEntity diary) {
        return buildDiaryView(diary, true);
    }

    private DiaryView buildDiaryView(DiaryEntity diary, boolean isPublic) {
        DiaryAnalysisEntity analysis = diaryAnalysisMapper.selectById(diary.getId());
        List<DiaryCommentEntity> comments = diaryCommentMapper.selectList(
                new LambdaQueryWrapper<DiaryCommentEntity>()
                        .eq(DiaryCommentEntity::getDiaryId, diary.getId())
                        .orderByAsc(DiaryCommentEntity::getCreatedAt)
        );
        return buildDiaryView(diary, isPublic,
                analysis != null ? Map.of(analysis.getDiaryId(), analysis) : Map.of(),
                Map.of(diary.getId(), comments));
    }

    private DiaryView buildDiaryView(DiaryEntity diary, boolean isPublic,
            Map<Long, DiaryAnalysisEntity> analysisMap,
            Map<Long, List<DiaryCommentEntity>> commentMap) {
        DiaryAnalysisEntity analysis = analysisMap.get(diary.getId());
        List<DiaryCommentEntity> comments = commentMap.getOrDefault(diary.getId(), List.of());
        return isPublic ? DiaryView.fromPublic(diary, analysis, comments)
                        : DiaryView.from(diary, analysis, comments);
    }

    private Map<Long, DiaryAnalysisEntity> batchLoadAnalyses(List<Long> diaryIds) {
        if (diaryIds.isEmpty()) return Map.of();
        return diaryAnalysisMapper.selectBatchIds(diaryIds).stream()
                .collect(Collectors.toMap(DiaryAnalysisEntity::getDiaryId, a -> a));
    }

    private Map<Long, List<DiaryCommentEntity>> batchLoadComments(List<Long> diaryIds) {
        if (diaryIds.isEmpty()) return Map.of();
        List<DiaryCommentEntity> all = diaryCommentMapper.selectList(
                new LambdaQueryWrapper<DiaryCommentEntity>()
                        .in(DiaryCommentEntity::getDiaryId, diaryIds)
                        .orderByAsc(DiaryCommentEntity::getCreatedAt));
        return all.stream().collect(Collectors.groupingBy(DiaryCommentEntity::getDiaryId));
    }

    private DiaryEntity findDiary(long id) {
        DiaryEntity diary = diaryMapper.selectById(id);
        if (diary == null) {
            throw new ResponseStatusException(NOT_FOUND, "日记不存在");
        }
        return diary;
    }

    private DiaryEntity findPublicDiary(long id) {
        DiaryEntity diary = findDiary(id);
        if (!"PUBLIC".equals(diary.getVisibility())) {
            throw new ResponseStatusException(NOT_FOUND, "公开日记不存在");
        }
        return diary;
    }

    private DiaryAnalysisEntity findAnalysis(long diaryId) {
        return diaryAnalysisMapper.selectById(diaryId);
    }

    private List<DiaryCommentEntity> findComments(long diaryId) {
        return diaryCommentMapper.selectList(
                new LambdaQueryWrapper<DiaryCommentEntity>()
                        .eq(DiaryCommentEntity::getDiaryId, diaryId)
                        .orderByAsc(DiaryCommentEntity::getCreatedAt)
        );
    }

    // ── Current user ──

    @Transactional
    public void deleteDiary(long diaryId) {
        DiaryEntity diary = diaryMapper.selectById(diaryId);
        if (diary == null) throw new ResponseStatusException(NOT_FOUND, "日记不存在");
        if (!diary.getAuthorUserId().equals(currentUser().getId())) {
            throw new ResponseStatusException(FORBIDDEN, "只能删除自己的日记");
        }
        diaryMapper.deleteById(diaryId);
        evictUserCache();
    }

    @Transactional
    public void deleteComment(long diaryId, long commentId) {
        DiaryEntity diary = diaryMapper.selectById(diaryId);
        if (diary == null) throw new ResponseStatusException(NOT_FOUND, "日记不存在");
        DiaryCommentEntity comment = diaryCommentMapper.selectById(commentId);
        if (comment == null || !comment.getDiaryId().equals(diaryId)) {
            throw new ResponseStatusException(NOT_FOUND, "评论不存在");
        }
        if (!comment.getAuthorUserId().equals(currentUser().getId())) {
            throw new ResponseStatusException(FORBIDDEN, "只能删除自己的评论");
        }
        diaryCommentMapper.deleteById(commentId);
    }

    public static String snippet(String content) {
        if (content == null || content.isEmpty()) return "";
        return content.length() > 30 ? content.substring(0, 30) : content;
    }

    // ── Daily status ──

    public Map<String, Object> todayStatus() {
        UserEntity user = currentUser();
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();

        // 今天是否有日记
        boolean todayExists = diaryMapper.exists(
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getAuthorUserId, user.getId())
                        .ge(DiaryEntity::getCreatedAt, todayStart)
        );

        // 连续天数
        int streak = 0;
        LocalDate d = today;
        while (true) {
            boolean has = diaryMapper.exists(
                    new LambdaQueryWrapper<DiaryEntity>()
                            .eq(DiaryEntity::getAuthorUserId, user.getId())
                            .ge(DiaryEntity::getCreatedAt, d.atStartOfDay())
                            .lt(DiaryEntity::getCreatedAt, d.plusDays(1).atStartOfDay())
            );
            if (has) { streak++; d = d.minusDays(1); }
            else break;
        }

        // 昨天情绪
        String yesterdayMood = null;
        List<DiaryEntity> yesterdayDiaries = diaryMapper.selectList(
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getAuthorUserId, user.getId())
                        .ge(DiaryEntity::getCreatedAt, today.minusDays(1).atStartOfDay())
                        .lt(DiaryEntity::getCreatedAt, today.atStartOfDay())
                        .orderByDesc(DiaryEntity::getCreatedAt)
                        .last("LIMIT 1")
        );
        if (!yesterdayDiaries.isEmpty()) {
            DiaryAnalysisEntity analysis = findAnalysis(yesterdayDiaries.get(0).getId());
            if (analysis != null) yesterdayMood = analysis.getMoodLabel();
        }

        return Map.of(
                "todayHasDiary", todayExists,
                "streak", streak,
                "yesterdayMood", yesterdayMood != null ? yesterdayMood : ""
        );
    }

    // ── Today match ──

    public DiaryView todayMatch() {
        UserEntity user = currentUser();
        // 获取用户最近的情绪标签
        List<DiaryEntity> recent = diaryMapper.selectList(
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getAuthorUserId, user.getId())
                        .orderByDesc(DiaryEntity::getCreatedAt)
                        .last("LIMIT 3")
        );
        // 批量加载最近日记的分析
        List<Long> recentIds = recent.stream().map(DiaryEntity::getId).toList();
        Map<Long, DiaryAnalysisEntity> recentAnalysisMap = batchLoadAnalyses(recentIds);
        String targetMood = null;
        for (DiaryEntity d : recent) {
            DiaryAnalysisEntity a = recentAnalysisMap.get(d.getId());
            if (a != null) { targetMood = a.getMoodLabel(); break; }
        }
        if (targetMood == null) return null;

        // 找公开的同情绪日记
        List<DiaryEntity> matches = diaryMapper.selectList(
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getVisibility, "PUBLIC")
                        .ne(DiaryEntity::getAuthorUserId, user.getId())
                        .orderByDesc(DiaryEntity::getCreatedAt)
                        .last("LIMIT 50")
        );
        // 批量加载候选分析
        List<Long> candidateIds = matches.stream().map(DiaryEntity::getId).toList();
        Map<Long, DiaryAnalysisEntity> candidateAnalysisMap = batchLoadAnalyses(candidateIds);
        for (DiaryEntity d : matches) {
            DiaryAnalysisEntity a = candidateAnalysisMap.get(d.getId());
            if (a != null && targetMood.equals(a.getMoodLabel())) {
                return buildDiaryView(d, true, candidateAnalysisMap, Map.of());
            }
        }
        return null;
    }

    // ── Coaching ──

    public Map<String, Object> coachingPlan() {
        UserEntity user = currentUser();
        String cacheKey = "coaching:" + user.getId();

        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) return objectMapper.readValue(cached, Map.class);
        } catch (Exception e) { log.debug("Coaching cache miss"); }

        List<DiaryEntity> recent = diaryMapper.selectList(
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getAuthorUserId, user.getId())
                        .orderByDesc(DiaryEntity::getCreatedAt)
                        .last("LIMIT 7")
        );
        List<String> contents = new ArrayList<>();
        List<DiaryAnalysis> analyses = new ArrayList<>();
        List<Long> recentIds = recent.stream().map(DiaryEntity::getId).toList();
        Map<Long, DiaryAnalysisEntity> analysisMap = batchLoadAnalyses(recentIds);
        for (DiaryEntity d : recent) {
            contents.add(d.getContent());
            DiaryAnalysisEntity a = analysisMap.get(d.getId());
            if (a != null) analyses.add(new DiaryAnalysis(a.getMoodLabel(), a.getMoodIntensity(),
                    a.getTopicLabelsJson(), a.getSummary(), a.getFeedback()));
            else analyses.add(null);
        }
        rateLimitService.tryAcquire(user.getId(), RateLimitService.AiApiType.COACHING);
        String suggestion = aiAnalysisService.generateCoaching(contents, analyses);
        Map<String, Object> result = Map.of("suggestion", suggestion, "diaryCount", recent.size());

        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result), Duration.ofMinutes(15));
        } catch (Exception e) { log.debug("Coaching cache write failed"); }
        return result;
    }

    // ── Community mood ──

    public Map<String, Integer> communityMood() {
        List<DiaryEntity> todayPublic = diaryMapper.selectList(
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getVisibility, "PUBLIC")
                        .ge(DiaryEntity::getCreatedAt, LocalDate.now().atStartOfDay())
        );
        List<String> moods = new ArrayList<>();
        List<Long> diaryIds = todayPublic.stream().map(DiaryEntity::getId).toList();
        Map<Long, DiaryAnalysisEntity> analysisMap = batchLoadAnalyses(diaryIds);
        for (DiaryEntity d : todayPublic) {
            DiaryAnalysisEntity a = analysisMap.get(d.getId());
            if (a != null) moods.add(a.getMoodLabel());
        }
        return aiAnalysisService.communityMood(moods);
    }

    // ── Encouragement ──

    public List<String> generateEncouragements(long diaryId) {
        DiaryEntity diary = findPublicDiary(diaryId);
        rateLimitService.tryAcquire(currentUser().getId(), RateLimitService.AiApiType.ENCOURAGEMENT);
        return aiAnalysisService.generateEncouragements(diary.getContent());
    }

    @Transactional
    public DiaryView sendEncouragement(long diaryId, String message) {
        DiaryEntity diary = findPublicDiary(diaryId);
        UserEntity actor = currentUser();

        DiaryResonanceEntity r = new DiaryResonanceEntity();
        r.setDiaryId(diaryId);
        r.setUserId(actor.getId());
        r.setMessage(message != null && message.length() > 200
                ? message.substring(0, 200) : message);
        diaryResonanceMapper.insert(r);

        diary.setResonanceCount(diary.getResonanceCount() + 1);
        diaryMapper.updateById(diary);

        if (!diary.getAuthorUserId().equals(actor.getId())) {
            notificationService.notifyEncouragement(diaryId, diary.getAuthorUserId(), message);
        }

        return toDiaryView(diary);
    }

    private UserEntity currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserEntity user) {
            return user;
        }
        throw new ResponseStatusException(BAD_REQUEST, "用户未登录");
    }

    private Long resolveRootId(long diaryId, Long parentCommentId) {
        if (parentCommentId == null) return null;
        DiaryCommentEntity parent = diaryCommentMapper.selectById(parentCommentId);
        if (parent == null || !parent.getDiaryId().equals(diaryId)) return null;
        return parent.getRootCommentId() != null ? parent.getRootCommentId() : parent.getId();
    }

    // ── Validation ──

    private String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "请先写下今天的情绪");
        }
        String normalized = content.trim();
        if (normalized.length() > 1000) {
            throw new ResponseStatusException(BAD_REQUEST, "日记内容不能超过 1000 字");
        }
        return normalized;
    }

    private DiaryVisibility parseVisibility(String visibility) {
        if (visibility == null || visibility.isBlank()) {
            return DiaryVisibility.PRIVATE;
        }
        try {
            return DiaryVisibility.valueOf(visibility.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "日记权限只能是 PRIVATE 或 PUBLIC");
        }
    }

    private void evictUserCache() {
        Long userId = currentUser().getId();
        // 精确删除已知 key，避免 KEYS 扫描
        redisTemplate.delete("coaching:" + userId);
        // 报告缓存：删除近 4 周周报 + 近 3 个月月报
        for (int i = 0; i < 4; i++) {
            redisTemplate.delete("report:" + userId + ":" + i);
        }
        for (int i = 0; i < 3; i++) {
            redisTemplate.delete("report:monthly:" + userId + ":" + i);
        }
        // 公开流缓存：删除常见分页组合
        for (int page = 1; page <= 5; page++) {
            for (int size : new int[]{10, 20, 50}) {
                redisTemplate.delete("public:diaries:" + page + ":" + size);
            }
        }
        // 关注流缓存
        for (int page = 1; page <= 5; page++) {
            for (int size : new int[]{10, 20, 50}) {
                redisTemplate.delete("following:" + userId + ":" + page + ":" + size);
            }
        }
    }

    private int similarityScore(DiaryAnalysisEntity sourceAnalysis, DiaryAnalysisEntity targetAnalysis) {
        if (sourceAnalysis == null || targetAnalysis == null) return 0;
        int score = 0;
        if (sourceAnalysis.getMoodLabel().equals(targetAnalysis.getMoodLabel())) {
            score += 10;
        }
        for (String topic : sourceAnalysis.getTopicLabelsJson()) {
            if (targetAnalysis.getTopicLabelsJson().contains(topic)) {
                score += 3;
            }
        }
        return score;
    }
}
