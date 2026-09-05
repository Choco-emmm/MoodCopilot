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
import com.moodcopilot.entity.DiaryImageMeta;
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

import com.moodcopilot.common.RateLimitException;
import com.moodcopilot.ai.mq.AiTaskProducer;

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
    private final com.moodcopilot.mapper.DiaryKnowledgeGraphMapper diaryKnowledgeGraphMapper;
    private final com.moodcopilot.mapper.DiaryCollectionRelationMapper diaryCollectionRelationMapper;
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
    private final com.moodcopilot.music.MusicParseService musicParseService;
    private final RateLimitService rateLimitService;
    private final UserGrowthService userGrowthService;
    private final TransactionTemplate transactionTemplate;
    private final AiTaskProducer aiTaskProducer;
    private final DiaryCacheService diaryCacheService;
    private final com.moodcopilot.event.LifeEventService lifeEventService;
    private final com.moodcopilot.event.LifeChapterService lifeChapterService;

    public DiaryService(DiaryMapper diaryMapper,
            DiaryAnalysisMapper diaryAnalysisMapper,
            DiaryCommentMapper diaryCommentMapper,
            DiaryResonanceMapper diaryResonanceMapper,
            DiaryHideMapper diaryHideMapper,
            DiaryRecommendationExposureMapper exposureMapper,
            UserMapper userMapper,
            com.moodcopilot.mapper.DiaryKnowledgeGraphMapper diaryKnowledgeGraphMapper,
            com.moodcopilot.mapper.DiaryCollectionRelationMapper diaryCollectionRelationMapper,
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
            com.moodcopilot.music.MusicParseService musicParseService,
            RateLimitService rateLimitService,
            UserGrowthService userGrowthService,
            TransactionTemplate transactionTemplate,
            @org.springframework.context.annotation.Lazy AiTaskProducer aiTaskProducer,
            DiaryCacheService diaryCacheService,
            @org.springframework.context.annotation.Lazy com.moodcopilot.event.LifeEventService lifeEventService,
            @org.springframework.context.annotation.Lazy com.moodcopilot.event.LifeChapterService lifeChapterService) {
        this.diaryMapper = diaryMapper;
        this.diaryAnalysisMapper = diaryAnalysisMapper;
        this.diaryCommentMapper = diaryCommentMapper;
        this.diaryResonanceMapper = diaryResonanceMapper;
        this.diaryHideMapper = diaryHideMapper;
        this.exposureMapper = exposureMapper;
        this.userMapper = userMapper;
        this.diaryKnowledgeGraphMapper = diaryKnowledgeGraphMapper;
        this.diaryCollectionRelationMapper = diaryCollectionRelationMapper;
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
        this.musicParseService = musicParseService;
        this.rateLimitService = rateLimitService;
        this.userGrowthService = userGrowthService;
        this.transactionTemplate = transactionTemplate;
        this.aiTaskProducer = aiTaskProducer;
        this.diaryCacheService = diaryCacheService;
        this.lifeEventService = lifeEventService;
        this.lifeChapterService = lifeChapterService;
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
        diary.setAuthorName(user.getNickname() != null ? user.getNickname() : user.getDisplayName());
        diary.setContent(content);
        if (visibility == DiaryVisibility.PUBLIC && ContentFilter.hasBannedWords(content)) {
            diary.setVisibility("BANNED");
        } else {
            diary.setVisibility(visibility.name());
        }
        diary.setMusicMeta(request.musicMeta());
        diary.setImages(promoteImages(request.images()));
        diary.setImageMeta(normalizeImageMeta(request.imageMeta(), diary.getImages()));
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
                diary.getContent(), diary.getMusicMeta());

        return DiaryView.from(diary, null, List.of(), normalizeAvatar(user.getAvatar()), user.getNickname() != null ? user.getNickname() : user.getDisplayName(),
                user.getLevel(), user.getRole(), Map.of(),
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
        List<String> oldImages = diary.getImages() == null ? List.of() : new ArrayList<>(diary.getImages());
        boolean hasBannedWords = visibility == DiaryVisibility.PUBLIC && ContentFilter.hasBannedWords(normalizedContent);
        String finalVisibility = hasBannedWords ? "BANNED" : visibility.name();
        boolean contentChanged = !oldContent.equals(normalizedContent);
        boolean visibilityChanged = !finalVisibility.equals(oldVisibility);

        // DB 写入放在编程式事务内，确保原子性且不扩散到 LLM 调用
        transactionTemplate.executeWithoutResult(status -> {
            diary.setContent(normalizedContent);
            diary.setVisibility(finalVisibility);
            diary.setUpdatedAt(LocalDateTime.now());
            if (request.musicMeta() != null) {
                diary.setMusicMeta(request.musicMeta());
            }
            if (request.images() != null) {
                List<String> promotedImages = promoteImages(request.images());
                diary.setImages(promotedImages);

                if (request.imageMeta() != null) {
                    diary.setImageMeta(normalizeImageMeta(request.imageMeta(), promotedImages));
                } else if (areSameImageList(oldImages, promotedImages)) {
                    // 编辑正文等场景不应覆写已有图片压缩元数据。
                    diary.setImageMeta(diary.getImageMeta());
                } else {
                    diary.setImageMeta(normalizeImageMeta(null, promotedImages));
                }
            } else if (request.imageMeta() != null) {
                diary.setImageMeta(normalizeImageMeta(request.imageMeta(), diary.getImages()));
            }
            diaryMapper.updateById(diary);
        });

        String analysisStatus = null;
        // AI 分析：在事务外执行，避免 HikariCP 连接泄漏
        if (contentChanged) {
            log.info("日记内容已更新，触发画像重建，diaryId={}，userId={}", diaryId, user.getId());
            ragMemoryService.indexDiary(user.getId(), diaryId,
                    normalizedContent, diary.getMusicMeta());

            if (!request.isAnalyze()) {
                analysisStatus = "skipped_user";
                log.info("用户主动关闭AI分析，不执行日记分析，diaryId={}", diaryId);
            } else {
                try {
                    if (request.isUseReasoning()) {
                        rateLimitService.tryAcquire(user, RateLimitService.AiApiType.REASONING);
                    } else {
                        rateLimitService.tryAcquire(user, RateLimitService.AiApiType.ANALYSIS);
                    }

                    // 删除旧的分析结果，防止前端在轮询时错误获取到旧数据
                    transactionTemplate.executeWithoutResult(status -> {
                        diaryAnalysisMapper.deleteById(diaryId);
                    });

                    log.info("日记内容已修改，提交后台重新执行 AI 分析，diaryId={}", diaryId);
                    submitAiAnalysisTask(diaryId, user.getId(), request.isUseReasoning());
                    analysisStatus = "analyzing";
                } catch (RateLimitException e) {
                    analysisStatus = request.isUseReasoning() ? "failed_limit" : "skipped_quota";
                    log.info("AI分析限额已满，跳过分析，diaryId={}", diaryId);
                }
            }
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

        DiaryView view = buildDiaryView(diary, "PUBLIC".equals(diary.getVisibility()));
        if (analysisStatus != null) {
            return view.withAnalysisStatus(analysisStatus);
        }
        return view;
    }

    public void submitAiAnalysisTask(long diaryId, long userId, boolean useReasoning) {
        submitAiAnalysisTask(diaryId, userId, useReasoning, false);
    }

    public void submitAiAnalysisTask(long diaryId, long userId, boolean useReasoning, boolean forceRetry) {
        DiaryEntity status = new DiaryEntity();
        status.setId(diaryId);
        status.setAnalysisStatus("analyzing");
        status.setAnalysisError(null);
        status.setRequestedModel(useReasoning ? "deepseek-v4-pro" : "deepseek-v4-flash");
        status.setActualModel(null);
        status.setFallbackReason(null);
        diaryMapper.updateById(status);
        try {
            aiTaskProducer.cancelPendingDiaryAnalysisTasks(diaryId, userId);
            aiTaskProducer.submitDiaryAnalysisTask(diaryId, userId, useReasoning, forceRetry);
        } catch (RuntimeException e) {
            DiaryEntity failed = new DiaryEntity();
            failed.setId(diaryId);
            failed.setAnalysisStatus("failed");
            failed.setAnalysisError(truncateAnalysisError(e.getMessage(), "AI 任务提交失败"));
            diaryMapper.updateById(failed);
            throw e;
        }
    }

    @Transactional
    public DiaryView retryAnalysis(long diaryId, boolean useReasoning) {
        UserEntity user = currentUser();
        DiaryEntity diary = findDiary(diaryId);
        if (!user.getId().equals(diary.getAuthorUserId())) {
            throw new ResponseStatusException(FORBIDDEN, "只能重新分析自己的日记");
        }
        if (Boolean.TRUE.equals(diary.getIsDeleted())) {
            throw new ResponseStatusException(NOT_FOUND, "日记不存在");
        }
        DiaryAnalysisEntity existing = diaryAnalysisMapper.selectById(diaryId);
        String status = diary.getAnalysisStatus();
        boolean allowed = existing == null || "failed".equals(status) || "failed_limit".equals(status);
        if (!allowed) {
            throw new ResponseStatusException(BAD_REQUEST, "当前日记不需要重新分析");
        }
        try {
            rateLimitService.tryAcquire(user, useReasoning ? RateLimitService.AiApiType.REASONING
                    : RateLimitService.AiApiType.ANALYSIS);
        } catch (RateLimitException e) {
            DiaryEntity limited = new DiaryEntity();
            limited.setId(diaryId);
            limited.setAnalysisStatus(useReasoning ? "failed_limit" : "skipped_quota");
            limited.setAnalysisError(truncateAnalysisError(e.getMessage(), "额度不足"));
            diaryMapper.updateById(limited);
            return buildDiaryView(diaryMapper.selectById(diaryId), false)
                    .withAnalysisStatus(limited.getAnalysisStatus());
        }
        diaryAnalysisMapper.deleteById(diaryId);
        submitAiAnalysisTask(diaryId, user.getId(), useReasoning, true);
        return buildDiaryView(diaryMapper.selectById(diaryId), false).withAnalysisStatus("analyzing");
    }
    public void runAiAnalysisSync(long diaryId, long userId, boolean useReasoning) {
        runAiAnalysisSync(diaryId, userId, useReasoning, null);
    }

    public void runAiAnalysisSync(long diaryId, long userId, boolean useReasoning, String parentTaskId) {
        DiaryEntity diary = diaryMapper.selectById(diaryId);
        if (diary == null || diary.getIsDeleted()) {
            log.warn("无法执行 AI 分析，日记不存在或已删除，diaryId={}", diaryId);
            if (diary != null) {
                DiaryEntity cancelled = new DiaryEntity();
                cancelled.setId(diaryId);
                cancelled.setAnalysisStatus("cancelled");
                cancelled.setAnalysisError("日记已删除，跳过分析");
                diaryMapper.updateById(cancelled);
            }
            return;
        }
        DiaryEntity running = new DiaryEntity();
        running.setId(diaryId);
        running.setAnalysisStatus("analyzing");
        running.setAnalysisError(null);
        running.setActualModel(null);
        running.setFallbackReason(null);
        diaryMapper.updateById(running);
        String content = diary.getContent();
        MusicMeta musicMeta = diary.getMusicMeta();
        java.util.List<String> images = diary.getImages();
        java.util.List<DiaryImageMeta> imageMeta = diary.getImageMeta();

        logImageCompressionSummary(diaryId, imageMeta, images);

        // 补充音乐氛围（异步解析可能尚未完成，从缓存或同步补全）
        if (musicMeta != null && musicMeta.getSongUrl() != null &&
                (musicMeta.getMoodTags() == null || musicMeta.getThemeSummary() == null)) {
            String md5Hex = org.springframework.util.DigestUtils.md5DigestAsHex(musicMeta.getSongUrl().getBytes());
            String cacheKey = "music:meta:" + md5Hex;
            try {
                String cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    MusicMeta cachedMeta = objectMapper.readValue(cached, MusicMeta.class);
                    if (cachedMeta.getMoodTags() != null)
                        musicMeta.setMoodTags(cachedMeta.getMoodTags());
                    if (cachedMeta.getThemeSummary() != null)
                        musicMeta.setThemeSummary(cachedMeta.getThemeSummary());
                    log.info("已从 Redis 补全音乐氛围");
                }
            } catch (Exception e) {
                log.warn("从 Redis 读取音乐缓存失败: {}", e.getMessage());
            }
            // 缓存仍为空则同步解析
            boolean needsSync = musicMeta.getMoodTags() == null || musicMeta.getThemeSummary() == null;
            if (needsSync) {
                try {
                    log.info("日记分析阶段：开始补充音乐元数据，diaryId={}", diaryId);
                    List<String> lyrics = musicParseService.suggestLyrics(musicMeta.getTitle(), musicMeta.getArtist(),
                            musicMeta.getSongUrl());
                    String lyricsStr;
                    if (!lyrics.isEmpty()) {
                        lyricsStr = String.join("\n", lyrics);
                    } else {
                        lyricsStr = "（歌词未获取到，请根据歌曲名和歌手推测）";
                        log.info("歌词获取为空，使用歌名兜底分析");
                    }
                    var result = aiAnalysisService.analyzeMusicSync(userId, musicMeta.getTitle(),
                            musicMeta.getArtist(), lyricsStr);
                    musicMeta.setMoodTags(result.getLeft());
                    musicMeta.setThemeSummary(result.getRight());
                    log.info("同步补全音乐氛围成功");
                } catch (Exception e) {
                    log.warn("同步补全音乐氛围失败: {}", e.getMessage());
                }
            }
            // 写回数据库（无论来源是 Redis 还是同步分析）
            DiaryEntity updateEntity = new DiaryEntity();
            updateEntity.setId(diaryId);
            updateEntity.setMusicMeta(musicMeta);
            diaryMapper.updateById(updateEntity);
        }

        log.info("开始同步执行日记 AI 分析，diaryId={}，userId={}，contentLength={}，hasMusic={}，hasImages={}", diaryId, userId,
                content == null ? 0 : content.length(), musicMeta != null, images != null && !images.isEmpty());
        try {
            log.info("日记分析阶段：开始视觉描述，diaryId={}", diaryId);
            String imageDescriptions = visionService.describeImages(images, imageMeta);
            log.info("日记分析阶段：视觉描述完成，diaryId={}，descriptionLength={}", diaryId,
                    imageDescriptions == null ? 0 : imageDescriptions.length());
            log.info("日记分析阶段：开始情绪分析，diaryId={}，model={}", diaryId,
                    useReasoning ? "deepseek-v4-pro" : "deepseek-v4-flash");
            long analysisStartedAt = System.nanoTime();
            AiAnalysisService.DiaryAnalysisResult analysisResult = aiAnalysisService.analyzeWithMemorySignals(
                    userId, content, musicMeta, imageDescriptions, useReasoning);
            DiaryAnalysis analysis = analysisResult.analysis();
            log.info("日记分析阶段：情绪分析返回，diaryId={}，durationMs={}", diaryId,
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - analysisStartedAt));

            DiaryAnalysisEntity analysisEntity = new DiaryAnalysisEntity();
            analysisEntity.setDiaryId(diaryId);
            analysisEntity.setMoodLabel(analysis.moodLabel());
            analysisEntity.setMoodIntensity(analysis.moodIntensity());
            analysisEntity.setValence(analysis.valence());
            analysisEntity.setArousal(analysis.arousal());
            analysisEntity.setSecondaryMoodsJson(analysis.secondaryMoods());
            analysisEntity.setTopicLabelsJson(analysis.topicLabels());
            analysisEntity.setMemorySignalsJson(analysisResult.memorySignals());
            analysisEntity.setSummary(analysis.summary());
            analysisEntity.setFeedback(analysis.feedback());
            analysisEntity.setCreatedAt(LocalDateTime.now());
            analysisEntity.setUpdatedAt(LocalDateTime.now());

            DiaryAnalysisEntity existing = diaryAnalysisMapper.selectById(diaryId);
            if (existing != null) {
                analysisEntity.setCreatedAt(existing.getCreatedAt());
                diaryAnalysisMapper.updateById(analysisEntity);
            } else {
                diaryAnalysisMapper.insert(analysisEntity);
            }
            DiaryEntity completed = new DiaryEntity();
            completed.setId(diaryId);
            completed.setAnalysisStatus("complete");
            completed.setAnalysisError(null);
            completed.setActualModel(useReasoning ? "deepseek-v4-pro" : "deepseek-v4-flash");
            completed.setFallbackReason(null);
            diaryMapper.updateById(completed);
            log.info("日记 AI 分析已落库，diaryId={}", diaryId);

            eventPublisher.publishEvent(new DiaryAnalysisCompletedEvent(
                    this, diaryId, userId, analysis.moodLabel(), analysis.moodIntensity(), analysis.topicLabels(),
                    content, analysis.summary(), analysis.feedback(), analysis.valence(), analysis.arousal()));

            String analysisVersion = org.springframework.util.DigestUtils.md5DigestAsHex(
                    (content == null ? "" : content).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            aiTaskProducer.submitAnalysisPostProcessTasks(diaryId, userId, analysisVersion,
                    useReasoning ? "deepseek-v4-pro" : "deepseek-v4-flash", parentTaskId);
            // 主分析成功即可进入动态时间线；画像、图谱等后处理失败不应阻断阶段归属。
            lifeChapterService.markDirtyForDiary(userId, diaryId);
            log.info("日记分析后处理任务已创建，diaryId={}，userId={}，analysisVersion={}", diaryId, userId, analysisVersion);
        } catch (Exception e) {
            DiaryEntity failed = new DiaryEntity();
            failed.setId(diaryId);
            failed.setAnalysisStatus("failed");
            failed.setAnalysisError(truncateAnalysisError(e.getMessage(), "AI 分析失败"));
            diaryMapper.updateById(failed);
            log.error("日记 AI 分析异步任务失败，diaryId={}，userId={}，错误信息={}", diaryId, userId, e.getMessage(), e);
            throw e;
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

    public Page<DiaryView> searchDiaries(String keyword, LocalDate startDate, LocalDate endDate,
            String visibility, int page, int size) {
        int cappedPage = Math.max(1, page);
        int cappedSize = Math.min(50, Math.max(1, size));
        LambdaQueryWrapper<DiaryEntity> query = new LambdaQueryWrapper<DiaryEntity>()
                .eq(DiaryEntity::getAuthorUserId, currentUser().getId());

        if (keyword != null && !keyword.isBlank()) {
            String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            query.and(w -> w
                    .like(DiaryEntity::getContent, escaped)
                    .or()
                    .apply("JSON_UNQUOTE(JSON_EXTRACT(music_meta, '$.title')) LIKE {0}", "%" + escaped + "%")
                    .or()
                    .apply("JSON_UNQUOTE(JSON_EXTRACT(music_meta, '$.artist')) LIKE {0}", "%" + escaped + "%"));
        }
        if (startDate != null) {
            query.ge(DiaryEntity::getCreatedAt, startDate.atStartOfDay());
        }
        if (endDate != null) {
            query.lt(DiaryEntity::getCreatedAt, endDate.plusDays(1).atStartOfDay());
        }
        if (visibility != null && !visibility.isBlank()) {
            query.eq(DiaryEntity::getVisibility, visibility.toUpperCase());
        }
        query.orderByDesc(DiaryEntity::getCreatedAt);

        Page<DiaryEntity> entityPage = diaryMapper.selectPage(Page.of(cappedPage, cappedSize), query);
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
        log.info("执行历史日记检索，userId={}，startDate={}，endDate={}", user.getId(), startDate, endDate);

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
                .eq(DiaryEntity::getIsDeleted, false)
                .orderByDesc(DiaryEntity::getCreatedAt)
                ;

        if (keyword != null) {
            query.like(DiaryEntity::getContent, keyword);
        }
        if (startDate != null) {
            query.ge(DiaryEntity::getCreatedAt, startDate.atStartOfDay());
        }
        if (endDate != null) {
            query.le(DiaryEntity::getCreatedAt, endDate.atTime(LocalTime.MAX));
        }

        List<DiarySearchResult.DiarySummary> diaries = diaryMapper.selectPage(Page.of(1, 20), query).getRecords().stream()
                .map(diary -> {
                    StringBuilder prefixSb = new StringBuilder();
                    if (diary.getMusicMeta() != null && diary.getMusicMeta().getTitle() != null
                            && !diary.getMusicMeta().getTitle().isBlank()) {
                        prefixSb.append("[分享音乐：").append(diary.getMusicMeta().getTitle());
                        if (diary.getMusicMeta().getArtist() != null && !diary.getMusicMeta().getArtist().isBlank()) {
                            prefixSb.append(" - ").append(diary.getMusicMeta().getArtist());
                        }
                        prefixSb.append("] ");
                    }
                    if (diary.getImages() != null && !diary.getImages().isEmpty()) {
                        prefixSb.append("[分享图片] ");
                    }
                    String snip = snippet(diary.getContent());
                    String finalSnippet;
                    if (snip == null || snip.isBlank()) {
                        finalSnippet = prefixSb.toString().trim();
                    } else {
                        finalSnippet = snip.trim() + (prefixSb.length() > 0 ? " " + prefixSb.toString().trim() : "");
                    }
                    return new DiarySearchResult.DiarySummary(
                            diary.getId(),
                            diary.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                            finalSnippet);
                })
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

    private Page<DiaryView> populateLikedByMe(Page<DiaryView> page, long userId) {
        if (page == null || page.getRecords() == null || page.getRecords().isEmpty()) {
            return page;
        }
        List<Long> diaryIds = page.getRecords().stream().map(DiaryView::id).toList();
        java.util.Set<Long> likedDiaryIds = new java.util.HashSet<>();
        String uid = String.valueOf(userId);
        for (Long diaryId : diaryIds) {
            if (Boolean.TRUE.equals(redisTemplate.opsForSet().isMember("resonance:" + diaryId, uid))) {
                likedDiaryIds.add(diaryId);
            }
        }
        List<DiaryView> newRecords = page.getRecords().stream()
                .map(view -> view.withLikedByMe(likedDiaryIds.contains(view.id())))
                .toList();
        page.setRecords(newRecords);
        return page;
    }

    public Page<DiaryView> publicDiaries(int page, int size) {
        int cappedPage = Math.max(1, page);
        int cappedSize = Math.min(50, Math.max(1, size));
        Long userId = currentUser().getId();
        String cacheKey = "public:diaries:%d:%d".formatted(cappedPage, cappedSize);

        Page<DiaryView> result = diaryCacheService.getCachedPage(cacheKey,
                () -> queryPublicDiaries(cappedPage, cappedSize));
        return populateLikedByMe(filterHiddenViews(result, userId), userId);
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

        Page<DiaryView> result = diaryCacheService.getCachedPage(cacheKey,
                () -> queryFollowingDiaries(userId, cappedPage, cappedSize));
        return populateLikedByMe(result, userId);
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
                    cachedReport = patchValenceArousal(cachedReport);
                    return withFreshness(cachedReport, userId, monthOffset, true);
                }
            } catch (Exception e) {
                log.debug("Cache read failed for {}", cacheKey, e);
            }

            WeeklyReportView dbReport = loadReportFromDb(userId, monthOffset, true);
            if (dbReport != null) {
                dbReport = patchValenceArousal(dbReport);
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
        List<AiAnalysisService.DiaryEntryContext> contents = new ArrayList<>();
        List<DiaryAnalysis> analyses = new ArrayList<>();
        Map<Long, DiaryAnalysisEntity> analysisMap = batchLoadAnalyses(
                diaries.stream().map(DiaryEntity::getId).toList());

        for (DiaryEntity diary : diaries) {
            contents.add(new AiAnalysisService.DiaryEntryContext(
                    diary.getCreatedAt().toLocalDate().format(DateTimeFormatter.ofPattern("MM-dd")),
                    diary.getContent()));
            DiaryAnalysisEntity analysisEntity = analysisMap.get(diary.getId());
            if (analysisEntity != null) {
                DiaryAnalysis analysis = new DiaryAnalysis(
                        analysisEntity.getMoodLabel(),
                        analysisEntity.getMoodIntensity(),
                        analysisEntity.getValence(),
                        analysisEntity.getArousal(),
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
                        analysis.valence() != null ? analysis.valence()
                                : AiAnalysisService.estimateValence(analysis.moodLabel(), analysis.moodIntensity()),
                        analysis.arousal() != null ? analysis.arousal()
                                : AiAnalysisService.estimateArousal(analysis.moodLabel(), analysis.moodIntensity()),
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
            aiSummary = aiAnalysisService.generateMonthlySummary(userId, contents, analyses, memCtx);
            AiAnalysisService.ReportGuidance guidance = aiAnalysisService.generateMonthlyGuidance(userId, contents, analyses);
            insights = guidance.insights();
            suggestions = guidance.suggestions();
            followUpPrompt = guidance.followUpPrompt();
            // 月度沉淀人生章节（时光画卷），异步执行不阻塞月报
            lifeChapterService.generateChapterForPeriod(userId, firstOfMonth, lastOfMonth);
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
                aiSummary != null ? LocalDateTime.now() : null,
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
                    cachedReport = patchValenceArousal(cachedReport);
                    return withFreshness(cachedReport, userId, weekOffset, false);
                }
            } catch (Exception e) {
                log.debug("Cache read failed for {}", cacheKey, e);
            }

            WeeklyReportView dbReport = loadReportFromDb(userId, weekOffset, false);
            if (dbReport != null) {
                dbReport = patchValenceArousal(dbReport);
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
        List<AiAnalysisService.DiaryEntryContext> contents = new ArrayList<>();
        List<DiaryAnalysis> analyses = new ArrayList<>();
        Map<Long, DiaryAnalysisEntity> analysisMap = batchLoadAnalyses(
                diaries.stream().map(DiaryEntity::getId).toList());

        for (DiaryEntity diary : diaries) {
            contents.add(new AiAnalysisService.DiaryEntryContext(
                    diary.getCreatedAt().toLocalDate().format(DateTimeFormatter.ofPattern("MM-dd")),
                    diary.getContent()));
            DiaryAnalysisEntity analysisEntity = analysisMap.get(diary.getId());
            if (analysisEntity != null) {
                DiaryAnalysis analysis = new DiaryAnalysis(
                        analysisEntity.getMoodLabel(),
                        analysisEntity.getMoodIntensity(),
                        analysisEntity.getValence(),
                        analysisEntity.getArousal(),
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
                        analysis.valence() != null ? analysis.valence()
                                : AiAnalysisService.estimateValence(analysis.moodLabel(), analysis.moodIntensity()),
                        analysis.arousal() != null ? analysis.arousal()
                                : AiAnalysisService.estimateArousal(analysis.moodLabel(), analysis.moodIntensity()),
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
            aiSummary = aiAnalysisService.generateWeeklySummary(userId, contents, analyses, memCtx);
            AiAnalysisService.ReportGuidance guidance = aiAnalysisService.generateWeeklyGuidance(userId, contents, analyses);
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
                aiSummary != null ? LocalDateTime.now() : null,
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

    private WeeklyReportView patchValenceArousal(WeeklyReportView report) {
        if (report == null || report.dailyMoods() == null || report.dailyMoods().isEmpty())
            return report;
        boolean needsPatch = report.dailyMoods().stream()
                .anyMatch(m -> m.valence() == null || m.arousal() == null);
        if (!needsPatch)
            return report;
        List<WeeklyReportView.DailyMood> patched = report.dailyMoods().stream()
                .map(m -> new WeeklyReportView.DailyMood(
                        m.date(), m.moodLabel(), m.moodIntensity(),
                        m.valence() != null ? m.valence()
                                : AiAnalysisService.estimateValence(m.moodLabel(), m.moodIntensity()),
                        m.arousal() != null ? m.arousal()
                                : AiAnalysisService.estimateArousal(m.moodLabel(), m.moodIntensity()),
                        m.diaryIds(), m.contentSnippet()))
                .toList();
        return new WeeklyReportView(
                report.weekLabel(), report.diaryCount(), patched, report.topicCounts(),
                report.moodDistribution(), report.moodDominantQuadrant(),
                report.positiveRatioPercent(), report.highEnergyRatioPercent(),
                report.aiSummary(), report.insights(), report.suggestions(),
                report.followUpPrompt(), report.generatedAt(), report.needsRegenerate());
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
        comment.setAuthorName(commenter.getNickname() != null ? commenter.getNickname() : commenter.getDisplayName());
        comment.setContent(ContentFilter.filter(content));
        comment.setIsDeleted(false);
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        diaryCommentMapper.insert(comment);

        String snippet = com.moodcopilot.common.TextSnippetUtil.generateSnippet(content, 30);

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

    private String toDiarySnippet(String content) {
        String snippet = com.moodcopilot.common.TextSnippetUtil.generateSnippet(content, 12);
        if (snippet.isEmpty()) {
            return "这篇日记";
        }
        return snippet;
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
        Map<Long, Integer> commentCountMap = batchLoadCommentCounts(ids);
        return diaries.stream()
                .map(diary -> buildFeedView(diary, isPublic, analysisMap, authorInfoMap,
                        likedDiaryIds.contains(diary.getId()), commentCountMap))
                .toList();
    }

    private DiaryView buildFeedView(DiaryEntity diary, boolean isPublic,
            Map<Long, DiaryAnalysisEntity> analysisMap, Map<Long, UserEntity> authorInfoMap,
            boolean likedByMe, Map<Long, Integer> commentCountMap) {
        DiaryAnalysisEntity analysis = analysisMap.get(diary.getId());
        Long viewerUserId = currentUser().getId();
        boolean isAuthorView = viewerUserId != null && viewerUserId.equals(diary.getAuthorUserId());
        UserEntity author = authorInfoMap.get(diary.getAuthorUserId());
        String authorName = author != null ? (author.getNickname() != null ? author.getNickname() : author.getDisplayName()) : diary.getAuthorName();
        String authorAvatar = author != null ? normalizeAvatar(author.getAvatar())
                : resolveAuthorAvatar(diary.getAuthorUserId());
        Integer authorLevel = author != null ? author.getLevel() : null;
        String authorRole = author != null ? author.getRole() : null;
        String feedContent = diary.getContent();
        int commentCount = commentCountMap.getOrDefault(diary.getId(), 0);
        return isPublic
                ? (isAuthorView
                        ? DiaryView.fromPublicFeedForAuthor(diary, analysis, authorName, authorAvatar, authorLevel,
                                authorRole,
                                likedByMe, feedContent, commentCount)
                        : DiaryView.fromPublicFeed(diary, analysis, authorName, authorAvatar, authorLevel, authorRole,
                                likedByMe, feedContent, commentCount))
                : DiaryView.fromFeed(diary, analysis, authorName, authorAvatar, authorLevel, authorRole, likedByMe,
                        feedContent, commentCount);
    }

    private DiaryView buildDiaryView(DiaryEntity diary, boolean isPublic) {
        DiaryAnalysisEntity analysis = findAnalysis(diary.getId());
        List<DiaryCommentEntity> comments = findComments(diary.getId());
        boolean likedByMe = Boolean.TRUE.equals(
                redisTemplate.opsForSet().isMember("resonance:" + diary.getId(),
                        String.valueOf(currentUser().getId())));
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
        String authorName = author != null ? (author.getNickname() != null ? author.getNickname() : author.getDisplayName()) : diary.getAuthorName();
        String authorAvatar = author != null
                ? normalizeAvatar(author.getAvatar())
                : resolveAuthorAvatar(diary.getAuthorUserId());
        Map<Long, String> commentAuthorNames = new java.util.HashMap<>();
        for (DiaryCommentEntity c : comments) {
            UserEntity cu = authorInfoMap.get(c.getAuthorUserId());
            commentAuthorNames.put(c.getAuthorUserId(),
                    cu != null ? (cu.getNickname() != null ? cu.getNickname() : cu.getDisplayName()) : c.getAuthorName());
        }
        Integer authorLevel = author != null ? author.getLevel() : null;
        String authorRole = author != null ? author.getRole() : null;
        Long viewerUserId = currentUser().getId();
        boolean isAuthorView = viewerUserId != null && viewerUserId.equals(diary.getAuthorUserId());
        return isPublic
                ? (isAuthorView
                        ? DiaryView.fromPublicForAuthor(diary, analysis, comments, authorAvatar, authorName,
                                authorLevel, authorRole,
                                commentAuthorNames, likedByMe)
                        : DiaryView.fromPublic(diary, analysis, comments, authorAvatar, authorName, authorLevel,
                                authorRole,
                                commentAuthorNames, likedByMe))
                : DiaryView.from(diary, analysis, comments, authorAvatar, authorName, authorLevel, authorRole,
                        commentAuthorNames, likedByMe);
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

    private Map<Long, Integer> batchLoadCommentCounts(List<Long> diaryIds) {
        if (diaryIds.isEmpty())
            return Map.of();
        List<DiaryCommentEntity> all = diaryCommentMapper.selectList(
                new LambdaQueryWrapper<DiaryCommentEntity>()
                        .in(DiaryCommentEntity::getDiaryId, diaryIds));
        Map<Long, Integer> result = new java.util.HashMap<>();
        for (Long id : diaryIds) {
            result.put(id, 0);
        }
        for (DiaryCommentEntity c : all) {
            result.merge(c.getDiaryId(), 1, Integer::sum);
        }
        return result;
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

        // 级联删除合集关联记录
        diaryCollectionRelationMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.moodcopilot.entity.DiaryCollectionRelationEntity>()
                        .eq(com.moodcopilot.entity.DiaryCollectionRelationEntity::getDiaryId, diaryId));

        markReportsStale(diary.getAuthorUserId());
        if ("PUBLIC".equals(diary.getVisibility())) {
            evictPublicDiaryCaches();
        }

        // Clean up knowledge graph
        java.util.List<com.moodcopilot.entity.DiaryKnowledgeGraphEntity> oldTriples = diaryKnowledgeGraphMapper
                .selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.moodcopilot.entity.DiaryKnowledgeGraphEntity>()
                                .eq(com.moodcopilot.entity.DiaryKnowledgeGraphEntity::getDiaryId, diaryId));
        for (com.moodcopilot.entity.DiaryKnowledgeGraphEntity old : oldTriples) {
            ragMemoryService.deleteKnowledgeGraph(old.getId());
        }
        if (!oldTriples.isEmpty()) {
            diaryKnowledgeGraphMapper.delete(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.moodcopilot.entity.DiaryKnowledgeGraphEntity>()
                            .eq(com.moodcopilot.entity.DiaryKnowledgeGraphEntity::getDiaryId, diaryId));
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
        return com.moodcopilot.common.TextSnippetUtil.generateSnippet(content, 500);
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
        List<AiAnalysisService.DiaryEntryContext> contents = new ArrayList<>();
        List<DiaryAnalysis> analyses = new ArrayList<>();
        Map<Long, DiaryAnalysisEntity> analysisMap = batchLoadAnalyses(
                recent.stream().map(DiaryEntity::getId).toList());
        for (DiaryEntity d : recent) {
            contents.add(new AiAnalysisService.DiaryEntryContext(
                    d.getCreatedAt().toLocalDate().format(DateTimeFormatter.ofPattern("MM-dd")), d.getContent()));
            DiaryAnalysisEntity a = analysisMap.get(d.getId());
            if (a != null)
                analyses.add(new DiaryAnalysis(a.getMoodLabel(), a.getMoodIntensity(),
                        a.getTopicLabelsJson(),
                        a.getSecondaryMoodsJson() != null ? a.getSecondaryMoodsJson() : List.of(),
                        a.getSummary(), a.getFeedback()));
            else
                analyses.add(null);
        }
        String suggestion = aiAnalysisService.generateCoaching(user.getId(), contents, analyses);
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
        return aiAnalysisService.generateEncouragements(currentUser().getId(), diary.getContent());
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
        String plainText = normalized.replaceAll("<[^>]*>", "");
        if (plainText.length() > 3000) {
            throw new ResponseStatusException(BAD_REQUEST, "日记内容不能超过 3000 字");
        }
        if (normalized.length() > 20000) {
            throw new ResponseStatusException(BAD_REQUEST, "日记格式过于复杂，总长度超限");
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

    private List<String> promoteImages(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty())
            return imageUrls;
        List<String> promoted = new ArrayList<>();
        for (String url : imageUrls) {
            try {
                promoted.add(ossService.promoteImage(url));
            } catch (Exception e) {
                log.error("图片转正失败，降级使用原 URL: {}", url, e);
                promoted.add(url);
            }
        }
        return promoted;

    }

    private List<DiaryImageMeta> normalizeImageMeta(List<DiaryImageMeta> imageMetaList, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return null;
        }

        java.util.Map<String, DiaryImageMeta> sourceByUrl = new java.util.LinkedHashMap<>();
        java.util.Map<String, DiaryImageMeta> sourceByObjectKey = new java.util.LinkedHashMap<>();
        java.util.List<DiaryImageMeta> sourceInOrder = new java.util.ArrayList<>();
        if (imageMetaList != null) {
            for (DiaryImageMeta source : imageMetaList) {
                if (source == null || source.getUrl() == null)
                    continue;
                String url = source.getUrl().trim();
                if (url.isEmpty())
                    continue;
                sourceByUrl.put(url, source);
                String objectKey = extractImageObjectKey(url);
                if (objectKey != null && !objectKey.isEmpty() && !sourceByObjectKey.containsKey(objectKey)) {
                    sourceByObjectKey.put(objectKey, source);
                }
                sourceInOrder.add(source);
            }
        }

        List<DiaryImageMeta> normalized = new java.util.ArrayList<>();
        int position = 0;
        for (String url : imageUrls) {
            if (url == null || url.isBlank())
                continue;
            DiaryImageMeta src = sourceByUrl.get(url.trim());
            if (src == null) {
                String objectKey = extractImageObjectKey(url);
                if (objectKey != null && !objectKey.isEmpty()) {
                    src = sourceByObjectKey.get(objectKey);
                }
            }
            if (src == null && position < sourceInOrder.size()) {
                // 保底按顺序兜底，避免 URL 转正后完全丢失通道信息。
                src = sourceInOrder.get(position);
            }
            DiaryImageMeta item = new DiaryImageMeta();
            // 隐私保护：库中不保存可直接回溯用户图片的 URL，仅保留压缩观测指标。
            item.setUrl(null);
            item.setChannel(normalizeChannel(src != null ? src.getChannel() : null));
            item.setOrigWidth(clampInt(src != null ? src.getOrigWidth() : null, 1, 20000));
            item.setOrigHeight(clampInt(src != null ? src.getOrigHeight() : null, 1, 20000));
            item.setCompressedWidth(clampInt(src != null ? src.getCompressedWidth() : null, 1, 20000));
            item.setCompressedHeight(clampInt(src != null ? src.getCompressedHeight() : null, 1, 20000));
            item.setOrigSize(clampLong(src != null ? src.getOrigSize() : null, 1L, 1024L * 1024L * 1024L));
            item.setCompressedSize(clampLong(src != null ? src.getCompressedSize() : null, 1L, 1024L * 1024L * 1024L));
            item.setQuality(clampDouble(src != null ? src.getQuality() : null, 0.0, 1.0));
            item.setMime(safeTrim(src != null ? src.getMime() : null, 64));
            normalized.add(item);
            position++;
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private String extractImageObjectKey(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        int queryIndex = trimmed.indexOf('?');
        if (queryIndex >= 0) {
            trimmed = trimmed.substring(0, queryIndex);
        }
        int hashIndex = trimmed.indexOf('#');
        if (hashIndex >= 0) {
            trimmed = trimmed.substring(0, hashIndex);
        }
        int slash = trimmed.lastIndexOf('/');
        if (slash < 0 || slash == trimmed.length() - 1) {
            return trimmed;
        }
        return trimmed.substring(slash + 1);
    }

    private void logImageCompressionSummary(long diaryId, List<DiaryImageMeta> imageMeta, List<String> images) {
        int imageCount = images == null ? 0 : images.size();
        if (imageCount == 0) {
            return;
        }
        if (imageMeta == null || imageMeta.isEmpty()) {
            log.info("图片压缩信息缺失 diaryId={} imageCount={}（可能是 legacy 图片或前端尚未上送 imageMeta）", diaryId, imageCount);
            return;
        }

        long totalOrig = 0L;
        long totalCompressed = 0L;
        int normalCount = 0;
        int textCount = 0;
        int legacyCount = 0;

        for (DiaryImageMeta item : imageMeta) {
            if (item == null)
                continue;
            String channel = item.getChannel();
            if ("normal".equals(channel)) {
                normalCount++;
            } else if ("text".equals(channel)) {
                textCount++;
            } else {
                legacyCount++;
            }
            if (item.getOrigSize() != null && item.getOrigSize() > 0) {
                totalOrig += item.getOrigSize();
            }
            if (item.getCompressedSize() != null && item.getCompressedSize() > 0) {
                totalCompressed += item.getCompressedSize();
            }
        }

        double savedRatio = totalOrig > 0
                ? ((double) (totalOrig - totalCompressed) / (double) totalOrig) * 100.0
                : 0.0;

        log.info(
                "图片压缩统计 diaryId={} imageCount={} metaCount={} channels(normal/text/legacy)={}/{}/{} orig={}KB compressed={}KB saved={}%%",
                diaryId,
                imageCount,
                imageMeta.size(),
                normalCount,
                textCount,
                legacyCount,
                totalOrig / 1024,
                totalCompressed / 1024,
                String.format(java.util.Locale.ROOT, "%.1f", savedRatio));
    }

    private String normalizeChannel(String channel) {
        if (channel == null)
            return "legacy";
        String normalized = channel.trim().toLowerCase();
        return switch (normalized) {
            case "text", "normal", "legacy" -> normalized;
            default -> "legacy";
        };
    }

    private Integer clampInt(Integer value, int min, int max) {
        if (value == null)
            return null;
        if (value < min)
            return min;
        return Math.min(value, max);
    }

    private Long clampLong(Long value, long min, long max) {
        if (value == null)
            return null;
        if (value < min)
            return min;
        return Math.min(value, max);
    }

    private Double clampDouble(Double value, double min, double max) {
        if (value == null)
            return null;
        if (value < min)
            return min;
        return Math.min(value, max);
    }

    private String safeTrim(String value, int maxLen) {
        if (value == null)
            return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty())
            return null;
        return trimmed.length() > maxLen ? trimmed.substring(0, maxLen) : trimmed;
    }

    private String truncateAnalysisError(String message, String fallback) {
        String value = message == null || message.isBlank() ? fallback : message.trim();
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }

    private boolean areSameImageList(List<String> a, List<String> b) {
        if (a == null || a.isEmpty()) {
            return b == null || b.isEmpty();
        }
        if (b == null || b.isEmpty()) {
            return false;
        }
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            String left = a.get(i) == null ? "" : a.get(i).trim();
            String right = b.get(i) == null ? "" : b.get(i).trim();
            if (!left.equals(right)) {
                return false;
            }
        }
        return true;
    }

    private String buildMemoryContext(long userId) {
        try {
            List<UserProfileMemoryEntity> memories = memoryExtractionService.listUserMemories(userId);
            if (memories == null || memories.isEmpty())
                return "";
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
