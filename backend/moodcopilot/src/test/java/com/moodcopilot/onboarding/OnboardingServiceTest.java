package com.moodcopilot.onboarding;

import com.moodcopilot.entity.DiaryAnalysisEntity;
import com.moodcopilot.entity.DiaryEntity;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.entity.UserOnboardingEntity;
import com.moodcopilot.mapper.DiaryAnalysisMapper;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.mapper.UserMapper;
import com.moodcopilot.mapper.UserOnboardingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock
    private UserOnboardingMapper userOnboardingMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private DiaryMapper diaryMapper;
    @Mock
    private DiaryAnalysisMapper diaryAnalysisMapper;

    private OnboardingService service;

    @BeforeEach
    void setup() {
        service = new OnboardingService(userOnboardingMapper, userMapper, diaryMapper, diaryAnalysisMapper);
    }

    @Test
    void getStateReturnsDefaultsForExistingUserWithNoRowsAndNoDiary() {
        when(userMapper.selectById(10L)).thenReturn(user(10L, 0));
        when(userOnboardingMapper.selectList(any())).thenReturn(List.of());
        when(userOnboardingMapper.existsActiveDiaryForUser(10L)).thenReturn(null);

        OnboardingStateView state = service.getState(10L, null);

        assertEquals(0, state.enrolledVersion());
        assertEquals(1, state.guideVersion());
        assertEquals("NOT_STARTED", state.welcomeStatus());
        assertEquals("NOT_STARTED", state.checklistStatus());
        assertEquals(5, state.tasks().size());
        assertEquals("NOT_STARTED", state.tasks().get("FIRST_DIARY"));
        assertEquals("NOT_STARTED", state.tasks().get("FIRST_ANALYSIS_VIEWED"));
        assertEquals("NOT_STARTED", state.tasks().get("FIRST_CHAT_REPLY"));
        assertEquals("NOT_STARTED", state.tasks().get("MEMORY_CENTER_VISITED"));
        assertEquals("NOT_STARTED", state.tasks().get("THEME_EXPLORED"));
        assertTrue(state.hints().isEmpty());
        verify(userOnboardingMapper, never()).upsertMonotonic(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void getStateBackfillsFirstDiaryEvenIfBestEffortUpsertThrows() {
        when(userMapper.selectById(11L)).thenReturn(user(11L, 1));
        when(userOnboardingMapper.selectList(any())).thenReturn(List.of());
        when(userOnboardingMapper.existsActiveDiaryForUser(11L)).thenReturn(1);
        doThrow(new RuntimeException("db timeout"))
                .when(userOnboardingMapper)
                .upsertMonotonic(eq(11L), eq("FIRST_DIARY"), eq(1), eq("GLOBAL"), eq("COMPLETED"), any(), any());

        OnboardingStateView state = service.getState(11L, "WEB");

        assertEquals(1, state.enrolledVersion());
        assertEquals("COMPLETED", state.tasks().get("FIRST_DIARY"));
        assertEquals("NOT_STARTED", state.tasks().get("FIRST_CHAT_REPLY"));
        assertEquals("NOT_STARTED", state.tasks().get("FIRST_ANALYSIS_VIEWED"));
    }

    @Test
    void getStateResolvesChecklistCompletedWhenAllFourCoreTasksDone() {
        when(userMapper.selectById(12L)).thenReturn(user(12L, 1));
        when(userOnboardingMapper.selectList(any())).thenReturn(List.of(
                row(12L, "STARTER_CHECKLIST_V1", "GLOBAL", "SKIPPED"),
                row(12L, "FIRST_DIARY", "GLOBAL", "COMPLETED"),
                row(12L, "FIRST_ANALYSIS_VIEWED", "GLOBAL", "COMPLETED"),
                row(12L, "FIRST_CHAT_REPLY", "GLOBAL", "COMPLETED"),
                row(12L, "MEMORY_CENTER_VISITED", "GLOBAL", "COMPLETED")
        ));

        OnboardingStateView state = service.getState(12L, null);

        assertEquals("COMPLETED", state.checklistStatus());
        assertEquals("NOT_STARTED", state.tasks().get("THEME_EXPLORED"));
        verify(userOnboardingMapper, never()).existsActiveDiaryForUser(12L);
    }

    @Test
    void getStateFiltersHintsByPlatformScopeAndRejectsInvalidScope() {
        when(userMapper.selectById(13L)).thenReturn(user(13L, 1));
        when(userOnboardingMapper.selectList(any())).thenReturn(List.of(
                row(13L, "ANALYSIS_READY", "GLOBAL", "COMPLETED"),
                row(13L, "PUBLIC_DIARY_WEB", "WEB", "SKIPPED")
        ));
        when(userOnboardingMapper.existsActiveDiaryForUser(13L)).thenReturn(null);

        OnboardingStateView allHints = service.getState(13L, null);
        assertEquals("COMPLETED", allHints.hints().get("ANALYSIS_READY"));
        assertEquals("SKIPPED", allHints.hints().get("PUBLIC_DIARY_WEB"));

        OnboardingStateView webHints = service.getState(13L, "WEB");
        assertEquals("COMPLETED", webHints.hints().get("ANALYSIS_READY"));
        assertEquals("SKIPPED", webHints.hints().get("PUBLIC_DIARY_WEB"));

        OnboardingStateView mpHints = service.getState(13L, "MINI_PROGRAM");
        assertEquals("COMPLETED", mpHints.hints().get("ANALYSIS_READY"));
        assertFalse(mpHints.hints().containsKey("PUBLIC_DIARY_WEB"));

        ResponseStatusException invalidScope = assertThrows(ResponseStatusException.class,
                () -> service.getState(13L, "GLOBAL"));
        assertEquals(HttpStatus.BAD_REQUEST, invalidScope.getStatusCode());

        ResponseStatusException bogusScope = assertThrows(ResponseStatusException.class,
                () -> service.getState(13L, "DESKTOP"));
        assertEquals(HttpStatus.BAD_REQUEST, bogusScope.getStatusCode());
    }

    @Test
    void recordEventHandlesWhitelistedEventsAndRejectsForgedOrInvalidInputs() {
        when(userMapper.selectById(20L)).thenReturn(user(20L, 1));
        when(userOnboardingMapper.selectList(any())).thenReturn(List.of());
        when(userOnboardingMapper.existsActiveDiaryForUser(20L)).thenReturn(null);

        service.recordEvent(20L, new OnboardingEventRequest("WELCOME_STARTED", null, null, null, 1));
        verify(userOnboardingMapper).upsertMonotonic(
                eq(20L), eq("WELCOME_V1"), eq(1), eq("GLOBAL"), eq("IN_PROGRESS"), isNull(), any());

        service.recordEvent(20L, new OnboardingEventRequest("WELCOME_COMPLETED", null, null, null, 1));
        verify(userOnboardingMapper).upsertMonotonic(
                eq(20L), eq("WELCOME_V1"), eq(1), eq("GLOBAL"), eq("COMPLETED"), any(), any());

        service.recordEvent(20L, new OnboardingEventRequest("MEMORY_CENTER_VISITED", null, null, null, null));
        verify(userOnboardingMapper).upsertMonotonic(
                eq(20L), eq("MEMORY_CENTER_VISITED"), eq(1), eq("GLOBAL"), eq("COMPLETED"), any(), any());

        service.recordEvent(20L, new OnboardingEventRequest("THEME_EXPLORED", null, null, null, null));
        verify(userOnboardingMapper).upsertMonotonic(
                eq(20L), eq("THEME_EXPLORED"), eq(1), eq("GLOBAL"), eq("COMPLETED"), any(), any());

        service.recordEvent(20L, new OnboardingEventRequest("HINT_SEEN", null, "PUBLIC_DIARY_WEB", "WEB", 1));
        verify(userOnboardingMapper).upsertMonotonic(
                eq(20L), eq("PUBLIC_DIARY_WEB"), eq(1), eq("WEB"), eq("COMPLETED"), any(), any());

        // Reject forging business tasks
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.recordEvent(20L, new OnboardingEventRequest("FIRST_DIARY", null, null, null, 1)))
                .getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.recordEvent(20L, new OnboardingEventRequest("FIRST_CHAT_REPLY", null, null, null, 1)))
                .getStatusCode());

        // Reject invalid version & cross-platform hint scope
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.recordEvent(20L, new OnboardingEventRequest("WELCOME_COMPLETED", null, null, null, 2)))
                .getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.recordEvent(20L, new OnboardingEventRequest("HINT_SEEN", null, "PUBLIC_DIARY_WEB", "MINI_PROGRAM", 1)))
                .getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.recordEvent(20L, new OnboardingEventRequest("HINT_SEEN", null, "PUBLIC_DIARY_WEB", "GLOBAL", 1)))
                .getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.recordEvent(20L, new OnboardingEventRequest("HINT_SEEN", null, "FIRST_DIARY", null, 1)))
                .getStatusCode());
    }

    @Test
    void globalHintAcceptsPlatformScopePersistsGlobalAndFiltersResponse() {
        when(userMapper.selectById(21L)).thenReturn(user(21L, 1));
        when(userOnboardingMapper.selectList(any())).thenReturn(List.of(
                row(21L, "ANALYSIS_READY", "GLOBAL", "COMPLETED"),
                row(21L, "MODEL_PRO", "GLOBAL", "SKIPPED"),
                row(21L, "PUBLIC_DIARY_WEB", "WEB", "COMPLETED")
        ));
        when(userOnboardingMapper.existsActiveDiaryForUser(21L)).thenReturn(null);

        OnboardingStateView afterEvent = service.recordEvent(
                21L, new OnboardingEventRequest("HINT_SEEN", null, "ANALYSIS_READY", "MINI_PROGRAM", 1));
        verify(userOnboardingMapper).upsertMonotonic(
                eq(21L), eq("ANALYSIS_READY"), eq(1), eq("GLOBAL"), eq("COMPLETED"), any(), any());
        assertEquals("COMPLETED", afterEvent.hints().get("ANALYSIS_READY"));
        assertFalse(afterEvent.hints().containsKey("PUBLIC_DIARY_WEB"));

        OnboardingStateView afterDismiss = service.dismiss(
                21L, new OnboardingDismissRequest("HINT", "MODEL_PRO", "MINI_PROGRAM", 1));
        verify(userOnboardingMapper).upsertMonotonic(
                eq(21L), eq("MODEL_PRO"), eq(1), eq("GLOBAL"), eq("SKIPPED"), isNull(), any());
        assertEquals("SKIPPED", afterDismiss.hints().get("MODEL_PRO"));
        assertFalse(afterDismiss.hints().containsKey("PUBLIC_DIARY_WEB"));
    }

    @Test
    void analysisViewedValidatesOwnershipAndRealAnalysisExistence() {
        // Missing diaryId -> 400
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.recordEvent(30L, new OnboardingEventRequest("ANALYSIS_VIEWED", null, null, null, 1)))
                .getStatusCode());

        // Non-existent diary -> 404
        when(diaryMapper.selectById(100L)).thenReturn(null);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.recordEvent(30L, new OnboardingEventRequest("ANALYSIS_VIEWED", 100L, null, null, 1)))
                .getStatusCode());

        // Another user's diary -> 403
        DiaryEntity otherDiary = diary(101L, 99L, "complete", false);
        when(diaryMapper.selectById(101L)).thenReturn(otherDiary);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.recordEvent(30L, new OnboardingEventRequest("ANALYSIS_VIEWED", 101L, null, null, 1)))
                .getStatusCode());

        // Owned diary with no analysis row -> 400
        DiaryEntity unanalyzed = diary(102L, 30L, "analyzing", false);
        when(diaryMapper.selectById(102L)).thenReturn(unanalyzed);
        when(diaryAnalysisMapper.selectById(102L)).thenReturn(null);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.recordEvent(30L, new OnboardingEventRequest("ANALYSIS_VIEWED", 102L, null, null, 1)))
                .getStatusCode());

        // Owned diary with skipped_user even if stale analysis row existed -> 400
        DiaryEntity skippedUser = diary(103L, 30L, "skipped_user", false);
        when(diaryMapper.selectById(103L)).thenReturn(skippedUser);
        when(diaryAnalysisMapper.selectById(103L)).thenReturn(new DiaryAnalysisEntity());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.recordEvent(30L, new OnboardingEventRequest("ANALYSIS_VIEWED", 103L, null, null, 1)))
                .getStatusCode());

        // Owned diary with real analysis and complete status -> succeeds
        DiaryEntity validDiary = diary(104L, 30L, "complete", false);
        DiaryAnalysisEntity analysis = new DiaryAnalysisEntity();
        analysis.setDiaryId(104L);
        when(diaryMapper.selectById(104L)).thenReturn(validDiary);
        when(diaryAnalysisMapper.selectById(104L)).thenReturn(analysis);
        when(userMapper.selectById(30L)).thenReturn(user(30L, 1));
        when(userOnboardingMapper.selectList(any())).thenReturn(List.of(
                row(30L, "FIRST_ANALYSIS_VIEWED", "GLOBAL", "COMPLETED")));
        when(userOnboardingMapper.existsActiveDiaryForUser(30L)).thenReturn(1);

        OnboardingStateView state = service.recordEvent(
                30L, new OnboardingEventRequest("ANALYSIS_VIEWED", 104L, null, null, 1));

        verify(userOnboardingMapper).upsertMonotonic(
                eq(30L), eq("FIRST_ANALYSIS_VIEWED"), eq(1), eq("GLOBAL"), eq("COMPLETED"), any(), any());
        assertEquals("COMPLETED", state.tasks().get("FIRST_ANALYSIS_VIEWED"));
    }

    @Test
    void dismissRecordsSkippedAndNeverForgesCompleted() {
        when(userMapper.selectById(40L)).thenReturn(user(40L, 1));
        when(userOnboardingMapper.selectList(any())).thenReturn(List.of(
                row(40L, "WELCOME_V1", "GLOBAL", "SKIPPED"),
                row(40L, "STARTER_CHECKLIST_V1", "GLOBAL", "SKIPPED"),
                row(40L, "THEME_EXPLORED", "GLOBAL", "SKIPPED"),
                row(40L, "MODEL_PRO", "GLOBAL", "SKIPPED")));
        when(userOnboardingMapper.existsActiveDiaryForUser(40L)).thenReturn(null);

        service.dismiss(40L, new OnboardingDismissRequest("WELCOME", null, null, 1));
        verify(userOnboardingMapper).upsertMonotonic(
                eq(40L), eq("WELCOME_V1"), eq(1), eq("GLOBAL"), eq("SKIPPED"), isNull(), any());

        service.dismiss(40L, new OnboardingDismissRequest("CHECKLIST", null, null, 1));
        verify(userOnboardingMapper).upsertMonotonic(
                eq(40L), eq("STARTER_CHECKLIST_V1"), eq(1), eq("GLOBAL"), eq("SKIPPED"), isNull(), any());

        service.dismiss(40L, new OnboardingDismissRequest("TASK", "THEME_EXPLORED", null, 1));
        verify(userOnboardingMapper).upsertMonotonic(
                eq(40L), eq("THEME_EXPLORED"), eq(1), eq("GLOBAL"), eq("SKIPPED"), isNull(), any());

        OnboardingStateView state = service.dismiss(40L, new OnboardingDismissRequest("HINT", "MODEL_PRO", "GLOBAL", 1));
        verify(userOnboardingMapper).upsertMonotonic(
                eq(40L), eq("MODEL_PRO"), eq(1), eq("GLOBAL"), eq("SKIPPED"), isNull(), any());
        assertEquals("SKIPPED", state.welcomeStatus());
        assertEquals("SKIPPED", state.checklistStatus());
        assertEquals("SKIPPED", state.tasks().get("THEME_EXPLORED"));
        assertEquals("NOT_STARTED", state.tasks().get("FIRST_DIARY"));
        assertEquals("SKIPPED", state.hints().get("MODEL_PRO"));

        // Invalid dismiss requests rejected
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.dismiss(40L, new OnboardingDismissRequest("TASK", "WELCOME_V1", null, 1)))
                .getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.dismiss(40L, new OnboardingDismissRequest("HINT", "PUBLIC_DIARY_WEB", "MINI_PROGRAM", 1)))
                .getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.dismiss(40L, new OnboardingDismissRequest("UNKNOWN", null, null, 1)))
                .getStatusCode());
    }

    private static UserEntity user(Long id, Integer enrolledVersion) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setOnboardingEnrolledVersion(enrolledVersion);
        return user;
    }

    private static DiaryEntity diary(Long id, Long authorUserId, String analysisStatus, boolean deleted) {
        DiaryEntity diary = new DiaryEntity();
        diary.setId(id);
        diary.setAuthorUserId(authorUserId);
        diary.setAnalysisStatus(analysisStatus);
        diary.setIsDeleted(deleted);
        return diary;
    }

    private static UserOnboardingEntity row(Long userId, String key, String scope, String status) {
        UserOnboardingEntity entity = new UserOnboardingEntity();
        entity.setUserId(userId);
        entity.setGuideKey(key);
        entity.setGuideVersion(1);
        entity.setScope(scope);
        entity.setStatus(status);
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }
}
