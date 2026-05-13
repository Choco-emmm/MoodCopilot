package com.moodcopilot.diary;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.ai.AiAnalysisService;
import com.moodcopilot.ai.MemoryExtractionService;
import com.moodcopilot.common.ContentFilter;
import com.moodcopilot.entity.DiaryAnalysisEntity;
import com.moodcopilot.follow.FollowService;
import com.moodcopilot.entity.DiaryCommentEntity;
import com.moodcopilot.entity.DiaryEntity;
import com.moodcopilot.entity.DiaryHideEntity;
import com.moodcopilot.entity.DiaryRecommendationExposureEntity;
import com.moodcopilot.entity.DiaryResonanceEntity;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.mapper.DiaryAnalysisMapper;
import com.moodcopilot.mapper.DiaryCommentMapper;
import com.moodcopilot.mapper.DiaryHideMapper;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.mapper.DiaryRecommendationExposureMapper;
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
    private final DiaryHideMapper diaryHideMapper;
    private final DiaryRecommendationExposureMapper exposureMapper;
    private static final Logger log = LoggerFactory.getLogger(DiaryService.class);

    private final AiAnalysisService aiAnalysisService;
    private final MemoryExtractionService memoryExtractionService;
    private final NotificationService notificationService;
    private final FollowService followService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public DiaryService(DiaryMapper diaryMapper,
                        DiaryAnalysisMapper diaryAnalysisMapper,
                        DiaryCommentMapper diaryCommentMapper,
                        DiaryResonanceMapper diaryResonanceMapper,
                        DiaryHideMapper diaryHideMapper,
                        DiaryRecommendationExposureMapper exposureMapper,
                        AiAnalysisService aiAnalysisService,
                        MemoryExtractionService memoryExtractionService,
                        NotificationService notificationService,
                        FollowService followService,
                        StringRedisTemplate redisTemplate,
                        ObjectMapper objectMapper) {
        this.diaryMapper = diaryMapper;
        this.diaryAnalysisMapper = diaryAnalysisMapper;
        this.diaryCommentMapper = diaryCommentMapper;
        this.diaryResonanceMapper = diaryResonanceMapper;
        this.diaryHideMapper = diaryHideMapper;
        this.exposureMapper = exposureMapper;
        this.aiAnalysisService = aiAnalysisService;
        this.memoryExtractionService = memoryExtractionService;
        this.notificationService = notificationService;
        this.followService = followService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
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

        evictUserCache(user.getId());

        return DiaryView.from(diary, List.of());
    }

    @Async
    @Transactional
    public void runAiAnalysis(long diaryId, long userId, String content) {
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
        memoryExtractionService.extractAndSyncMemory(userId, content);
    }

    public Page<DiaryView> myDiaries(int page, int size) {
        int cappedPage = Math.max(1, page);
        int cappedSize = Math.min(50, Math.max(1, size));
        Page<DiaryEntity> entityPage = diaryMapper.selectPage(
                Page.of(cappedPage, cappedSize),
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getAuthorUserId, currentUser().getId())
                        .orderByDesc(DiaryEntity::getCreatedAt)
        );
        List<DiaryView> views = buildDiaryViews(entityPage.getRecords(), false);
        Page<DiaryView> viewPage = new Page<>(cappedPage, cappedSize, entityPage.getTotal());
        viewPage.setRecords(views);
        return viewPage;
    }

    public Page<DiaryView> publicDiaries(int page, int size) {
        int cappedPage = Math.max(1, page);
        int cappedSize = Math.min(50, Math.max(1, size));
        Long userId = currentUser().getId();
        String cacheKey = "public:diaries:%d:%d".formatted(cappedPage, cappedSize);

        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                Page<DiaryView> cachedPage = objectMapper.readValue(cached, new TypeReference<Page<DiaryView>>() {});
                return filterHiddenViews(cachedPage, userId);
            }
        } catch (Exception e) { log.debug("Cache miss {}", cacheKey); }

        Page<DiaryView> result = queryPublicDiaries(cappedPage, cappedSize);

        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result), Duration.ofMinutes(5));
        } catch (Exception e) { log.debug("Cache write failed"); }
        return filterHiddenViews(result, userId);
    }

    private Page<DiaryView> queryPublicDiaries(int page, int size) {
        Page<DiaryEntity> entityPage = diaryMapper.selectPage(
                Page.of(page, size),
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getVisibility, "PUBLIC")
                        .orderByDesc(DiaryEntity::getCreatedAt)
        );
        List<DiaryView> views = buildDiaryViews(entityPage.getRecords(), true);
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

        Page<DiaryEntity> candidatePage = diaryMapper.selectPage(
                Page.of(1, 200),
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getVisibility, "PUBLIC")
                        .ne(DiaryEntity::getId, id)
                        .orderByDesc(DiaryEntity::getCreatedAt)
        );
        UserEntity user = currentUser();
        List<DiaryEntity> publicDiaries = filterHidden(candidatePage.getRecords(), user.getId()).stream()
                .filter(diary -> !user.getId().equals(diary.getAuthorUserId()))
                .toList();
        Map<Long, DiaryAnalysisEntity> analysisMap = batchLoadAnalyses(
                publicDiaries.stream().map(DiaryEntity::getId).toList());

        int cappedLimit = Math.max(1, Math.min(limit, 10));

        List<DiaryEntity> recommended = dedupeByAuthor(publicDiaries.stream()
                .sorted(Comparator
                        .comparingInt((DiaryEntity d) -> similarityScore(sourceAnalysis, analysisMap.get(d.getId()))).reversed()
                        .thenComparing(DiaryEntity::getCreatedAt, Comparator.reverseOrder()))
                .toList()).stream()
                .limit(cappedLimit)
                .toList();
        recordExposures(user.getId(), "SIMILAR_DIARIES", recommended);
        return recommended.stream()
                .map(d -> buildDiaryView(d, true, analysisMap, Map.of()))
                .toList();
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
        List<DiaryEntity> visibleDiaries = filterHidden(entityPage.getRecords(), userId);
        List<DiaryView> views = buildDiaryViews(visibleDiaries, true);
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

        List<WeeklyReportView.DailyMood> dailyMoods = new ArrayList<>();
        Map<String, Integer> topicCounts = new LinkedHashMap<>();
        List<String> contents = new ArrayList<>();
        List<DiaryAnalysis> analyses = new ArrayList<>();
        Map<Long, DiaryAnalysisEntity> analysisMap = batchLoadAnalyses(
                diaries.stream().map(DiaryEntity::getId).toList());

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

        String aiSummary = aiAnalysisService.generateMonthlySummary(contents, analyses);
        AiAnalysisService.ReportGuidance guidance = aiAnalysisService.generateMonthlyGuidance(contents, analyses);

        return new WeeklyReportView(
                monthLabel,
                diaries.size(),
                dailyMoods,
                sortedTopics,
                aiSummary,
                guidance.insights(),
                guidance.suggestions(),
                guidance.followUpPrompt()
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

        List<WeeklyReportView.DailyMood> dailyMoods = new ArrayList<>();
        Map<String, Integer> topicCounts = new LinkedHashMap<>();
        List<String> contents = new ArrayList<>();
        List<DiaryAnalysis> analyses = new ArrayList<>();
        Map<Long, DiaryAnalysisEntity> analysisMap = batchLoadAnalyses(
                diaries.stream().map(DiaryEntity::getId).toList());

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

        String aiSummary = aiAnalysisService.generateWeeklySummary(contents, analyses);
        AiAnalysisService.ReportGuidance guidance = aiAnalysisService.generateWeeklyGuidance(contents, analyses);

        return new WeeklyReportView(
                weekLabel,
                diaries.size(),
                dailyMoods,
                sortedTopics,
                aiSummary,
                guidance.insights(),
                guidance.suggestions(),
                guidance.followUpPrompt()
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

    private DiaryView toOwnDiaryView(DiaryEntity diary) {
        return buildDiaryView(diary, false);
    }

    private List<DiaryView> buildDiaryViews(List<DiaryEntity> diaries, boolean isPublic) {
        List<Long> ids = diaries.stream().map(DiaryEntity::getId).toList();
        Map<Long, DiaryAnalysisEntity> analysisMap = batchLoadAnalyses(ids);
        Map<Long, List<DiaryCommentEntity>> commentMap = batchLoadComments(ids);
        return diaries.stream()
                .map(diary -> buildDiaryView(diary, isPublic, analysisMap, commentMap))
                .toList();
    }

    private DiaryView buildDiaryView(DiaryEntity diary, boolean isPublic) {
        DiaryAnalysisEntity analysis = findAnalysis(diary.getId());
        List<DiaryCommentEntity> comments = findComments(diary.getId());
        return buildDiaryView(diary, isPublic,
                analysis != null ? Map.of(diary.getId(), analysis) : Map.of(),
                Map.of(diary.getId(), comments));
    }

    private DiaryView buildDiaryView(DiaryEntity diary,
                                     boolean isPublic,
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
                .collect(Collectors.toMap(DiaryAnalysisEntity::getDiaryId, analysis -> analysis));
    }

    private Map<Long, List<DiaryCommentEntity>> batchLoadComments(List<Long> diaryIds) {
        if (diaryIds.isEmpty()) return Map.of();
        List<DiaryCommentEntity> comments = diaryCommentMapper.selectList(
                new LambdaQueryWrapper<DiaryCommentEntity>()
                        .in(DiaryCommentEntity::getDiaryId, diaryIds)
                        .orderByAsc(DiaryCommentEntity::getCreatedAt)
        );
        return comments.stream().collect(Collectors.groupingBy(DiaryCommentEntity::getDiaryId));
    }

    private List<DiaryEntity> filterHidden(List<DiaryEntity> diaries, long userId) {
        if (diaries.isEmpty()) return diaries;
        Set<Long> hiddenIds = hiddenDiaryIds(userId);
        if (hiddenIds.isEmpty()) return diaries;
        return diaries.stream().filter(diary -> !hiddenIds.contains(diary.getId())).toList();
    }

    private Page<DiaryView> filterHiddenViews(Page<DiaryView> page, long userId) {
        List<DiaryView> records = page.getRecords();
        if (records == null || records.isEmpty()) return page;
        Set<Long> hiddenIds = hiddenDiaryIds(userId);
        if (hiddenIds.isEmpty()) return page;
        Page<DiaryView> filtered = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        filtered.setRecords(records.stream()
                .filter(diary -> !hiddenIds.contains(diary.id()))
                .toList());
        return filtered;
    }

    private Set<Long> hiddenDiaryIds(long userId) {
        List<DiaryHideEntity> hides = diaryHideMapper.selectList(
                new LambdaQueryWrapper<DiaryHideEntity>()
                        .eq(DiaryHideEntity::getUserId, userId)
        );
        return hides.stream().map(DiaryHideEntity::getDiaryId).collect(Collectors.toSet());
    }

    private Set<Long> recentExposureIds(long userId, String scene) {
        List<DiaryRecommendationExposureEntity> exposures = exposureMapper.selectList(
                new LambdaQueryWrapper<DiaryRecommendationExposureEntity>()
                        .eq(DiaryRecommendationExposureEntity::getUserId, userId)
                        .eq(DiaryRecommendationExposureEntity::getScene, scene)
                        .ge(DiaryRecommendationExposureEntity::getCreatedAt, LocalDateTime.now().minusDays(7))
        );
        return exposures.stream()
                .map(DiaryRecommendationExposureEntity::getDiaryId)
                .collect(Collectors.toSet());
    }

    private List<DiaryEntity> dedupeByAuthor(List<DiaryEntity> diaries) {
        Set<Long> seenAuthors = new java.util.HashSet<>();
        return diaries.stream()
                .filter(diary -> seenAuthors.add(diary.getAuthorUserId()))
                .toList();
    }

    private void recordExposures(long userId, String scene, List<DiaryEntity> diaries) {
        for (DiaryEntity diary : diaries) {
            DiaryRecommendationExposureEntity exposure = new DiaryRecommendationExposureEntity();
            exposure.setUserId(userId);
            exposure.setDiaryId(diary.getId());
            exposure.setScene(scene);
            exposure.setCreatedAt(LocalDateTime.now());
            exposureMapper.insert(exposure);
        }
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
        evictUserCache(currentUser().getId());
    }

    @Transactional
    public void hideDiary(long diaryId) {
        DiaryEntity diary = findPublicDiary(diaryId);
        Long userId = currentUser().getId();
        boolean exists = diaryHideMapper.exists(
                new LambdaQueryWrapper<DiaryHideEntity>()
                        .eq(DiaryHideEntity::getUserId, userId)
                        .eq(DiaryHideEntity::getDiaryId, diary.getId())
        );
        if (!exists) {
            DiaryHideEntity hide = new DiaryHideEntity();
            hide.setUserId(userId);
            hide.setDiaryId(diary.getId());
            hide.setCreatedAt(LocalDateTime.now());
            diaryHideMapper.insert(hide);
        }
        evictUserCache(userId);
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
        String targetMood = null;
        Map<Long, DiaryAnalysisEntity> recentAnalysisMap = batchLoadAnalyses(
                recent.stream().map(DiaryEntity::getId).toList());
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
        matches = filterHidden(matches, user.getId());
        Map<Long, DiaryAnalysisEntity> matchAnalysisMap = batchLoadAnalyses(
                matches.stream().map(DiaryEntity::getId).toList());
        List<DiaryEntity> moodMatches = new ArrayList<>();
        for (DiaryEntity d : matches) {
            DiaryAnalysisEntity a = matchAnalysisMap.get(d.getId());
            if (a != null && targetMood.equals(a.getMoodLabel())) {
                moodMatches.add(d);
            }
        }
        Set<Long> recentExposureIds = recentExposureIds(user.getId(), "TODAY_MATCH");
        DiaryEntity selected = moodMatches.stream()
                .filter(diary -> !recentExposureIds.contains(diary.getId()))
                .findFirst()
                .orElseGet(() -> moodMatches.stream().findFirst().orElse(null));
        if (selected != null) {
            recordExposures(user.getId(), "TODAY_MATCH", List.of(selected));
            return buildDiaryView(selected, true, matchAnalysisMap, Map.of());
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
        Map<Long, DiaryAnalysisEntity> analysisMap = batchLoadAnalyses(
                recent.stream().map(DiaryEntity::getId).toList());
        for (DiaryEntity d : recent) {
            contents.add(d.getContent());
            DiaryAnalysisEntity a = analysisMap.get(d.getId());
            if (a != null) analyses.add(new DiaryAnalysis(a.getMoodLabel(), a.getMoodIntensity(),
                    a.getTopicLabelsJson(), a.getSummary(), a.getFeedback()));
            else analyses.add(null);
        }
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
        todayPublic = filterHidden(todayPublic, currentUser().getId());
        List<String> moods = new ArrayList<>();
        Map<Long, DiaryAnalysisEntity> analysisMap = batchLoadAnalyses(
                todayPublic.stream().map(DiaryEntity::getId).toList());
        for (DiaryEntity d : todayPublic) {
            DiaryAnalysisEntity a = analysisMap.get(d.getId());
            if (a != null) moods.add(a.getMoodLabel());
        }
        return aiAnalysisService.communityMood(moods);
    }

    // ── Encouragement ──

    public List<String> generateEncouragements(long diaryId) {
        DiaryEntity diary = findPublicDiary(diaryId);
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

    private void evictUserCache(long userId) {
        try {
            for (int offset = -4; offset <= 0; offset++) {
                redisTemplate.delete("report:%d:%d".formatted(userId, offset));
                redisTemplate.delete("report:monthly:%d:%d".formatted(userId, offset));
            }
            for (int page = 1; page <= 5; page++) {
                for (int size : List.of(10, 20, 50)) {
                    redisTemplate.delete("following:%d:%d:%d".formatted(userId, page, size));
                    redisTemplate.delete("public:diaries:%d:%d".formatted(page, size));
                }
            }
            redisTemplate.delete("coaching:" + userId);
        } catch (Exception e) {
            log.debug("Cache evict failed", e);
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
