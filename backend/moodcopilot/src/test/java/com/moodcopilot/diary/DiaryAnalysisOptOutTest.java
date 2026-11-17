package com.moodcopilot.diary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.ai.AiAnalysisService;
import com.moodcopilot.ai.RagMemoryService;
import com.moodcopilot.ai.VisionService;
import com.moodcopilot.ai.mq.AiTaskProducer;
import com.moodcopilot.entity.DiaryEntity;
import com.moodcopilot.entity.DiaryAnalysisEntity;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.growth.UserGrowthService;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.mapper.DiaryAnalysisMapper;
import com.moodcopilot.mapper.DiaryCommentMapper;
import com.moodcopilot.mapper.UserMapper;
import com.moodcopilot.security.RateLimitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiaryAnalysisOptOutTest {
    @Mock DiaryMapper diaries;
    @Mock DiaryAnalysisMapper analyses;
    @Mock DiaryCommentMapper comments;
    @Mock UserMapper users;
    @Mock RagMemoryService rag;
    @Mock UserGrowthService growth;
    @Mock StringRedisTemplate redis;
    @Mock AiTaskProducer tasks;
    @Mock RateLimitService quotas;
    @Mock AiAnalysisService analysis;
    @Mock VisionService vision;
    @Mock TransactionTemplate transactions;
    @Mock org.springframework.context.ApplicationEventPublisher events;
    @Mock com.moodcopilot.event.LifeChapterService chapters;
    @InjectMocks DiaryService service;

    @BeforeEach void login() {
        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setDisplayName("测试用户");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
        lenient().when(diaries.selectOne(any())).thenAnswer(invocation -> diaries.selectById(31L));
        lenient().when(transactions.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });
    }

    @AfterEach void logout() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @ValueSource(strings = {"PUBLIC", "PRIVATE"})
    void optingOutPersistsAcrossCreateAndRead(String visibility) throws Exception {
        // Deserialize the same explicit false sent by the web editor.
        CreateDiaryRequest request = new ObjectMapper().readValue(
                "{\"content\":\"今天写一篇不需要分析的日记\",\"visibility\":\"" + visibility + "\",\"analyze\":false}",
                CreateDiaryRequest.class);
        doAnswer(invocation -> {
            DiaryEntity saved = invocation.getArgument(0);
            saved.setId(31L);
            return 1;
        }).when(diaries).insert(any(DiaryEntity.class));

        DiaryView response = new DiaryController(service, quotas).create(request).data();

        assertEquals("skipped_user", response.analysisStatus());
        var saved = org.mockito.ArgumentCaptor.forClass(DiaryEntity.class);
        verify(diaries).insert(saved.capture());
        assertEquals("skipped_user", saved.getValue().getAnalysisStatus(), "AI opt-out must survive the initial response");
        assertEquals("skipped_user", DiaryView.from(saved.getValue(), null, List.of(), null, "用户", java.util.Map.of(), false).analysisStatus());
        verifyNoInteractions(tasks, quotas, analysis, vision);
    }

    @Test void ownerFeedRetainsOptOutButPublicFeedDoesNotExposePrivateStatus() {
        DiaryEntity diary = diary();
        assertEquals("skipped_user", DiaryView.fromPublicFeedForAuthor(diary, null, "用户", null, null, null, false, diary.getContent(), 0).analysisStatus());
        assertEquals("skipped_user", DiaryView.fromFeed(diary, null, "用户", null, null, null, false, diary.getContent(), 0).analysisStatus());
        assertNull(DiaryView.fromPublicFeed(diary, null, "用户", null, null, null, false, diary.getContent(), 0).analysisStatus());
    }

    @Test void staleQueuedAnalysisCannotOverrideAnExplicitOptOut() {
        when(diaries.selectById(31L)).thenReturn(diary());
        lenient().doThrow(new AssertionError("Disabled diary reached the emotion analysis model"))
                .when(analysis).analyzeWithMemorySignals(anyLong(), anyString(), any(), any(), anyBoolean());
        service.runAiAnalysisSync(31L, 7L, false, "queued-before-opt-out");
        verifyNoInteractions(analysis, vision, tasks);
        verify(diaries, never()).updateById(any(DiaryEntity.class));
    }

    @Test void editingAndSharingWithoutAnalysisDoesNotReturnThePreviousAnalysis() {
        DiaryEntity diary = diary();
        diary.setVisibility("PRIVATE");
        diary.setAnalysisStatus("complete");
        when(diaries.selectById(31L)).thenReturn(diary);
        DiaryAnalysisEntity previous = new DiaryAnalysisEntity();
        previous.setDiaryId(31L);
        previous.setMoodIntensity(2);
        previous.setSummary("对旧正文的分析，不应展示成新正文的分析");
        var storedAnalysis = new java.util.concurrent.atomic.AtomicReference<>(previous);
        when(analyses.selectById(31L)).thenAnswer(invocation -> storedAnalysis.get());
        lenient().doAnswer(invocation -> { storedAnalysis.set(null); return 1; }).when(analyses).deleteById(31L);
        doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(new SimpleTransactionStatus());
            return null;
        }).when(transactions).executeWithoutResult(any());
        @SuppressWarnings("unchecked") SetOperations<String, String> sets = mock(SetOperations.class);
        when(redis.opsForSet()).thenReturn(sets);

        DiaryView response = service.updateDiary(31L, new UpdateDiaryRequest(
                "更新后的日记正文", "PUBLIC", null, null, null, null, false, false));

        assertEquals("skipped_user", response.analysisStatus());
        assertNull(response.analysis(), "the old analysis must not appear to analyse the revised public diary");
        assertEquals("skipped_user", diary.getAnalysisStatus(), "the edit opt-out must persist");
        verify(analyses).deleteById(31L);
        verify(tasks).cancelPendingDiaryAnalysisTasks(31L, 7L);
        verifyNoInteractions(quotas, analysis, vision);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sharingWithoutChangingContentRespectsTheAnalysisSwitch(boolean analyzeEnabled) {
        DiaryEntity diary = diary();
        diary.setVisibility("PRIVATE");
        diary.setAnalysisStatus("complete");
        when(diaries.selectById(31L)).thenReturn(diary);
        lenient().when(diaries.selectOne(any())).thenReturn(diary);
        DiaryAnalysisEntity previous = new DiaryAnalysisEntity();
        previous.setDiaryId(31L); previous.setMoodIntensity(2); previous.setSummary("已有分析");
        var stored = new java.util.concurrent.atomic.AtomicReference<>(previous);
        when(analyses.selectById(31L)).thenAnswer(invocation -> stored.get());
        lenient().doAnswer(invocation -> { stored.set(null); return 1; }).when(analyses).deleteById(31L);
        doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(new SimpleTransactionStatus()); return null;
        }).when(transactions).executeWithoutResult(any());
        @SuppressWarnings("unchecked") SetOperations<String, String> sets = mock(SetOperations.class);
        when(redis.opsForSet()).thenReturn(sets);
        DiaryView response = service.updateDiary(31L, new UpdateDiaryRequest(
                diary.getContent(), "PUBLIC", null, null, null, null, analyzeEnabled, false));
        if (analyzeEnabled) {
            assertNotNull(response.analysis());
            assertEquals("complete", response.analysisStatus());
            verifyNoInteractions(tasks, quotas, analysis, vision);
        } else {
            assertNull(response.analysis(), "explicit opt-out must clear the visible old analysis even for sharing only");
            assertEquals("skipped_user", response.analysisStatus());
            verify(tasks).cancelPendingDiaryAnalysisTasks(31L, 7L);
            verifyNoInteractions(quotas, analysis, vision);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void optingOutWhileTheModelIsRunningDiscardsItsResultOrFailure(boolean modelFails) {
        DiaryEntity diary = diary();
        diary.setAnalysisStatus("analyzing");
        when(diaries.selectById(31L)).thenReturn(diary);
        lenient().when(diaries.selectOne(any())).thenReturn(diary);
        when(analysis.analyzeWithMemorySignals(eq(7L), anyString(), isNull(), any(), eq(false))).thenAnswer(invocation -> {
            diary.setAnalysisStatus("skipped_user");
            if (modelFails) throw new IllegalStateException("old request failed after opting out");
            return new AiAnalysisService.DiaryAnalysisResult(new DiaryAnalysis("平静", 2, 0, 0, List.of(), List.of(), "过期结果", "过期反馈"), List.of());
        });
        assertDoesNotThrow(() -> service.runAiAnalysisSync(31L, 7L, false, "old-task"));
        verify(analyses, never()).insert(any(DiaryAnalysisEntity.class));
        verify(analyses, never()).updateById(any(DiaryAnalysisEntity.class));
        verifyNoInteractions(tasks);
        var updates = org.mockito.ArgumentCaptor.forClass(DiaryEntity.class);
        verify(diaries, atLeastOnce()).updateById(updates.capture());
        assertTrue(updates.getAllValues().stream().noneMatch(row -> "complete".equals(row.getAnalysisStatus()) || "failed".equals(row.getAnalysisStatus())));
    }

    @Test void currentEnabledAnalysisStillPersistsAndSchedulesPostProcessing() {
        DiaryEntity diary = diary();
        diary.setAnalysisStatus("analyzing");
        when(diaries.selectById(31L)).thenReturn(diary);
        when(analysis.analyzeWithMemorySignals(eq(7L), anyString(), isNull(), any(), eq(false)))
                .thenReturn(new AiAnalysisService.DiaryAnalysisResult(
                        new DiaryAnalysis("平静", 2, 0, 0, List.of(), List.of(), "当前分析", "当前反馈"), List.of()));
        service.runAiAnalysisSync(31L, 7L, false, "current-task");
        verify(analyses).insert(any(DiaryAnalysisEntity.class));
        verify(events).publishEvent(any(com.moodcopilot.ai.DiaryAnalysisCompletedEvent.class));
        verify(tasks).submitAnalysisPostProcessTasks(eq(31L), eq(7L), anyString(), eq("deepseek-v4-flash"), eq("current-task"));
    }

    @Test void changedUserInputInvalidatesTheModelResultWithoutPostProcessing() {
        DiaryEntity diary = diary();
        diary.setAnalysisStatus("analyzing");
        when(diaries.selectById(31L)).thenReturn(diary);
        when(analysis.analyzeWithMemorySignals(eq(7L), anyString(), isNull(), any(), eq(false))).thenAnswer(invocation -> {
            diary.setContent("模型运行中用户重新编辑的内容");
            return new AiAnalysisService.DiaryAnalysisResult(new DiaryAnalysis("平静", 2, List.of(), "过期分析", "过期反馈"), List.of());
        });
        service.runAiAnalysisSync(31L, 7L, false, "old-task");
        verifyNoInteractions(analyses, tasks, events, chapters);
    }

    private DiaryEntity diary() {
        DiaryEntity diary = new DiaryEntity();
        diary.setId(31L);
        diary.setAuthorUserId(7L);
        diary.setContent("今天写一篇不需要分析的日记");
        diary.setVisibility("PUBLIC");
        diary.setResonanceCount(0);
        diary.setIsDeleted(false);
        diary.setAnalysisStatus("skipped_user");
        return diary;
    }
}
