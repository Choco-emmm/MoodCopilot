package com.moodcopilot.summary;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodcopilot.ai.AiAnalysisService;
import com.moodcopilot.diary.DiaryAnalysis;
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
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class SummaryService {

    private final DiarySummaryMapper summaryMapper;
    private final DiaryMapper diaryMapper;
    private final DiaryAnalysisMapper diaryAnalysisMapper;
    private final AiAnalysisService aiAnalysisService;

    public SummaryService(DiarySummaryMapper summaryMapper,
                          DiaryMapper diaryMapper,
                          DiaryAnalysisMapper diaryAnalysisMapper,
                          AiAnalysisService aiAnalysisService) {
        this.summaryMapper = summaryMapper;
        this.diaryMapper = diaryMapper;
        this.diaryAnalysisMapper = diaryAnalysisMapper;
        this.aiAnalysisService = aiAnalysisService;
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
        for (DiaryEntity diary : diaries) {
            contents.add(diary.getContent());
            DiaryAnalysisEntity analysisEntity = diaryAnalysisMapper.selectById(diary.getId());
            if (analysisEntity != null) {
                analyses.add(new DiaryAnalysis(
                        analysisEntity.getMoodLabel(),
                        analysisEntity.getMoodIntensity(),
                        analysisEntity.getTopicLabelsJson(),
                        analysisEntity.getSummary(),
                        analysisEntity.getFeedback()
                ));
            } else {
                analyses.add(null);
            }
        }

        String aiSummary = aiAnalysisService.generateWeeklySummary(contents, analyses);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("M/d");
        String title = startDate.format(fmt) + " - " + endDate.format(fmt);

        DiarySummaryEntity entity = new DiarySummaryEntity();
        entity.setUserId(user.getId());
        entity.setTitle(title);
        entity.setStartDate(startDate);
        entity.setEndDate(endDate);
        entity.setAiSummary(aiSummary);
        summaryMapper.insert(entity);

        return SummaryView.from(entity);
    }

    public List<SummaryView> list() {
        UserEntity user = currentUser();
        return summaryMapper.selectList(
                new LambdaQueryWrapper<DiarySummaryEntity>()
                        .eq(DiarySummaryEntity::getUserId, user.getId())
                        .orderByDesc(DiarySummaryEntity::getCreatedAt)
        ).stream().map(SummaryView::from).toList();
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
