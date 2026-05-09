package com.moodcopilot.diary;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodcopilot.entity.DiaryAnalysisEntity;
import com.moodcopilot.entity.DiaryCommentEntity;
import com.moodcopilot.entity.DiaryEntity;
import com.moodcopilot.entity.DiaryResonanceEntity;
import com.moodcopilot.mapper.DiaryAnalysisMapper;
import com.moodcopilot.mapper.DiaryCommentMapper;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.mapper.DiaryResonanceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DiaryService {
    private static final long CURRENT_USER_ID = 1001L;
    private static final String CURRENT_USER_NAME = "我";

    private final DiaryMapper diaryMapper;
    private final DiaryAnalysisMapper diaryAnalysisMapper;
    private final DiaryCommentMapper diaryCommentMapper;
    private final DiaryResonanceMapper diaryResonanceMapper;

    public DiaryService(DiaryMapper diaryMapper,
                        DiaryAnalysisMapper diaryAnalysisMapper,
                        DiaryCommentMapper diaryCommentMapper,
                        DiaryResonanceMapper diaryResonanceMapper) {
        this.diaryMapper = diaryMapper;
        this.diaryAnalysisMapper = diaryAnalysisMapper;
        this.diaryCommentMapper = diaryCommentMapper;
        this.diaryResonanceMapper = diaryResonanceMapper;
    }

    @Transactional
    public DiaryView create(CreateDiaryRequest request) {
        String content = normalizeContent(request.content());
        DiaryVisibility visibility = parseVisibility(request.visibility());
        DiaryAnalysis analysis = analyze(content);

        DiaryEntity diary = new DiaryEntity();
        diary.setAuthorUserId(CURRENT_USER_ID);
        diary.setAuthorName(CURRENT_USER_NAME);
        diary.setContent(content);
        diary.setVisibility(visibility.name());
        diary.setResonanceCount(0);
        diary.setIsDeleted(false);
        diary.setCreatedAt(LocalDateTime.now());
        diary.setUpdatedAt(LocalDateTime.now());
        diaryMapper.insert(diary);

        DiaryAnalysisEntity analysisEntity = new DiaryAnalysisEntity();
        analysisEntity.setDiaryId(diary.getId());
        analysisEntity.setMoodLabel(analysis.moodLabel());
        analysisEntity.setMoodIntensity(analysis.moodIntensity());
        analysisEntity.setTopicLabelsJson(analysis.topicLabels());
        analysisEntity.setSummary(analysis.summary());
        analysisEntity.setFeedback(analysis.feedback());
        analysisEntity.setCreatedAt(LocalDateTime.now());
        analysisEntity.setUpdatedAt(LocalDateTime.now());
        diaryAnalysisMapper.insert(analysisEntity);

        return DiaryView.from(diary, analysisEntity, List.of());
    }

    public List<DiaryView> myDiaries() {
        List<DiaryEntity> diaries = diaryMapper.selectList(
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getAuthorUserId, CURRENT_USER_ID)
                        .orderByDesc(DiaryEntity::getCreatedAt)
        );
        return diaries.stream().map(this::toDiaryView).toList();
    }

    public List<DiaryView> publicDiaries() {
        List<DiaryEntity> diaries = diaryMapper.selectList(
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getVisibility, "PUBLIC")
                        .orderByDesc(DiaryEntity::getCreatedAt)
        );
        return diaries.stream().map(this::toDiaryView).toList();
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

    @Transactional
    public DiaryView addComment(long diaryId, CreateCommentRequest request) {
        DiaryEntity diary = findPublicDiary(diaryId);
        String content = normalizeContent(request.content());

        DiaryCommentEntity comment = new DiaryCommentEntity();
        comment.setDiaryId(diaryId);
        comment.setAuthorUserId(CURRENT_USER_ID);
        comment.setAuthorName(CURRENT_USER_NAME);
        comment.setContent(content);
        comment.setIsDeleted(false);
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        diaryCommentMapper.insert(comment);

        DiaryAnalysisEntity analysis = findAnalysis(diaryId);
        List<DiaryCommentEntity> comments = findComments(diaryId);
        return DiaryView.from(diary, analysis, comments);
    }

    @Transactional
    public DiaryView resonate(long diaryId) {
        DiaryEntity diary = findPublicDiary(diaryId);

        boolean exists = diaryResonanceMapper.exists(
                new LambdaQueryWrapper<DiaryResonanceEntity>()
                        .eq(DiaryResonanceEntity::getDiaryId, diaryId)
                        .eq(DiaryResonanceEntity::getUserId, CURRENT_USER_ID)
        );
        if (!exists) {
            DiaryResonanceEntity resonance = new DiaryResonanceEntity();
            resonance.setDiaryId(diaryId);
            resonance.setUserId(CURRENT_USER_ID);
            resonance.setCreatedAt(LocalDateTime.now());
            diaryResonanceMapper.insert(resonance);

            diary.setResonanceCount(diary.getResonanceCount() + 1);
            diary.setUpdatedAt(LocalDateTime.now());
            diaryMapper.updateById(diary);
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
        DiaryAnalysisEntity analysis = diaryAnalysisMapper.selectById(diaryId);
        if (analysis == null) {
            throw new ResponseStatusException(NOT_FOUND, "日记分析不存在");
        }
        return analysis;
    }

    private List<DiaryCommentEntity> findComments(long diaryId) {
        return diaryCommentMapper.selectList(
                new LambdaQueryWrapper<DiaryCommentEntity>()
                        .eq(DiaryCommentEntity::getDiaryId, diaryId)
                        .orderByAsc(DiaryCommentEntity::getCreatedAt)
        );
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

    // ── AI Analysis (keyword-based, will be replaced by DeepSeek in Phase 3) ──

    private DiaryAnalysis analyze(String content) {
        String mood = pickMood(content);
        List<String> topics = pickTopics(content);
        return new DiaryAnalysis(
                mood,
                intensity(content, mood),
                topics,
                summarize(content),
                feedbackFor(mood, topics)
        );
    }

    private String pickMood(String content) {
        if (containsAny(content, "焦虑", "担心", "紧张", "害怕", "慌")) return "焦虑";
        if (containsAny(content, "委屈", "难过", "想哭", "失落", "孤单")) return "委屈";
        if (containsAny(content, "生气", "烦", "愤怒", "讨厌")) return "烦躁";
        if (containsAny(content, "累", "疲惫", "困", "撑", "压力", "崩溃")) return "疲惫";
        if (containsAny(content, "开心", "高兴", "舒服", "期待", "安心")) return "轻松";
        return "平静";
    }

    private List<String> pickTopics(String content) {
        List<String> topics = new ArrayList<>();
        if (containsAny(content, "朋友", "同事", "家人", "关系", "聊天", "争吵", "误会")) topics.add("人际关系");
        if (containsAny(content, "工作", "加班", "任务", "项目", "考试", "学习", "上课")) topics.add("工作学习");
        if (containsAny(content, "睡", "失眠", "身体", "头痛", "胃", "运动")) topics.add("睡眠身体");
        if (containsAny(content, "自己", "未来", "目标", "坚持", "改变")) topics.add("自我成长");
        if (topics.isEmpty()) topics.add("日常情绪");
        return topics;
    }

    private int intensity(String content, String mood) {
        int base = switch (mood) {
            case "焦虑", "委屈", "烦躁" -> 3;
            case "疲惫" -> 2;
            default -> 1;
        };
        int extra = containsAny(content, "很", "特别", "一直", "真的", "崩溃") ? 1 : 0;
        return Math.min(5, base + extra);
    }

    private String summarize(String content) {
        String compact = content.replaceAll("\\s+", " ");
        if (compact.length() <= 48) return compact;
        return compact.substring(0, 48) + "...";
    }

    private String feedbackFor(String mood, List<String> topics) {
        String topic = topics.get(0);
        return switch (mood) {
            case "焦虑" -> "你正在承受一些不确定感，可以先把最小的一步从脑子里拿出来。";
            case "委屈" -> "这份委屈值得被看见，先不用急着替别人解释一切。";
            case "烦躁" -> "烦躁可能是在提醒你边界被挤压了，给自己留一点缓冲。";
            case "疲惫" -> "今天已经消耗了你不少能量，休息不是退后，是在保护自己。";
            case "轻松" -> "这份轻松很珍贵，可以记住让你感觉被托住的细节。";
            default -> "这是一段关于" + topic + "的日常波动，慢慢记录会更看清自己的节奏。";
        };
    }

    private int similarityScore(DiaryAnalysisEntity sourceAnalysis, DiaryEntity target) {
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

    private boolean containsAny(String content, String... keywords) {
        for (String keyword : keywords) {
            if (content.contains(keyword)) return true;
        }
        return false;
    }
}
