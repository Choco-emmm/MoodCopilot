package com.moodcopilot.ai.tool;

import com.moodcopilot.ai.tool.impl.ListRecentDiariesTool;
import com.moodcopilot.ai.tool.impl.ListRecentDiariesTool.RecentDiaryListRequest;
import com.moodcopilot.diary.DiarySearchRequest;
import com.moodcopilot.diary.DiarySearchResult;
import com.moodcopilot.diary.DiaryService;
import com.moodcopilot.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListRecentDiariesToolTest {

    private final DiaryService diaryService = mock(DiaryService.class);
    private final ListRecentDiariesTool tool = new ListRecentDiariesTool(diaryService);

    private static ToolExecutionContext contextFor(long userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        Authentication auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
        return new ToolExecutionContext(auth, null, List.of());
    }

    private static DiarySearchResult summaries(int returned, String note) {
        List<DiarySearchResult.DiarySummary> items = new ArrayList<>();
        for (int i = 1; i <= returned; i++) {
            items.add(new DiarySearchResult.DiarySummary((long) i, "2026-11-17 10:00:00", "第 " + i + " 篇摘要"));
        }
        return new DiarySearchResult(null, null, null, items.size(), items, note);
    }

    @Test
    void passesEmptyKeywordAndRequestedLimitToService() {
        when(diaryService.searchOwnDiarySummaries(anyLong(), any(), anyInt())).thenReturn(summaries(3, "ok"));

        tool.execute(new RecentDiaryListRequest(7, null, null), contextFor(7L));

        // keyword 为 null 是核心：服务层据此退化成「该用户按时间倒序的日记」
        verify(diaryService).searchOwnDiarySummaries(eq(7L), eq(new DiarySearchRequest(null, null, null)), eq(7));
    }

    @Test
    void passesDateRangeThrough() {
        when(diaryService.searchOwnDiarySummaries(anyLong(), any(), anyInt())).thenReturn(summaries(1, "ok"));
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 17);

        tool.execute(new RecentDiaryListRequest(5, from, to), contextFor(7L));

        verify(diaryService).searchOwnDiarySummaries(eq(7L), eq(new DiarySearchRequest(null, from, to)), eq(5));
    }

    @Test
    void defaultsToTenWhenLimitMissing() {
        when(diaryService.searchOwnDiarySummaries(anyLong(), any(), anyInt())).thenReturn(summaries(1, "ok"));

        tool.execute(new RecentDiaryListRequest(null, null, null), contextFor(7L));

        verify(diaryService).searchOwnDiarySummaries(anyLong(), any(), eq(10));
    }

    @Test
    void capsLimitAtFifty() {
        when(diaryService.searchOwnDiarySummaries(anyLong(), any(), anyInt())).thenReturn(summaries(1, "ok"));

        tool.execute(new RecentDiaryListRequest(999, null, null), contextFor(7L));

        verify(diaryService).searchOwnDiarySummaries(anyLong(), any(), eq(50));
    }

    @Test
    void clampsNonPositiveLimitToOne() {
        when(diaryService.searchOwnDiarySummaries(anyLong(), any(), anyInt())).thenReturn(summaries(1, "ok"));

        tool.execute(new RecentDiaryListRequest(0, null, null), contextFor(7L));

        verify(diaryService).searchOwnDiarySummaries(anyLong(), any(), eq(1));
    }

    @Test
    void returnsServiceNoteAndTotalUnchanged() {
        String note = "匹配 30 篇，本次返回最近 20 篇（按时间倒序）。还有更早的记录未返回，如需完整回顾请缩小时间范围再查。";
        when(diaryService.searchOwnDiarySummaries(anyLong(), any(), anyInt()))
                .thenReturn(new DiarySearchResult(null, null, null, 30, summaries(20, note).diaries(), note));

        DiarySearchResult result = (DiarySearchResult) tool.execute(
                new RecentDiaryListRequest(20, null, null), contextFor(7L));

        // total 是真实匹配总数，不是本次返回条数 —— 模型据此判断有没有漏
        assertEquals(30, result.total());
        assertEquals(20, result.diaries().size());
        assertEquals(note, result.note());
    }

    @Test
    void emptyResultKeepsServiceNote() {
        when(diaryService.searchOwnDiarySummaries(anyLong(), any(), anyInt()))
                .thenReturn(summaries(0, "未找到符合条件的历史日记"));

        DiarySearchResult result = (DiarySearchResult) tool.execute(
                new RecentDiaryListRequest(null, null, null), contextFor(7L));

        assertEquals(0, result.total());
        assertTrue(result.diaries().isEmpty());
        assertTrue(result.note().contains("未找到"));
    }

    @Test
    void isReadOnlyAndRequiresNoArguments() {
        assertFalse(tool.requiresApproval(), "只读工具不应触发审批");
        assertTrue(tool.required().isEmpty(), "全部参数可选，模型可以零参数调用");
        assertEquals("listRecentDiariesFunction", tool.name());
        assertEquals("listRecentDiaries", tool.displayName());
    }
}
