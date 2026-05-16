package com.moodcopilot.summary;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.ai.AiAnalysisService;
import com.moodcopilot.diary.DiaryAnalysis;
import com.moodcopilot.diary.DiaryService;
import com.moodcopilot.diary.WeeklyReportView.DailyMood;
import com.moodcopilot.entity.*;
import com.moodcopilot.mapper.DiaryAnalysisMapper;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.mapper.DiarySummaryMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class SummaryService {

    private final DiarySummaryMapper summaryMapper;
    private final DiaryMapper diaryMapper;
    private final DiaryAnalysisMapper diaryAnalysisMapper;
    private final AiAnalysisService aiAnalysisService;
    private final ObjectMapper objectMapper;

    public SummaryService(DiarySummaryMapper summaryMapper,
                          DiaryMapper diaryMapper,
                          DiaryAnalysisMapper diaryAnalysisMapper,
                          AiAnalysisService aiAnalysisService,
                          ObjectMapper objectMapper) {
        this.summaryMapper = summaryMapper;
        this.diaryMapper = diaryMapper;
        this.diaryAnalysisMapper = diaryAnalysisMapper;
        this.aiAnalysisService = aiAnalysisService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SummaryView create(LocalDate startDate, LocalDate endDate) {
        UserEntity user = currentUser();

        List<DiaryEntity> diaries = diaryMapper.selectList(
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getAuthorUserId, user.getId())
                        .ge(DiaryEntity::getCreatedAt, startDate.atStartOfDay())
                        .le(DiaryEntity::getCreatedAt, endDate.atTime(LocalTime.MAX))
                        .orderByAsc(DiaryEntity::getCreatedAt)
        );

        if (diaries.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "该时间段内没有日记");
        }

        List<String> contents = new ArrayList<>();
        List<DiaryAnalysis> analyses = new ArrayList<>();
        List<DailyMood> dailyMoods = new ArrayList<>();
        List<Long> diaryIds = new ArrayList<>();
        Map<String, Integer> topicCounts = new LinkedHashMap<>();
        Map<Long, DiaryAnalysisEntity> analysisMap = diaryAnalysisMapper
                .selectBatchIds(diaries.stream().map(DiaryEntity::getId).toList())
                .stream()
                .collect(Collectors.toMap(DiaryAnalysisEntity::getDiaryId, analysis -> analysis));

        for (DiaryEntity diary : diaries) {
            contents.add(diary.getContent());
            diaryIds.add(diary.getId());
            DiaryAnalysisEntity analysisEntity = analysisMap.get(diary.getId());
            if (analysisEntity != null) {
                DiaryAnalysis a = new DiaryAnalysis(
                        analysisEntity.getMoodLabel(),
                        analysisEntity.getMoodIntensity(),
                        analysisEntity.getTopicLabelsJson(),
                        analysisEntity.getSummary(),
                        analysisEntity.getFeedback()
                );
                analyses.add(a);
                dailyMoods.add(new DailyMood(diary.getCreatedAt().toLocalDate(), a.moodLabel(), a.moodIntensity(), List.of(diary.getId()), DiaryService.snippet(diary.getContent())));
                for (String topic : a.topicLabels()) {
                    topicCounts.merge(topic, 1, Integer::sum);
                }
            } else {
                analyses.add(null);
            }
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("M/d");
        String title = startDate.format(fmt) + " - " + endDate.format(fmt);

        String aiSummary = aiAnalysisService.generateWeeklySummary(contents, analyses);
        AiAnalysisService.ReportGuidance guidance = aiAnalysisService.generateCustomGuidance(
                title, contents, analyses);

        var sortedTopics = topicCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));

        DiarySummaryEntity entity = new DiarySummaryEntity();
        entity.setUserId(user.getId());
        entity.setTitle(title);
        entity.setStartDate(startDate);
        entity.setEndDate(endDate);
        entity.setAiSummary(aiSummary);
        entity.setDiaryCount(diaries.size());
        try {
            entity.setInsightsJson(guidance.insights().isEmpty() ? null
                    : objectMapper.writeValueAsString(guidance.insights()));
            entity.setSuggestionsJson(guidance.suggestions().isEmpty() ? null
                    : objectMapper.writeValueAsString(guidance.suggestions()));
            entity.setFollowUpPrompt(guidance.followUpPrompt());
            entity.setMoodsJson(objectMapper.writeValueAsString(dailyMoods));
            entity.setTopicsJson(objectMapper.writeValueAsString(sortedTopics));
            entity.setDiaryIds(objectMapper.writeValueAsString(diaryIds));
        } catch (Exception ignored) {}
        summaryMapper.insert(entity);

        return SummaryView.from(entity, objectMapper);
    }

    public List<SummaryView> list() {
        UserEntity user = currentUser();
        return summaryMapper.selectList(
                new LambdaQueryWrapper<DiarySummaryEntity>()
                        .eq(DiarySummaryEntity::getUserId, user.getId())
                        .orderByDesc(DiarySummaryEntity::getCreatedAt)
        ).stream().map(e -> SummaryView.from(e, objectMapper)).toList();
    }

    @Transactional
    public void delete(long id) {
        UserEntity user = currentUser();
        DiarySummaryEntity entity = summaryMapper.selectById(id);
        if (entity == null || !entity.getUserId().equals(user.getId())) {
            throw new ResponseStatusException(NOT_FOUND, "总结不存在");
        }
        summaryMapper.deleteById(id);
    }

    private UserEntity currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserEntity user) {
            return user;
        }
        throw new ResponseStatusException(BAD_REQUEST, "用户未登录");
    }
}
