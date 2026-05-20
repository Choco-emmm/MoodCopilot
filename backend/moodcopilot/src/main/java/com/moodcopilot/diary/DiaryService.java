package com.moodcopilot.diary;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.ai.*;
import com.moodcopilot.oss.*;
import com.moodcopilot.common.ContentFilter;
import com.moodcopilot.growth.ExpAction;
import com.moodcopilot.growth.UserGrowthService;
import com.moodcopilot.security.RateLimitService;
import com.moodcopilot.entity.DiaryAnalysisEntity;
import com.moodcopilot.follow.FollowService;
import com.moodcopilot.entity.DiaryCommentEntity;
import com.moodcopilot.entity.DiaryEntity;
import com.moodcopilot.entity.DiaryHideEntity;
import com.moodcopilot.entity.MusicMeta;
import com.moodcopilot.entity.DiaryRecommendationExposureEntity;
import com.moodcopilot.entity.DiaryResonanceEntity;
import com.moodcopilot.entity.DiarySummaryEntity;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.entity.UserProfileMemoryEntity;
import com.moodcopilot.mapper.DiaryAnalysisMapper;
import com.moodcopilot.mapper.DiaryCommentMapper;
import com.moodcopilot.mapper.DiaryHideMapper;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.mapper.DiaryRecommendationExposureMapper;
import com.moodcopilot.mapper.DiaryResonanceMapper;
import com.moodcopilot.mapper.DiarySummaryMapper;
import com.moodcopilot.mapper.UserMapper;
import com.moodcopilot.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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

    private static final String Q_POS_HIGH = "正向高能量";
    private static final String Q_POS_LOW = "正向低能量";
    private static final String Q_NEG_HIGH = "负向高能量";
    private static final String Q_NEG_LOW = "负向低能量";

    private final DiaryMapper diaryMapper;
    private final DiaryAnalysisMapper diaryAnalysisMapper;
    private final DiaryCommentMapper diaryCommentMapper;
    private final DiaryResonanceMapper diaryResonanceMapper;
    private final DiaryHideMapper diaryHideMapper;
    private final DiaryRecommendationExposureMapper exposureMapper;
    private final UserMapper userMapper;
    private static final Logger log = LoggerFactory.getLogger(DiaryService.class);

    private final AiAnalysisService aiAnalysisService;
    private final VisionService visionService;
    private final OssService ossService;
    private final MemoryExtractionService memoryExtractionService;
    private final NotificationService notificationService;
    private final FollowService followService;
    private final DiarySummaryMapper diarySummaryMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final RagMemoryService ragMemoryService;
    private final RateLimitService rateLimitService;
    private final UserGrowthService userGrowthService;
    private final TransactionTemplate transactionTemplate;

    public DiaryService(DiaryMapper diaryMapper,
            DiaryAnalysisMapper diaryAnalysisMapper,
            DiaryCommentMapper diaryCommentMapper,
            DiaryResonanceMapper diaryResonanceMapper,
            DiaryHideMapper diaryHideMapper,
            DiaryRecommendationExposureMapper exposureMapper,
            UserMapper userMapper,
            AiAnalysisService aiAnalysisService,
            VisionService visionService,
            OssService ossService,
            MemoryExtractionService memoryExtractionService,
            NotificationService notificationService,
            FollowService followService,
            DiarySummaryMapper diarySummaryMapper,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher,
            RagMemoryService ragMemoryService,
            RateLimitService rateLimitService,
            UserGrowthService userGrowthService,
            TransactionTemplate transactionTemplate) {
        this.diaryMapper = diaryMapper;
        this.diaryAnalysisMapper = diaryAnalysisMapper;
        this.diaryCommentMapper = diaryCommentMapper;
        this.diaryResonanceMapper = diaryResonanceMapper;
        this.diaryHideMapper = diaryHideMapper;
        this.exposureMapper = exposureMapper;
        this.userMapper = userMapper;
        this.aiAnalysisService = aiAnalysisService;
        this.visionService = visionService;
        this.ossService = ossService;
        this.memoryExtractionService = memoryExtractionService;
        this.notificationService = notificationService;
        this.followService = followService;
        this.diarySummaryMapper = diarySummaryMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.ragMemoryService = ragMemoryService;
        this.rateLimitService = rateLimitService;
        this.userGrowthService = userGrowthService;
        this.transactionTemplate = transactionTemplate;
    }

    @jakarta.annotation.PostConstruct
    private void migrateResonanceToRedis() {
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey("resonance:migrated"))) {
                log.info("点赞数据已迁移到 Redis，跳过");
                return;
            }
            List<DiaryResonanceEntity> all = diaryResonanceMapper.selectList(null);
            log.info("开始迁移点赞数据到 Redis，共 {} 条", all.size());
            for (DiaryResonanceEntity r : all) {
                redisTemplate.opsForSet().add("resonance:" + r.getDiaryId(), String.valueOf(r.getUserId()));
            }
            redisTemplate.opsForValue().set("resonance:migrated", "1");
            log.info("点赞数据迁移完成");
        } catch (Exception e) {
            log.warn("点赞数据迁移失败，将在下次启动重试: {}", e.getMessage());
        }
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
        diary.setMusicMeta(request.musicMeta());
        diary.setImages(request.images());
        diary.setResonanceCount(0);
        diary.setIsDeleted(false);
        diary.setCreatedAt(LocalDateTime.now());
        diary.setUpdatedAt(LocalDateTime.now());
        diaryMapper.insert(diary);

        if (diary.getContent() != null && diary.getContent().length() >= 15) {
            userGrowthService.addExp(user.getId(), ExpAction.DIARY, diary.getContent().length());
        }

        markReportsStale(user.getId());
        if ("PUBLIC".equals(diary.getVisibility())) {
            evictPublicDiaryCaches();
        }
        ragMemoryService.indexDiary(user.getId(), diary.getId(),
                buildIndexContent(diary.getContent(), diary.getMusicMeta()), diary.getMusicMeta());

        return DiaryView.from(diary, List.of(), normalizeAvatar(user.getAvatar()), user.getDisplayName(), Map.of(),
                false);
    }

    public DiaryView updateDiary(long diaryId, UpdateDiaryRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "更新参数不能为空");
        }

        UserEntity user = currentUser();
        DiaryEntity diary = findDiary(diaryId);
        if (!diary.getAuthorUserId().equals(user.getId())) {
            throw new ResponseStatusException(FORBIDDEN, "只能编辑自己的日记");
        }

        String normalizedContent = normalizeContent(request.content());
        DiaryVisibility visibility = parseVisibility(request.visibility());

        if (request.isPinned() != null) {
            if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
                throw new ResponseStatusException(FORBIDDEN, "只有管理员才能置顶日记");
            }
            diary.setIsPinned(request.isPinned());
        }

        String oldContent = diary.getContent() == null ? "" : diary.getContent();
        String oldVisibility = diary.getVisibility();
        String filteredContent = ContentFilter.filter(normalizedContent);
        boolean contentChanged = !oldContent.equals(filteredContent);
        boolean visibilityChanged = !visibility.name().equals(oldVisibility);

        // DB 写入放在编程式事务内，确保原子性且不扩散到 LLM 调用
        transactionTemplate.executeWithoutResult(status -> {
            diary.setContent(filteredContent);
            diary.setVisibility(visibility.name());
            diary.setUpdatedAt(LocalDateTime.now());
            if (request.musicMeta() != null) {
                diary.setMusicMeta(request.musicMeta());
            }
            if (request.images() != null) {
                diary.setImages(request.images());
            }
            diaryMapper.updateById(diary);
        });

        // AI 分析：在事务外执行，避免 HikariCP 连接泄漏
        if (contentChanged) {
            log.info("日记内容已更新，触发分析与画像重建，diaryId={}，userId={}", diaryId, user.getId());
            rateLimitService.tryAcquire(user, RateLimitService.AiApiType.ANALYSIS);
            ragMemoryService.indexDiary(user.getId(), diaryId,
                    buildIndexContent(filteredContent, diary.getMusicMeta()), diary.getMusicMeta());

            String imageDescriptions = visionService.describeImages(diary.getImages());
//            log.info("图片描述：{}", imageDescriptions);
            DiaryAnalysis analysis = aiAnalysisService.analyze(filteredContent, diary.getMusicMeta(), imageDescriptions);

            // 分析结果持久化单独一个事务
            LocalDateTime now = LocalDateTime.now();
            transactionTemplate.executeWithoutResult(status -> {
                DiaryAnalysisEntity existingAnalysis = diaryAnalysisMapper.selectById(diaryId);
                if (existingAnalysis == null) {
                    DiaryAnalysisEntity analysisEntity = new DiaryAnalysisEntity();
                    analysisEntity.setDiaryId(diaryId);
                    analysisEntity.setMoodLabel(analysis.moodLabel());
                    analysisEntity.setMoodIntensity(analysis.moodIntensity());
                    analysisEntity.setTopicLabelsJson(analysis.topicLabels());
                    analysisEntity.setSummary(analysis.summary());
                    analysisEntity.setFeedback(analysis.feedback());
                    analysisEntity.setCreatedAt(now);
                    analysisEntity.setUpdatedAt(now);
                    diaryAnalysisMapper.insert(analysisEntity);
                } else {
                    existingAnalysis.setMoodLabel(analysis.moodLabel());
                    existingAnalysis.setMoodIntensity(analysis.moodIntensity());
                    existingAnalysis.setTopicLabelsJson(analysis.topicLabels());
                    existingAnalysis.setSummary(analysis.summary());
                    existingAnalysis.setFeedback(analysis.feedback());
                    existingAnalysis.setUpdatedAt(now);
                    diaryAnalysisMapper.updateById(existingAnalysis);
                }
            });

            memoryExtractionService.extractAndSyncMemory(user.getId(), filteredContent, diary.getMusicMeta());
        }

        boolean affectsPublicCache = "PUBLIC".equals(visibility.name()) || "PUBLIC".equals(oldVisibility);
        if (visibilityChanged || contentChanged) {
            markReportsStale(user.getId());
            if (affectsPublicCache) {
                evictPublicDiaryCaches();
            }
        }
        evictUserCache(user.getId());

        log.info("日记更新完成，diaryId={}，userId={}，contentChanged={}，visibilityChanged={}，visibility={}",
                diaryId, user.getId(), contentChanged, visibilityChanged, visibility.name());

        return buildDiaryView(diary, "PUBLIC".equals(diary.getVisibility()));
    }

    @Async("aiExecutor")
    public void runAiAnalysis(long diaryId, long userId, String content, MusicMeta musicMeta, java.util.List<String> images, UserEntity user) {
        log.info("开始执行日记 AI 分析，diaryId={}，userId={}，contentLength={}，hasMusic={}，hasImages={}", diaryId, userId,
                content == null ? 0 : content.length(), musicMeta != null, images != null && !images.isEmpty());
        try {
            String imageDescriptions = visionService.describeImages(images);
            DiaryAnalysis analysis = aiAnalysisService.analyze(content, musicMeta, imageDescriptions);

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
            log.info("日记 AI 分析已落库，diaryId={}，mood={}，topics={}", diaryId, analysis.moodLabel(), analysis.topicLabels());

            eventPublisher.publishEvent(new DiaryAnalysisCompletedEvent(
                    this, diaryId, userId, analysis.moodLabel(), analysis.moodIntensity(), analysis.topicLabels()));

            memoryExtractionService.extractAndSyncMemory(userId, content, musicMeta, imageDescriptions);
            // VLM 描述拿到后重新索引，让图片信息可被 RAG 检索
            if (imageDescriptions != null && !imageDescriptions.isBlank()) {
                String enriched = buildIndexContent(content, musicMeta) + "\n[图片描述] " + imageDescriptions;
                ragMemoryService.indexDiary(userId, diaryId, enriched, musicMeta);
                ragMemoryService.indexDiaryImages(userId, diaryId, imageDescriptions);
                log.info("RAG 已用图片描述重新索引 diaryId={}（含独立图片条目）", diaryId);
            }
            markReportsStale(userId);
            DiaryEntity diary = diaryMapper.selectById(diaryId);
            if (diary != null && "PUBLIC".equals(diary.getVisibility())) {
                evictPublicDiaryCaches();
            }
            log.info("日记分析后续任务已触发，diaryId={}，userId={}，动作=publishEvent+extractMemory+markReportsStale", diaryId, userId);
        } catch (Exception e) {
            log.error("日记 AI 分析异步任务失败，diaryId={}，userId={}，错误信息={}", diaryId, userId, e.getMessage(), e);
        }
    }

    public Page<DiaryView> myDiaries(int page, int size) {
        int cappedPage = Math.max(1, page);
        int cappedSize = Math.min(50, Math.max(1, size));
        Page<DiaryEntity> entityPage = diaryMapper.selectPage(
                Page.of(cappedPage, cappedSize),
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getAuthorUserId, currentUser().getId())
                        .orderByDesc(DiaryEntity::getCreatedAt));
        List<DiaryView> views = buildDiaryViews(entityPage.getRecords(), false);
        Page<DiaryView> viewPage = new Page<>(cappedPage, cappedSize, entityPage.getTotal());
        viewPage.setRecords(views);
        return viewPage;
    }

    public Page<DiaryView> userDiaries(long targetUserId, int page, int size) {
        int cappedPage = Math.max(1, page);
        int cappedSize = Math.min(50, Math.max(1, size));
        Long viewerId = currentUser().getId();
        boolean isOwner = viewerId.equals(targetUserId);

        LambdaQueryWrapper<DiaryEntity> query = new LambdaQueryWrapper<DiaryEntity>()
                .eq(DiaryEntity::getAuthorUserId, targetUserId)
                .orderByDesc(DiaryEntity::getCreatedAt);
        if (!isOwner) {
            query.eq(DiaryEntity::getVisibility, "PUBLIC");
        }

        Page<DiaryEntity> entityPage = diaryMapper.selectPage(Page.of(cappedPage, cappedSize), query);
        List<DiaryView> views = buildDiaryViews(entityPage.getRecords(), !isOwner);
        Page<DiaryView> viewPage = new Page<>(cappedPage, cappedSize, entityPage.getTotal());
        viewPage.setRecords(views);
        return viewPage;
    }

    public DiarySearchResult searchOwnDiarySummaries(DiarySearchRequest request) {
        UserEntity user = currentUser();
        String keyword = request != null && request.keyword() != null ? request.keyword().trim() : null;
        keyword = keyword != null && !keyword.isBlank() ? keyword : null;
        LocalDate startDate = request != null ? request.startDate() : null;
        LocalDate endDate = request != null ? request.endDate() : null;
        log.info("执行历史日记检索，userId={}，keyword={}，startDate={}，endDate={}", user.getId(), keyword, startDate, endDate);

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            log.info("历史日记检索参数非法，userId={}，startDate={}，endDate={}", user.getId(), startDate, endDate);
            return new DiarySearchResult(
                    keyword,
                    startDate,
                    endDate,
                    0,
                    List.of(),
                    "起始日期不能晚于结束日期");
        }

        LambdaQueryWrapper<DiaryEntity> query = new LambdaQueryWrapper<DiaryEntity>()
                .eq(DiaryEntity::getAuthorUserId, user.getId())
                .orderByDesc(DiaryEntity::getCreatedAt)
                .last("LIMIT 20");

        if (keyword != null) {
            query.like(DiaryEntity::getContent, keyword);
        }
        if (startDate != null) {
            query.ge(DiaryEntity::getCreatedAt, startDate.atStartOfDay());
        }
        if (endDate != null) {
            query.le(DiaryEntity::getCreatedAt, endDate.atTime(LocalTime.MAX));
        }

        List<DiarySearchResult.DiarySummary> diaries = diaryMapper.selectList(query).stream()
                .map(diary -> new DiarySearchResult.DiarySummary(
                        diary.getCreatedAt().toLocalDate(),
                        snippet(diary.getContent())))
                .toList();

        String note = diaries.isEmpty()
                ? "未找到符合条件的历史日记"
                : "已返回最多 20 条按时间倒序排列的历史日记摘要";

        log.info("历史日记检索完成，userId={}，resultCount={}", user.getId(), diaries.size());

        return new DiarySearchResult(keyword, startDate, endDate, diaries.size(), diaries, note);
    }

    public UserStatsResult getOwnMoodStats(UserStatsRequest request) {
        UserEntity user = currentUser();
        int days = request != null && request.days() != null ? request.days() : 14;
        int clampedDays = Math.min(60, Math.max(7, days));
        LocalDateTime startTime = LocalDate.now().minusDays(clampedDays - 1L).atStartOfDay();

        List<DiaryEntity> diaries = diaryMapper.selectList(
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getAuthorUserId, user.getId())
                        .eq(DiaryEntity::getIsDeleted, false)
                        .ge(DiaryEntity::getCreatedAt, startTime)
                        .orderByDesc(DiaryEntity::getCreatedAt)
                        .last("LIMIT 120"));

        if (diaries.isEmpty()) {
            return new UserStatsResult(clampedDays, 0, Map.of(), Map.of(), "最近时段暂无可统计的日记记录");
        }

        List<Long> diaryIds = diaries.stream().map(DiaryEntity::getId).toList();
        Map<Long, DiaryAnalysisEntity> analysisMap = diaryAnalysisMapper.selectBatchIds(diaryIds)
                .stream()
                .collect(Collectors.toMap(DiaryAnalysisEntity::getDiaryId, analysis -> analysis));

        Map<String, Long> moodCounts = new LinkedHashMap<>();
        Map<String, Long> topicCounter = new HashMap<>();

        for (DiaryEntity diary : diaries) {
            DiaryAnalysisEntity analysis = analysisMap.get(diary.getId());
            if (analysis == null) {
                continue;
            }
            String mood = analysis.getMoodLabel();
            if (mood != null && !mood.isBlank()) {
                moodCounts.put(mood, moodCounts.getOrDefault(mood, 0L) + 1L);
            }
            List<String> topics = analysis.getTopicLabelsJson();
            if (topics != null) {
                for (String topic : topics) {
                    if (topic == null || topic.isBlank()) {
                        continue;
                    }
                    topicCounter.put(topic, topicCounter.getOrDefault(topic, 0L) + 1L);
                }
            }
        }

        Map<String, Long> topTopics = topicCounter.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(5)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new));

        String note = "统计窗口 " + clampedDays + " 天，含分析结果日记 "
                + moodCounts.values().stream().mapToLong(Long::longValue).sum() + " 篇";
        log.info("执行用户情绪统计，userId={}，days={}，diaryCount={}，moodTypes={}，topTopicCount={}",
                user.getId(), clampedDays, diaries.size(), moodCounts.size(), topTopics.size());
        return new UserStatsResult(clampedDays, diaries.size(), moodCounts, topTopics, note);
    }

    public ReportSnapshotResult getOwnReportSnapshot(ReportSnapshotRequest request) {
        String period = request != null && request.period() != null ? request.period().trim().toLowerCase(Locale.ROOT)
                : "week";
        int offset = request != null && request.offset() != null ? request.offset() : 0;

        WeeklyReportView report = "month".equals(period)
                ? monthlyReport(offset)
                : weeklyReport(offset);

        String normalizedPeriod = "month".equals(period) ? "month" : "week";
        String note = report.diaryCount() == 0
                ? "该周期暂无日记记录"
                : "已返回该周期报告关键指标";

        return new ReportSnapshotResult(
                normalizedPeriod,
                offset,
                report.weekLabel(),
                report.diaryCount(),
                report.moodDominantQuadrant(),
                report.positiveRatioPercent(),
                report.highEnergyRatioPercent(),
                report.generatedAt() != null ? report.generatedAt().toString() : null,
                note);
    }

    public Page<DiaryView> publicDiaries(int page, int size) {
        int cappedPage = Math.max(1, page);
        int cappedSize = Math.min(50, Math.max(1, size));
        Long userId = currentUser().getId();
        String cacheKey = "public:diaries:%d:%d".formatted(cappedPage, cappedSize);

        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                Page<DiaryView> cachedPage = objectMapper.readValue(cached, new TypeReference<Page<DiaryView>>() {
                });
                return filterHiddenViews(cachedPage, userId);
            }
        } catch (Exception e) {
            log.debug("Cache miss {}", cacheKey);
        }

        Page<DiaryView> result = queryPublicDiaries(cappedPage, cappedSize);

        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result), Duration.ofMinutes(5));
        } catch (Exception e) {
            log.debug("Cache write failed");
        }
        return filterHiddenViews(result, userId);
    }

    private Page<DiaryView> queryPublicDiaries(int page, int size) {
        Page<DiaryEntity> entityPage = diaryMapper.selectPage(
                Page.of(page, size),
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getVisibility, "PUBLIC")
                        .orderByDesc(DiaryEntity::getIsPinned)
                        .orderByDesc(DiaryEntity::getCreatedAt));
        List<DiaryView> views = buildDiaryViews(entityPage.getRecords(), true);
        Page<DiaryView> viewPage = new Page<>(page, size, entityPage.getTotal());
        viewPage.setRecords(views);
        return viewPage;
    }

    public DiaryView get(long id) {
        DiaryEntity diary = findDiary(id);
        Long currentUserId = currentUser().getId();
        boolean isOwner = diary.getAuthorUserId().equals(currentUserId);
        if (!isOwner && !"PUBLIC".equals(diary.getVisibility())) {
            throw new ResponseStatusException(NOT_FOUND, "公开日记不存在");
        }
        return buildDiaryView(diary, !isOwner);
    }

    public List<DiaryView> similar(long id, int limit) {
        DiaryEntity source = findDiary(id);
        DiaryAnalysisEntity sourceAnalysis = findAnalysis(id);

        Page<DiaryEntity> candidatePage = diaryMapper.selectPage(
                Page.of(1, 200),
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getVisibility, "PUBLIC")
                        .ne(DiaryEntity::getId, id)
                        .orderByDesc(DiaryEntity::getCreatedAt));
        UserEntity user = currentUser();
        List<DiaryEntity> publicDiaries = filterHidden(candidatePage.getRecords(), user.getId()).stream()
                .filter(diary -> !user.getId().equals(diary.getAuthorUserId()))
                .toList();
        Map<Long, DiaryAnalysisEntity> analysisMap = batchLoadAnalyses(
                publicDiaries.stream().map(DiaryEntity::getId).toList());

        int cappedLimit = Math.max(1, Math.min(limit, 10));

        List<DiaryEntity> recommended = dedupeByAuthor(publicDiaries.stream()
                .sorted(Comparator
                        .comparingInt((DiaryEntity d) -> similarityScore(sourceAnalysis, analysisMap.get(d.getId())))
                        .reversed()
                        .thenComparing(DiaryEntity::getCreatedAt, Comparator.reverseOrder()))
                .toList()).stream()
                .limit(cappedLimit)
                .toList();
        recordExposures(user.getId(), "SIMILAR_DIARIES", recommended);
        return recommended.stream()
                .map(d -> buildDiaryView(d, true, analysisMap, java.util.Collections.emptyMap(), false))
                .toList();
    }

    public Page<DiaryView> followingDiaries(int page, int size) {
        Long userId = currentUser().getId();
        int cappedPage = Math.max(1, page);
        int cappedSize = Math.min(50, Math.max(1, size));
        String cacheKey = "following:%d:%d:%d".formatted(userId, cappedPage, cappedSize);

        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null)
                return objectMapper.readValue(cached, new TypeReference<Page<DiaryView>>() {
                });
        } catch (Exception e) {
            log.debug("Cache miss {}", cacheKey);
        }

        Page<DiaryView> result = queryFollowingDiaries(userId, cappedPage, cappedSize);

        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result), Duration.ofMinutes(5));
        } catch (Exception e) {
            log.debug("Cache write failed");
        }
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
                        .orderByDesc(DiaryEntity::getCreatedAt));
        List<DiaryEntity> visibleDiaries = filterHidden(entityPage.getRecords(), userId);
        List<DiaryView> views = buildDiaryViews(visibleDiaries, true);
        Page<DiaryView> viewPage = new Page<>(page, size, entityPage.getTotal());
        viewPage.setRecords(views);
        return viewPage;
    }

    // ── Monthly report ──

    public WeeklyReportView monthlyReport(int monthOffset) {
        Long userId = currentUser().getId();
        return loadOrComputeMonthlyReport(monthOffset, userId, false);
    }

    public WeeklyReportView generateMonthlyAiSummary(int monthOffset) {
        UserEntity user = currentUser();
        log.info("强制生成月报摘要，userId={}，monthOffset={}", user.getId(), monthOffset);
        rateLimitService.tryAcquire(user, RateLimitService.AiApiType.REPORT);
        return loadOrComputeMonthlyReport(monthOffset, user.getId(), true);
    }

    public WeeklyReportView generateMonthlyAiSummaryForUser(long userId, int monthOffset) {
        return loadOrComputeMonthlyReport(monthOffset, userId, true);
    }

    public WeeklyReportView loadMonthlyReportForUser(long userId, int monthOffset) {
        return loadOrComputeMonthlyReport(monthOffset, userId, false);
    }

    private WeeklyReportView loadOrComputeMonthlyReport(int monthOffset, long userId, boolean forceGenerate) {
        String cacheKey = "report:monthly:%d:%d".formatted(userId, monthOffset);

        if (!forceGenerate) {
            try {
                String cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    WeeklyReportView cachedReport = objectMapper.readValue(cached, WeeklyReportView.class);
                    return withFreshness(cachedReport, userId, monthOffset, true);
                }
            } catch (Exception e) {
                log.debug("Cache read failed for {}", cacheKey, e);
            }

            WeeklyReportView dbReport = loadReportFromDb(userId, monthOffset, true);
            if (dbReport != null) {
                dbReport = withFreshness(dbReport, userId, monthOffset, true);
                try {
                    redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(dbReport),
                            Duration.ofDays(30));
                } catch (Exception e) {
                    log.debug("Cache write failed for {}", cacheKey, e);
                }
                return dbReport;
            }
        }

        WeeklyReportView report = computeMonthlyReport(monthOffset, userId, forceGenerate);
        report = withFreshness(report, userId, monthOffset, true);

        if (forceGenerate && report.aiSummary() != null) {
            saveReportToDb(userId, report, monthOffset, true);
        }

        try {
            Duration ttl = forceGenerate && report.aiSummary() != null ? Duration.ofDays(30) : Duration.ofMinutes(2);
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(report), ttl);
        } catch (Exception e) {
            log.debug("Cache write failed for {}", cacheKey, e);
        }
        return report;
    }

    private WeeklyReportView computeMonthlyReport(int monthOffset, long userId, boolean forceGenerate) {
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
                        .orderByAsc(DiaryEntity::getCreatedAt));

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
                        analysisEntity.getSecondaryMoodsJson() != null ? analysisEntity.getSecondaryMoodsJson()
                                : List.of(),
                        analysisEntity.getSummary(),
                        analysisEntity.getFeedback());
                analyses.add(analysis);
                dailyMoods.add(new WeeklyReportView.DailyMood(
                        diary.getCreatedAt().toLocalDate(),
                        analysis.moodLabel(),
                        analysis.moodIntensity(),
                        List.of(diary.getId()),
                        snippet(diary.getContent())));
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

        String aiSummary = null;
        List<String> insights = List.of();
        List<String> suggestions = List.of();
        String followUpPrompt = null;

        if (forceGenerate && !contents.isEmpty()) {
            String memCtx = buildMemoryContext(userId);
            aiSummary = aiAnalysisService.generateMonthlySummary(contents, analyses, memCtx);
            AiAnalysisService.ReportGuidance guidance = aiAnalysisService.generateMonthlyGuidance(contents, analyses);
            insights = guidance.insights();
            suggestions = guidance.suggestions();
            followUpPrompt = guidance.followUpPrompt();
        }

        Map<String, Integer> moodDistribution = buildMoodDistribution(dailyMoods);
        String dominantQuadrant = dominantQuadrant(moodDistribution);
        int positiveRatioPercent = calculatePositiveRatioPercent(moodDistribution);
        int highEnergyRatioPercent = calculateHighEnergyRatioPercent(moodDistribution);

        return new WeeklyReportView(
                monthLabel,
                diaries.size(),
                dailyMoods,
                sortedTopics,
                moodDistribution,
                dominantQuadrant,
                positiveRatioPercent,
                highEnergyRatioPercent,
                aiSummary,
                insights,
                suggestions,
                followUpPrompt,
                LocalDateTime.now(),
                false);
    }

    // ── Weekly report ──

    public WeeklyReportView weeklyReport(int weekOffset) {
        Long userId = currentUser().getId();
        return loadOrComputeWeeklyReport(weekOffset, userId, false);
    }

    public WeeklyReportView generateWeeklyAiSummary(int weekOffset) {
        UserEntity user = currentUser();
        log.info("强制生成周报摘要，userId={}，weekOffset={}", user.getId(), weekOffset);
        rateLimitService.tryAcquire(user, RateLimitService.AiApiType.REPORT);
        return loadOrComputeWeeklyReport(weekOffset, user.getId(), true);
    }

    public WeeklyReportView generateWeeklyAiSummaryForUser(long userId, int weekOffset) {
        return loadOrComputeWeeklyReport(weekOffset, userId, true);
    }

    public WeeklyReportView loadWeeklyReportForUser(long userId, int weekOffset) {
        return loadOrComputeWeeklyReport(weekOffset, userId, false);
    }

    private WeeklyReportView loadOrComputeWeeklyReport(int weekOffset, long userId, boolean forceGenerate) {
        String cacheKey = "report:%d:%d".formatted(userId, weekOffset);

        if (!forceGenerate) {
            try {
                String cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    WeeklyReportView cachedReport = objectMapper.readValue(cached, WeeklyReportView.class);
                    return withFreshness(cachedReport, userId, weekOffset, false);
                }
            } catch (Exception e) {
                log.debug("Cache read failed for {}", cacheKey, e);
            }

            WeeklyReportView dbReport = loadReportFromDb(userId, weekOffset, false);
            if (dbReport != null) {
                dbReport = withFreshness(dbReport, userId, weekOffset, false);
                try {
                    redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(dbReport),
                            Duration.ofDays(7));
                } catch (Exception e) {
                    log.debug("Cache write failed for {}", cacheKey, e);
                }
                return dbReport;
            }
        }

        WeeklyReportView report = computeWeeklyReport(weekOffset, userId, forceGenerate);
        report = withFreshness(report, userId, weekOffset, false);

        if (forceGenerate && report.aiSummary() != null) {
            saveReportToDb(userId, report, weekOffset, false);
        }

        try {
            Duration ttl = forceGenerate && report.aiSummary() != null ? Duration.ofDays(7) : Duration.ofMinutes(2);
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(report), ttl);
        } catch (Exception e) {
            log.debug("Cache write failed for {}", cacheKey, e);
        }
        return report;
    }

    private WeeklyReportView computeWeeklyReport(int weekOffset, long userId, boolean forceGenerate) {
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
                        .orderByAsc(DiaryEntity::getCreatedAt));

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
                        analysisEntity.getSecondaryMoodsJson() != null ? analysisEntity.getSecondaryMoodsJson()
                                : List.of(),
                        analysisEntity.getSummary(),
                        analysisEntity.getFeedback());
                analyses.add(analysis);
                dailyMoods.add(new WeeklyReportView.DailyMood(
                        diary.getCreatedAt().toLocalDate(),
                        analysis.moodLabel(),
                        analysis.moodIntensity(),
                        List.of(diary.getId()),
                        snippet(diary.getContent())));
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

        String aiSummary = null;
        List<String> insights = List.of();
        List<String> suggestions = List.of();
        String followUpPrompt = null;

        if (forceGenerate && !contents.isEmpty()) {
            String memCtx = buildMemoryContext(userId);
            aiSummary = aiAnalysisService.generateWeeklySummary(contents, analyses, memCtx);
            AiAnalysisService.ReportGuidance guidance = aiAnalysisService.generateWeeklyGuidance(contents, analyses);
            insights = guidance.insights();
            suggestions = guidance.suggestions();
            followUpPrompt = guidance.followUpPrompt();
        }

        Map<String, Integer> moodDistribution = buildMoodDistribution(dailyMoods);
        String dominantQuadrant = dominantQuadrant(moodDistribution);
        int positiveRatioPercent = calculatePositiveRatioPercent(moodDistribution);
        int highEnergyRatioPercent = calculateHighEnergyRatioPercent(moodDistribution);

        return new WeeklyReportView(
                weekLabel,
                diaries.size(),
                dailyMoods,
                sortedTopics,
                moodDistribution,
                dominantQuadrant,
                positiveRatioPercent,
                highEnergyRatioPercent,
                aiSummary,
                insights,
                suggestions,
                followUpPrompt,
                LocalDateTime.now(),
                false);
    }

    // ── Report DB persistence ──

    private LocalDate[] weekDates(int weekOffset) {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(DayOfWeek.MONDAY).plusWeeks(weekOffset);
        return new LocalDate[] { monday, monday.plusDays(6) };
    }

    private LocalDate[] monthDates(int monthOffset) {
        LocalDate today = LocalDate.now();
        LocalDate firstOfMonth = today.withDayOfMonth(1).plusMonths(monthOffset);
        LocalDate lastOfMonth = firstOfMonth.withDayOfMonth(firstOfMonth.lengthOfMonth());
        return new LocalDate[] { firstOfMonth, lastOfMonth };
    }

    private void saveReportToDb(long userId, WeeklyReportView report, int offset, boolean monthly) {
        try {
            LocalDate[] dates = monthly ? monthDates(offset) : weekDates(offset);
            LocalDate startDate = dates[0];
            LocalDate endDate = dates[1];
            String reportType = monthly ? "MONTHLY" : "WEEKLY";

            DiarySummaryEntity existing = diarySummaryMapper.selectOne(
                    new LambdaQueryWrapper<DiarySummaryEntity>()
                            .eq(DiarySummaryEntity::getUserId, userId)
                            .eq(DiarySummaryEntity::getStartDate, startDate)
                            .eq(DiarySummaryEntity::getEndDate, endDate)
                            .eq(DiarySummaryEntity::getReportType, reportType));

            DiarySummaryEntity entity = existing != null ? existing : new DiarySummaryEntity();
            entity.setUserId(userId);
            entity.setReportType(reportType);
            entity.setTitle(report.weekLabel());
            entity.setStartDate(startDate);
            entity.setEndDate(endDate);
            entity.setAiSummary(report.aiSummary());
            entity.setInsightsJson(report.insights() != null && !report.insights().isEmpty()
                    ? objectMapper.writeValueAsString(report.insights())
                    : null);
            entity.setSuggestionsJson(report.suggestions() != null && !report.suggestions().isEmpty()
                    ? objectMapper.writeValueAsString(report.suggestions())
                    : null);
            entity.setFollowUpPrompt(report.followUpPrompt());
            entity.setMoodsJson(objectMapper.writeValueAsString(report.dailyMoods()));
            entity.setTopicsJson(objectMapper.writeValueAsString(report.topicCounts()));
            entity.setDiaryCount(report.diaryCount());
            entity.setCreatedAt(LocalDateTime.now());

            if (existing != null) {
                diarySummaryMapper.updateById(entity);
            } else {
                diarySummaryMapper.insert(entity);
            }
        } catch (Exception e) {
            log.warn("Failed to save report to DB for userId={}, offset={}, monthly={}", userId, offset, monthly, e);
        }
    }

    private WeeklyReportView loadReportFromDb(long userId, int offset, boolean monthly) {
        try {
            LocalDate[] dates = monthly ? monthDates(offset) : weekDates(offset);
            String reportType = monthly ? "MONTHLY" : "WEEKLY";

            DiarySummaryEntity entity = diarySummaryMapper.selectOne(
                    new LambdaQueryWrapper<DiarySummaryEntity>()
                            .eq(DiarySummaryEntity::getUserId, userId)
                            .eq(DiarySummaryEntity::getStartDate, dates[0])
                            .eq(DiarySummaryEntity::getEndDate, dates[1])
                            .eq(DiarySummaryEntity::getReportType, reportType));

            if (entity == null || entity.getAiSummary() == null)
                return null;

            List<WeeklyReportView.DailyMood> dailyMoods = objectMapper.readValue(
                    entity.getMoodsJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class,
                            WeeklyReportView.DailyMood.class));

            Map<String, Integer> topicCounts = objectMapper.readValue(
                    entity.getTopicsJson(), new TypeReference<Map<String, Integer>>() {
                    });

            List<String> insights = entity.getInsightsJson() != null
                    ? objectMapper.readValue(entity.getInsightsJson(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class))
                    : List.of();

            List<String> suggestions = entity.getSuggestionsJson() != null
                    ? objectMapper.readValue(entity.getSuggestionsJson(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class))
                    : List.of();

            return new WeeklyReportView(
                    entity.getTitle(),
                    entity.getDiaryCount(),
                    dailyMoods,
                    topicCounts,
                    buildMoodDistribution(dailyMoods),
                    dominantQuadrant(buildMoodDistribution(dailyMoods)),
                    calculatePositiveRatioPercent(buildMoodDistribution(dailyMoods)),
                    calculateHighEnergyRatioPercent(buildMoodDistribution(dailyMoods)),
                    entity.getAiSummary(),
                    insights,
                    suggestions,
                    entity.getFollowUpPrompt(),
                    entity.getCreatedAt(),
                    false);
        } catch (Exception e) {
            log.debug("Failed to load report from DB for userId={}, offset={}, monthly={}", userId, offset, monthly, e);
            return null;
        }
    }

    public boolean hasUnreportedDiaries(long userId, LocalDate startDate, LocalDate endDate,
            LocalDateTime generatedAt) {
        if (generatedAt == null) {
            return true;
        }
        Long count = diaryMapper.selectCount(new LambdaQueryWrapper<DiaryEntity>()
                .eq(DiaryEntity::getAuthorUserId, userId)
                .ge(DiaryEntity::getCreatedAt, startDate.atStartOfDay())
                .le(DiaryEntity::getCreatedAt, endDate.atTime(LocalTime.MAX))
                .gt(DiaryEntity::getCreatedAt, generatedAt));
        return count != null && count > 0;
    }

    private WeeklyReportView withFreshness(WeeklyReportView report, long userId, int offset, boolean monthly) {
        if (report == null) {
            return null;
        }

        LocalDate today = LocalDate.now();
        LocalDate startDate;
        LocalDate endDate;
        if (monthly) {
            LocalDate firstOfMonth = today.withDayOfMonth(1).plusMonths(offset);
            LocalDate lastOfMonth = firstOfMonth.withDayOfMonth(firstOfMonth.lengthOfMonth());
            startDate = firstOfMonth;
            endDate = lastOfMonth;
        } else {
            LocalDate monday = today.with(DayOfWeek.MONDAY).plusWeeks(offset);
            LocalDate sunday = monday.plusDays(6);
            startDate = monday;
            endDate = sunday;
        }

        boolean needsRegenerate = hasUnreportedDiaries(userId, startDate, endDate, report.generatedAt());
        Map<String, Integer> moodDistribution = report.moodDistribution() != null
                ? report.moodDistribution()
                : buildMoodDistribution(report.dailyMoods());
        String dominantQuadrant = report.moodDominantQuadrant() != null
                ? report.moodDominantQuadrant()
                : dominantQuadrant(moodDistribution);
        Integer positiveRatioPercent = report.positiveRatioPercent() != null
                ? report.positiveRatioPercent()
                : calculatePositiveRatioPercent(moodDistribution);
        Integer highEnergyRatioPercent = report.highEnergyRatioPercent() != null
                ? report.highEnergyRatioPercent()
                : calculateHighEnergyRatioPercent(moodDistribution);
        return new WeeklyReportView(
                report.weekLabel(),
                report.diaryCount(),
                report.dailyMoods(),
                report.topicCounts(),
                moodDistribution,
                dominantQuadrant,
                positiveRatioPercent,
                highEnergyRatioPercent,
                report.aiSummary(),
                report.insights(),
                report.suggestions(),
                report.followUpPrompt(),
                report.generatedAt(),
                needsRegenerate);
    }

    private Map<String, Integer> buildMoodDistribution(List<WeeklyReportView.DailyMood> dailyMoods) {
        Map<String, Integer> distribution = new LinkedHashMap<>();
        distribution.put(Q_POS_HIGH, 0);
        distribution.put(Q_POS_LOW, 0);
        distribution.put(Q_NEG_HIGH, 0);
        distribution.put(Q_NEG_LOW, 0);

        if (dailyMoods == null) {
            return distribution;
        }
        for (WeeklyReportView.DailyMood mood : dailyMoods) {
            if (mood == null || mood.moodLabel() == null) {
                continue;
            }
            String quadrant = classifyMoodQuadrant(mood.moodLabel());
            distribution.put(quadrant, distribution.getOrDefault(quadrant, 0) + 1);
        }
        return distribution;
    }

    private String dominantQuadrant(Map<String, Integer> distribution) {
        return distribution.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(Q_POS_LOW);
    }

    private int calculatePositiveRatioPercent(Map<String, Integer> distribution) {
        int total = distribution.values().stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) {
            return 0;
        }
        int positive = distribution.getOrDefault(Q_POS_HIGH, 0) + distribution.getOrDefault(Q_POS_LOW, 0);
        return (int) Math.round((positive * 100.0) / total);
    }

    private int calculateHighEnergyRatioPercent(Map<String, Integer> distribution) {
        int total = distribution.values().stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) {
            return 0;
        }
        int highEnergy = distribution.getOrDefault(Q_POS_HIGH, 0) + distribution.getOrDefault(Q_NEG_HIGH, 0);
        return (int) Math.round((highEnergy * 100.0) / total);
    }

    private String classifyMoodQuadrant(String moodLabel) {
        if (isPositiveMood(moodLabel)) {
            return isHighEnergyMood(moodLabel) ? Q_POS_HIGH : Q_POS_LOW;
        }
        return isHighEnergyMood(moodLabel) ? Q_NEG_HIGH : Q_NEG_LOW;
    }

    private boolean isPositiveMood(String moodLabel) {
        return Set.of("喜悦", "期待", "兴奋", "自豪", "轻松", "平静", "感恩", "满足").contains(moodLabel);
    }

    private boolean isHighEnergyMood(String moodLabel) {
        return Set.of("喜悦", "期待", "兴奋", "自豪", "烦躁", "愤怒", "焦虑", "害怕").contains(moodLabel);
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

        String snippet = content.length() > 30 ? content.substring(0, 30) + "..." : content;

        // 通知日记作者
        if (!commenter.getId().equals(diary.getAuthorUserId())) {
            userGrowthService.addExp(commenter.getId(), ExpAction.COMMENT, null);
            notificationService.notifyComment(commenter, diaryId, diary.getAuthorUserId(),
                    comment.getId(), snippet);
        }

        // 通知被回复的人（回复的是某条评论的作者，且不是自己也不是日记作者）
        if (request.parentCommentId() != null) {
            DiaryCommentEntity parentComment = diaryCommentMapper.selectById(request.parentCommentId());
            if (parentComment != null
                    && !parentComment.getAuthorUserId().equals(commenter.getId())
                    && !parentComment.getAuthorUserId().equals(diary.getAuthorUserId())) {
                notificationService.notifyCommentReply(commenter, diaryId, parentComment.getAuthorUserId(),
                        comment.getId(), snippet);
            }
        }

        evictRelatedUserCaches(commenter.getId(), diary.getAuthorUserId());
        return buildDiaryView(diary, true);
    }

    public DiaryView resonate(long diaryId) {
        DiaryEntity diary = findPublicDiary(diaryId);
        UserEntity actor = currentUser();
        long userId = actor.getId();

        String setKey = "resonance:" + diaryId;
        Boolean isMember = redisTemplate.opsForSet().isMember(setKey, String.valueOf(userId));

        if (Boolean.FALSE.equals(isMember)) {
            redisTemplate.opsForSet().add(setKey, String.valueOf(userId));
            asyncPersistResonance(diaryId, userId, true);

            if (!actor.getId().equals(diary.getAuthorUserId())) {
                notificationService.notifyResonance(actor, diaryId, diary.getAuthorUserId(),
                        toDiarySnippet(diary.getContent()));
                // 防刷：同一日记对同一用户当天只给一次 EXP
                String expKey = "resonance:exp:" + diaryId + ":" + userId;
                if (Boolean.FALSE.equals(redisTemplate.hasKey(expKey))) {
                    long secondsUntilMidnight = java.time.LocalDateTime.now().until(
                            java.time.LocalDate.now().plusDays(1).atStartOfDay(),
                            java.time.temporal.ChronoUnit.SECONDS);
                    redisTemplate.opsForValue().set(expKey, "1", Duration.ofSeconds(secondsUntilMidnight));
                    userGrowthService.addExp(actor.getId(), ExpAction.LIKE, null);
                }
            }
        } else {
            redisTemplate.opsForSet().remove(setKey, String.valueOf(userId));
            asyncPersistResonance(diaryId, userId, false);
        }

        Long count = redisTemplate.opsForSet().size(setKey);
        diary.setResonanceCount(count != null ? count.intValue() : 0);
        diary.setUpdatedAt(LocalDateTime.now());

        evictRelatedUserCaches(actor.getId(), diary.getAuthorUserId());
        return buildDiaryView(diary, true);
    }

    @Async("aiExecutor")
    private void asyncPersistResonance(long diaryId, long userId, boolean isLike) {
        try {
            if (isLike) {
                DiaryResonanceEntity r = new DiaryResonanceEntity();
                r.setDiaryId(diaryId);
                r.setUserId(userId);
                r.setCreatedAt(LocalDateTime.now());
                diaryResonanceMapper.insert(r);
            } else {
                diaryResonanceMapper.delete(
                        new LambdaQueryWrapper<DiaryResonanceEntity>()
                                .eq(DiaryResonanceEntity::getDiaryId, diaryId)
                                .eq(DiaryResonanceEntity::getUserId, userId));
            }
            // 同步 DB 中的 resonance_count
            Long count = redisTemplate.opsForSet().size("resonance:" + diaryId);
            DiaryEntity diary = new DiaryEntity();
            diary.setId(diaryId);
            diary.setResonanceCount(count != null ? count.intValue() : 0);
            diaryMapper.updateById(diary);
        } catch (Exception e) {
            log.warn("异步持久化点赞失败 diaryId={} userId={} isLike={}", diaryId, userId, isLike, e);
        }
    }

    /** 拼接音乐元数据进 RAG 索引内容，让向量搜索能找到歌曲相关日记。 */
    private String buildIndexContent(String diaryContent, MusicMeta musicMeta) {
        if (musicMeta == null) return diaryContent;
        StringBuilder sb = new StringBuilder();
        sb.append("歌曲：").append(musicMeta.getTitle())
          .append(" 歌手：").append(musicMeta.getArtist());
        if (musicMeta.getUserLyric() != null && !musicMeta.getUserLyric().isBlank()) {
            sb.append(" 歌词：").append(musicMeta.getUserLyric());
        }
        sb.append("\n").append(diaryContent);
        return sb.toString();
    }

    private String toDiarySnippet(String content) {
        if (content == null)
            return "这篇日记";
        String normalized = content.strip();
        if (normalized.isEmpty())
            return "这篇日记";
        return normalized.length() > 12 ? normalized.substring(0, 12) + "..." : normalized;
    }

    private DiaryView toDiaryView(DiaryEntity diary) {
        return buildDiaryView(diary, true);
    }

    private DiaryView toOwnDiaryView(DiaryEntity diary) {
        return buildDiaryView(diary, false);
    }

    /**
     * 组装日记列表视图（feed 模式）。跳过评论和作者信息批量加载以缩减响应体积。
     */
    private List<DiaryView> buildDiaryViews(List<DiaryEntity> diaries, boolean isPublic) {
        List<Long> ids = diaries.stream().map(DiaryEntity::getId).toList();
        Map<Long, DiaryAnalysisEntity> analysisMap = batchLoadAnalyses(ids);
        Set<Long> authorIds = diaries.stream().map(DiaryEntity::getAuthorUserId).collect(Collectors.toSet());
        Map<Long, UserEntity> authorInfoMap = batchLoadAuthorInfo(authorIds);
        Set<Long> likedDiaryIds = batchLoadLikedDiaryIds(ids);
        return diaries.stream()
                .map(diary -> buildFeedView(diary, isPublic, analysisMap, authorInfoMap,
                        likedDiaryIds.contains(diary.getId())))
                .toList();
    }

    private DiaryView buildFeedView(DiaryEntity diary, boolean isPublic,
            Map<Long, DiaryAnalysisEntity> analysisMap, Map<Long, UserEntity> authorInfoMap,
            boolean likedByMe) {
        DiaryAnalysisEntity analysis = analysisMap.get(diary.getId());
        UserEntity author = authorInfoMap.get(diary.getAuthorUserId());
        String authorName = author != null ? author.getDisplayName() : diary.getAuthorName();
        String authorAvatar = author != null ? normalizeAvatar(author.getAvatar())
                : resolveAuthorAvatar(diary.getAuthorUserId());
        Integer authorLevel = author != null ? author.getLevel() : null;
        String feedContent = diary.getContent() != null && diary.getContent().length() > 150
                ? diary.getContent().substring(0, 150) + "..."
                : diary.getContent();
        return isPublic
                ? DiaryView.fromPublicFeed(diary, analysis, authorName, authorAvatar, authorLevel, likedByMe, feedContent)
                : DiaryView.fromFeed(diary, analysis, authorName, authorAvatar, authorLevel, likedByMe, feedContent);
    }

    private DiaryView buildDiaryView(DiaryEntity diary, boolean isPublic) {
        DiaryAnalysisEntity analysis = findAnalysis(diary.getId());
        List<DiaryCommentEntity> comments = findComments(diary.getId());
        boolean likedByMe = Boolean.TRUE.equals(
                redisTemplate.opsForSet().isMember("resonance:" + diary.getId(), String.valueOf(currentUser().getId())));
        return buildDiaryView(diary, isPublic,
                analysis != null ? Map.of(diary.getId(), analysis) : Map.of(),
                Map.of(diary.getId(), comments),
                likedByMe);
    }

    private DiaryView buildDiaryView(DiaryEntity diary,
            boolean isPublic,
            Map<Long, DiaryAnalysisEntity> analysisMap,
            Map<Long, List<DiaryCommentEntity>> commentMap,
            boolean likedByMe) {
        java.util.Set<Long> authorIds = new java.util.HashSet<>();
        authorIds.add(diary.getAuthorUserId());
        commentMap.getOrDefault(diary.getId(), List.of())
                .forEach(c -> authorIds.add(c.getAuthorUserId()));
        Map<Long, UserEntity> authorInfoMap = batchLoadAuthorInfo(authorIds);
        return buildDiaryView(diary, isPublic, analysisMap, commentMap, authorInfoMap, likedByMe);
    }

    private DiaryView buildDiaryView(DiaryEntity diary,
            boolean isPublic,
            Map<Long, DiaryAnalysisEntity> analysisMap,
            Map<Long, List<DiaryCommentEntity>> commentMap,
            Map<Long, UserEntity> authorInfoMap,
            boolean likedByMe) {
        DiaryAnalysisEntity analysis = analysisMap.get(diary.getId());
        List<DiaryCommentEntity> comments = commentMap.getOrDefault(diary.getId(), List.of());
        UserEntity author = authorInfoMap.get(diary.getAuthorUserId());
        String authorName = author != null ? author.getDisplayName() : diary.getAuthorName();
        String authorAvatar = author != null
                ? normalizeAvatar(author.getAvatar())
                : resolveAuthorAvatar(diary.getAuthorUserId());
        Map<Long, String> commentAuthorNames = new java.util.HashMap<>();
        for (DiaryCommentEntity c : comments) {
            UserEntity cu = authorInfoMap.get(c.getAuthorUserId());
            commentAuthorNames.put(c.getAuthorUserId(),
                    cu != null ? cu.getDisplayName() : c.getAuthorName());
        }
        Integer authorLevel = author != null ? author.getLevel() : null;
        return isPublic
                ? DiaryView.fromPublic(diary, analysis, comments, authorAvatar, authorName, authorLevel, commentAuthorNames,
                        likedByMe)
                : DiaryView.from(diary, analysis, comments, authorAvatar, authorName, authorLevel, commentAuthorNames, likedByMe);
    }

    private Set<Long> batchLoadLikedDiaryIds(List<Long> diaryIds) {
        if (diaryIds.isEmpty()) {
            return Set.of();
        }
        Long currentUserId = currentUser().getId();
        String uid = String.valueOf(currentUserId);
        Set<Long> result = new java.util.HashSet<>();
        for (Long diaryId : diaryIds) {
            Boolean isMember = redisTemplate.opsForSet().isMember("resonance:" + diaryId, uid);
            if (Boolean.TRUE.equals(isMember)) {
                result.add(diaryId);
            }
        }
        return result;
    }

    private Map<Long, DiaryAnalysisEntity> batchLoadAnalyses(List<Long> diaryIds) {
        if (diaryIds.isEmpty())
            return Map.of();
        return diaryAnalysisMapper.selectBatchIds(diaryIds).stream()
                .collect(Collectors.toMap(DiaryAnalysisEntity::getDiaryId, analysis -> analysis));
    }

    private Map<Long, List<DiaryCommentEntity>> batchLoadComments(List<Long> diaryIds) {
        if (diaryIds.isEmpty())
            return Map.of();
        List<DiaryCommentEntity> comments = diaryCommentMapper.selectList(
                new LambdaQueryWrapper<DiaryCommentEntity>()
                        .in(DiaryCommentEntity::getDiaryId, diaryIds)
                        .orderByAsc(DiaryCommentEntity::getCreatedAt));
        return comments.stream().collect(Collectors.groupingBy(DiaryCommentEntity::getDiaryId));
    }

    private Map<Long, UserEntity> batchLoadAuthorInfo(java.util.Set<Long> authorIds) {
        if (authorIds.isEmpty())
            return Map.of();
        return userMapper.selectBatchIds(new ArrayList<>(authorIds)).stream()
                .collect(Collectors.toMap(UserEntity::getId, u -> u));
    }

    private String resolveAuthorAvatar(Long authorUserId) {
        if (authorUserId == null)
            return null;
        UserEntity author = userMapper.selectById(authorUserId);
        if (author == null)
            return null;
        return normalizeAvatar(author.getAvatar());
    }

    private String normalizeAvatar(String avatar) {
        if (avatar == null || avatar.isBlank())
            return avatar;
        String normalized = avatar.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("data:")) {
            return normalized;
        }
        if (normalized.startsWith("/api/uploads/")) {
            return normalized;
        }
        if (normalized.startsWith("/uploads/")) {
            return "/api" + normalized;
        }
        if (normalized.startsWith("uploads/")) {
            return "/api/" + normalized;
        }
        return normalized;
    }

    private List<DiaryEntity> filterHidden(List<DiaryEntity> diaries, long userId) {
        return diaries;
    }

    private Page<DiaryView> filterHiddenViews(Page<DiaryView> page, long userId) {
        return page;
    }

    private Set<Long> hiddenDiaryIds(long userId) {
        return Set.of();
    }

    private Set<Long> recentExposureIds(long userId, String scene) {
        List<DiaryRecommendationExposureEntity> exposures = exposureMapper.selectList(
                new LambdaQueryWrapper<DiaryRecommendationExposureEntity>()
                        .eq(DiaryRecommendationExposureEntity::getUserId, userId)
                        .eq(DiaryRecommendationExposureEntity::getScene, scene)
                        .ge(DiaryRecommendationExposureEntity::getCreatedAt, LocalDateTime.now().minusDays(7)));
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
                        .orderByAsc(DiaryCommentEntity::getCreatedAt));
    }

    // ── Current user ──

    @Transactional
    public void deleteDiary(long diaryId) {
        UserEntity user = currentUser();
        DiaryEntity diary = diaryMapper.selectById(diaryId);
        if (diary == null)
            throw new ResponseStatusException(NOT_FOUND, "日记不存在");
        if (!diary.getAuthorUserId().equals(user.getId()) && !"ADMIN".equals(user.getRole())) {
            throw new ResponseStatusException(FORBIDDEN, "只能删除自己的日记或由管理员操作");
        }
        diaryMapper.deleteById(diaryId);
        markReportsStale(diary.getAuthorUserId());
        if ("PUBLIC".equals(diary.getVisibility())) {
            evictPublicDiaryCaches();
        }
        ragMemoryService.deleteDiaryEmbedding(diaryId);
        ossService.deleteImages(diary.getImages());
        log.info("日记{}删除成功，diaryId={}，操作者UserId={}，原作者UserId={}",
                "ADMIN".equals(user.getRole()) ? "强制" : "", diaryId, user.getId(), diary.getAuthorUserId());
    }

    @Transactional
    public void hideDiary(long diaryId) {
        throw new ResponseStatusException(BAD_REQUEST, "该功能已下线");
    }

    @Transactional
    public void deleteComment(long diaryId, long commentId) {
        UserEntity user = currentUser();
        DiaryEntity diary = diaryMapper.selectById(diaryId);
        if (diary == null)
            throw new ResponseStatusException(NOT_FOUND, "日记不存在");
        DiaryCommentEntity comment = diaryCommentMapper.selectById(commentId);
        if (comment == null || !comment.getDiaryId().equals(diaryId)) {
            throw new ResponseStatusException(NOT_FOUND, "评论不存在");
        }
        if (!comment.getAuthorUserId().equals(user.getId()) && !"ADMIN".equals(user.getRole())) {
            throw new ResponseStatusException(FORBIDDEN, "只能删除自己的评论或由管理员操作");
        }
        diaryCommentMapper.deleteById(commentId);
        evictRelatedUserCaches(comment.getAuthorUserId(), diary.getAuthorUserId());
    }

    public static String snippet(String content) {
        if (content == null || content.isEmpty())
            return "";
        return content.length() > 30 ? content.substring(0, 30) : content;
    }

    // ── Daily status ──

    public Map<String, Object> todayStatus() {
        UserEntity user = currentUser();
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();

        // 一次查询获取最近 90 天有日记的日期，内存中计算连续天数（避免循环 SQL）
        List<DiaryEntity> recentDiaries = diaryMapper.selectList(
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getAuthorUserId, user.getId())
                        .ge(DiaryEntity::getCreatedAt, today.minusDays(90).atStartOfDay())
                        .select(DiaryEntity::getCreatedAt));
        java.util.Set<LocalDate> datesWithDiary = recentDiaries.stream()
                .map(e -> e.getCreatedAt().toLocalDate())
                .collect(Collectors.toSet());

        boolean todayExists = datesWithDiary.contains(today);

        int streak = 0;
        LocalDate d = today;
        while (datesWithDiary.contains(d)) {
            streak++;
            d = d.minusDays(1);
        }

        // 昨天情绪
        String yesterdayMood = null;
        List<DiaryEntity> yesterdayDiaries = diaryMapper.selectList(
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getAuthorUserId, user.getId())
                        .ge(DiaryEntity::getCreatedAt, today.minusDays(1).atStartOfDay())
                        .lt(DiaryEntity::getCreatedAt, today.atStartOfDay())
                        .orderByDesc(DiaryEntity::getCreatedAt)
                        .last("LIMIT 1"));
        if (!yesterdayDiaries.isEmpty()) {
            DiaryAnalysisEntity analysis = findAnalysis(yesterdayDiaries.get(0).getId());
            if (analysis != null)
                yesterdayMood = analysis.getMoodLabel();
        }

        return Map.of(
                "todayHasDiary", todayExists,
                "streak", streak,
                "yesterdayMood", yesterdayMood != null ? yesterdayMood : "");
    }

    // ── Today match ──

    public DiaryView todayMatch() {
        UserEntity user = currentUser();
        // 获取用户最近的情绪标签
        List<DiaryEntity> recent = diaryMapper.selectList(
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getAuthorUserId, user.getId())
                        .orderByDesc(DiaryEntity::getCreatedAt)
                        .last("LIMIT 3"));
        String targetMood = null;
        Map<Long, DiaryAnalysisEntity> recentAnalysisMap = batchLoadAnalyses(
                recent.stream().map(DiaryEntity::getId).toList());
        for (DiaryEntity d : recent) {
            DiaryAnalysisEntity a = recentAnalysisMap.get(d.getId());
            if (a != null) {
                targetMood = a.getMoodLabel();
                break;
            }
        }
        if (targetMood == null)
            return null;

        // 找公开的同情绪日记
        List<DiaryEntity> matches = diaryMapper.selectList(
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getVisibility, "PUBLIC")
                        .ne(DiaryEntity::getAuthorUserId, user.getId())
                        .orderByDesc(DiaryEntity::getCreatedAt)
                        .last("LIMIT 50"));
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
            // 补上第5个参数 false，以及明确 Map 的类型
            return buildDiaryView(selected, true, matchAnalysisMap, java.util.Collections.emptyMap(), false);
        }
        return null;
    }

    // ── Coaching ──

    public Map<String, Object> coachingPlan() {
        UserEntity user = currentUser();
        String cacheKey = "coaching:" + user.getId();

        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null)
                return objectMapper.readValue(cached, Map.class);
        } catch (Exception e) {
            log.debug("Coaching cache miss");
        }

        List<DiaryEntity> recent = diaryMapper.selectList(
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getAuthorUserId, user.getId())
                        .orderByDesc(DiaryEntity::getCreatedAt)
                        .last("LIMIT 7"));
        List<String> contents = new ArrayList<>();
        List<DiaryAnalysis> analyses = new ArrayList<>();
        Map<Long, DiaryAnalysisEntity> analysisMap = batchLoadAnalyses(
                recent.stream().map(DiaryEntity::getId).toList());
        for (DiaryEntity d : recent) {
            contents.add(d.getContent());
            DiaryAnalysisEntity a = analysisMap.get(d.getId());
            if (a != null)
                analyses.add(new DiaryAnalysis(a.getMoodLabel(), a.getMoodIntensity(),
                        a.getTopicLabelsJson(),
                        a.getSecondaryMoodsJson() != null ? a.getSecondaryMoodsJson() : List.of(),
                        a.getSummary(), a.getFeedback()));
            else
                analyses.add(null);
        }
        String suggestion = aiAnalysisService.generateCoaching(contents, analyses);
        Map<String, Object> result = Map.of("suggestion", suggestion, "diaryCount", recent.size());

        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result), Duration.ofMinutes(15));
        } catch (Exception e) {
            log.debug("Coaching cache write failed");
        }
        return result;
    }

    // ── Community mood ──

    public Map<String, Integer> communityMood() {
        List<DiaryEntity> todayPublic = diaryMapper.selectList(
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getVisibility, "PUBLIC")
                        .ge(DiaryEntity::getCreatedAt, LocalDate.now().atStartOfDay()));
        todayPublic = filterHidden(todayPublic, currentUser().getId());
        List<String> moods = new ArrayList<>();
        Map<Long, DiaryAnalysisEntity> analysisMap = batchLoadAnalyses(
                todayPublic.stream().map(DiaryEntity::getId).toList());
        for (DiaryEntity d : todayPublic) {
            DiaryAnalysisEntity a = analysisMap.get(d.getId());
            if (a != null)
                moods.add(a.getMoodLabel());
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
                ? message.substring(0, 200)
                : message);
        diaryResonanceMapper.insert(r);

        diary.setResonanceCount(diary.getResonanceCount() + 1);
        diaryMapper.updateById(diary);

        if (!diary.getAuthorUserId().equals(actor.getId())) {
            notificationService.notifyEncouragement(diaryId, diary.getAuthorUserId(), message);
        }

        evictRelatedUserCaches(actor.getId(), diary.getAuthorUserId());
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
        if (parentCommentId == null)
            return null;
        DiaryCommentEntity parent = diaryCommentMapper.selectById(parentCommentId);
        if (parent == null || !parent.getDiaryId().equals(diaryId))
            return null;
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

    private void evictRelatedUserCaches(Long primaryUserId, Long secondaryUserId) {
        if (primaryUserId != null) {
            evictUserCache(primaryUserId);
        }
        if (secondaryUserId != null && !secondaryUserId.equals(primaryUserId)) {
            evictUserCache(secondaryUserId);
        }
    }

    private void evictPublicDiaryCaches() {
        try {
            List<String> keysToDelete = new ArrayList<>();
            for (int page = 0; page <= 5; page++) {
                for (int size : List.of(10, 20, 50)) {
                    keysToDelete.add("public:diaries:%d:%d".formatted(page, size));
                }
            }
            redisTemplate.delete(keysToDelete);
        } catch (Exception e) {
            log.debug("Public diary cache eviction failed", e);
        }
    }

    private void markReportsStale(long userId) {
        try {
            List<String> keysToDelete = new ArrayList<>();
            keysToDelete.add("coaching:" + userId);
            for (int offset = -4; offset <= 3; offset++) {
                keysToDelete.add("report:%d:%d".formatted(userId, offset));
            }
            for (int offset = -5; offset <= 0; offset++) {
                keysToDelete.add("report:monthly:%d:%d".formatted(userId, offset));
            }
            redisTemplate.delete(keysToDelete);
        } catch (Exception e) {
            log.debug("Cache mark stale failed", e);
        }
    }

    private void evictUserCache(Long userId) {
        if (userId == null) {
            return;
        }
        markReportsStale(userId);
    }

    private int similarityScore(DiaryAnalysisEntity sourceAnalysis, DiaryAnalysisEntity targetAnalysis) {
        if (sourceAnalysis == null || targetAnalysis == null)
            return 0;
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

    private String buildMemoryContext(long userId) {
        try {
            List<UserProfileMemoryEntity> memories = memoryExtractionService.listUserMemories(userId);
            if (memories == null || memories.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (UserProfileMemoryEntity m : memories) {
                sb.append(m.getAttributeKey()).append(": ").append(m.getAttributeValue()).append("; ");
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
