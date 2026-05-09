package com.moodcopilot.diary;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moodcopilot.ai.AiAnalysisService;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
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
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DiaryService {

    private final DiaryMapper diaryMapper;
    private final DiaryAnalysisMapper diaryAnalysisMapper;
    private final DiaryCommentMapper diaryCommentMapper;
    private final DiaryResonanceMapper diaryResonanceMapper;
    private final AiAnalysisService aiAnalysisService;
    private final NotificationService notificationService;
    private final FollowService followService;

    public DiaryService(DiaryMapper diaryMapper,
                        DiaryAnalysisMapper diaryAnalysisMapper,
                        DiaryCommentMapper diaryCommentMapper,
                        DiaryResonanceMapper diaryResonanceMapper,
                        AiAnalysisService aiAnalysisService,
                        NotificationService notificationService,
                        FollowService followService) {
        this.diaryMapper = diaryMapper;
        this.diaryAnalysisMapper = diaryAnalysisMapper;
        this.diaryCommentMapper = diaryCommentMapper;
        this.diaryResonanceMapper = diaryResonanceMapper;
        this.aiAnalysisService = aiAnalysisService;
        this.notificationService = notificationService;
        this.followService = followService;
    }

    @Transactional
    public DiaryView create(CreateDiaryRequest request) {
        String content = normalizeContent(request.content());
        DiaryVisibility visibility = parseVisibility(request.visibility());

        DiaryEntity diary = new DiaryEntity();
        UserEntity user = currentUser();
        diary.setAuthorUserId(user.getId());
        diary.setAuthorName(user.getDisplayName());
        diary.setContent(content);
        diary.setVisibility(visibility.name());
        diary.setResonanceCount(0);
        diary.setIsDeleted(false);
        diary.setCreatedAt(LocalDateTime.now());
        diary.setUpdatedAt(LocalDateTime.now());
        diaryMapper.insert(diary);

        return DiaryView.from(diary, List.of());
    }

    @Async
    @Transactional
    public void runAiAnalysis(long diaryId, String content) {
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

    public List<DiaryView> myDiaries() {
        List<DiaryEntity> diaries = diaryMapper.selectList(
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getAuthorUserId, currentUser().getId())
                        .orderByDesc(DiaryEntity::getCreatedAt)
        );
        return diaries.stream().map(this::toDiaryView).toList();
    }

    public Page<DiaryView> publicDiaries(int page, int size) {
        int cappedPage = Math.max(1, page);
        int cappedSize = Math.min(50, Math.max(1, size));
        Page<DiaryEntity> entityPage = diaryMapper.selectPage(
                Page.of(cappedPage, cappedSize),
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getVisibility, "PUBLIC")
                        .orderByDesc(DiaryEntity::getCreatedAt)
        );
        List<DiaryView> views = entityPage.getRecords().stream().map(this::toDiaryView).toList();
        Page<DiaryView> viewPage = new Page<>(cappedPage, cappedSize, entityPage.getTotal());
        viewPage.setRecords(views);
        return viewPage;
    }

    public DiaryView get(long id) {
        DiaryEntity diary = findDiary(id);
        DiaryAnalysisEntity analysis = findAnalysis(id);
        List<DiaryCommentEntity> comments = findComments(id);
        return DiaryView.from(diary, analysis, comments);
    }

    public List<DiaryView> similar(long id, int limit) {
        DiaryEntity source = findDiary(id);
        DiaryAnalysisEntity sourceAnalysis = findAnalysis(id);

        List<DiaryEntity> publicDiaries = diaryMapper.selectList(
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getVisibility, "PUBLIC")
                        .ne(DiaryEntity::getId, id)
        );

        int cappedLimit = Math.max(1, Math.min(limit, 10));

        return publicDiaries.stream()
                .sorted(Comparator
                        .comparingInt((DiaryEntity d) -> similarityScore(sourceAnalysis, d)).reversed()
                        .thenComparing(DiaryEntity::getCreatedAt, Comparator.reverseOrder()))
                .limit(cappedLimit)
                .map(this::toDiaryView)
                .toList();
    }

    public Page<DiaryView> followingDiaries(int page, int size) {
        List<Long> followingIds = followService.getFollowingIds(currentUser().getId());
        if (followingIds.isEmpty()) {
            Page<DiaryView> empty = new Page<>(page, size, 0);
            empty.setRecords(List.of());
            return empty;
        }

        int cappedPage = Math.max(1, page);
        int cappedSize = Math.min(50, Math.max(1, size));
        Page<DiaryEntity> entityPage = diaryMapper.selectPage(
                Page.of(cappedPage, cappedSize),
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getVisibility, "PUBLIC")
                        .in(DiaryEntity::getAuthorUserId, followingIds)
                        .orderByDesc(DiaryEntity::getCreatedAt)
        );
        List<DiaryView> views = entityPage.getRecords().stream().map(this::toDiaryView).toList();
        Page<DiaryView> viewPage = new Page<>(cappedPage, cappedSize, entityPage.getTotal());
        viewPage.setRecords(views);
        return viewPage;
    }

    public WeeklyReportView weeklyReport(int weekOffset) {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(DayOfWeek.MONDAY).plusWeeks(weekOffset);
        LocalDate sunday = monday.plusDays(6);

        LocalDateTime start = monday.atStartOfDay();
        LocalDateTime end = sunday.atTime(LocalTime.MAX);

        List<DiaryEntity> diaries = diaryMapper.selectList(
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getAuthorUserId, currentUser().getId())
                        .ge(DiaryEntity::getCreatedAt, start)
                        .le(DiaryEntity::getCreatedAt, end)
                        .orderByAsc(DiaryEntity::getCreatedAt)
        );

        List<WeeklyReportView.DailyMood> dailyMoods = new ArrayList<>();
        Map<String, Integer> topicCounts = new LinkedHashMap<>();
        List<String> contents = new ArrayList<>();
        List<DiaryAnalysis> analyses = new ArrayList<>();

        for (DiaryEntity diary : diaries) {
            contents.add(diary.getContent());
            DiaryAnalysisEntity analysisEntity = findAnalysis(diary.getId());
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
                        analysis.moodIntensity()
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
        comment.setContent(content);
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
        return DiaryView.from(diary, analysis, comments);
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
        return DiaryView.from(diary, analysis, comments);
    }

    private DiaryView toDiaryView(DiaryEntity diary) {
        DiaryAnalysisEntity analysis = diaryAnalysisMapper.selectById(diary.getId());
        List<DiaryCommentEntity> comments = diaryCommentMapper.selectList(
                new LambdaQueryWrapper<DiaryCommentEntity>()
                        .eq(DiaryCommentEntity::getDiaryId, diary.getId())
                        .orderByAsc(DiaryCommentEntity::getCreatedAt)
        );
        return DiaryView.from(diary, analysis, comments);
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

    private int similarityScore(DiaryAnalysisEntity sourceAnalysis, DiaryEntity target) {
        if (sourceAnalysis == null) return 0;
        DiaryAnalysisEntity targetAnalysis = diaryAnalysisMapper.selectById(target.getId());
        if (targetAnalysis == null) return 0;
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
