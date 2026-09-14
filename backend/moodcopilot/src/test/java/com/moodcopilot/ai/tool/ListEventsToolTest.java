package com.moodcopilot.ai.tool;

import com.moodcopilot.ai.tool.impl.ListEventsTool;
import com.moodcopilot.ai.tool.impl.ListEventsTool.EventItem;
import com.moodcopilot.ai.tool.impl.ListEventsTool.ListEventsRequest;
import com.moodcopilot.ai.tool.impl.ListEventsTool.ListEventsResult;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.event.LifeEventService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListEventsToolTest {

    private final LifeEventService lifeEventService = mock(LifeEventService.class);
    private final ListEventsTool tool = new ListEventsTool(lifeEventService);

    private static ToolExecutionContext contextFor(long userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        Authentication auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
        return new ToolExecutionContext(auth, null, List.of());
    }

    private static LifeEventService.LifeEventView event(long id, String title, String status, String targetDate) {
        return new LifeEventService.LifeEventView(id, title, "描述", targetDate, null, null, null, status,
                List.of(), 0, null, null, null, null, null, null, null, 0, null, false, BigDecimal.ONE);
    }

    @Test
    void listsEveryEventWhenKeywordIsBlank() {
        when(lifeEventService.listUserEvents(42L)).thenReturn(List.of(
                event(7L, "参加蓝桥杯竞赛", "PENDING", "2026-09-07"),
                event(9L, "期末考", "FOLLOWED_UP", "2026-09-06")));

        ListEventsResult result = (ListEventsResult) tool.execute(new ListEventsRequest(""), contextFor(42L));

        verify(lifeEventService).listUserEvents(42L);
        assertEquals(2, result.count());
        assertEquals(7L, result.events().get(0).id());
        assertEquals("参加蓝桥杯竞赛", result.events().get(0).title());
        assertEquals("PENDING", result.events().get(0).status());
        assertEquals("2026-09-07", result.events().get(0).targetDate());
        // 提示里必须点名 updateEventStatusFunction，模型才知道下一步怎么用这个 id
        assertTrue(result.note().contains("updateEventStatusFunction"));
    }

    @Test
    void keywordNarrowsByTitleAndStillExposesTheId() {
        when(lifeEventService.listUserEvents(anyLong())).thenReturn(List.of(
                event(7L, "参加蓝桥杯竞赛", "PENDING", "2026-09-07"),
                event(9L, "期末考", "PENDING", "2026-09-06")));

        ListEventsResult result = (ListEventsResult) tool.execute(new ListEventsRequest("蓝桥杯"), contextFor(42L));

        assertEquals(1, result.count());
        assertEquals(7L, result.events().get(0).id());
    }

    @Test
    void noMatchIsAnEmptyResultWithAnExplanation() {
        when(lifeEventService.listUserEvents(anyLong())).thenReturn(List.of());

        ListEventsResult result = (ListEventsResult) tool.execute(new ListEventsRequest("不存在的事"), contextFor(42L));

        assertEquals(0, result.count());
        assertTrue(result.events().isEmpty());
        assertTrue(result.note().contains("不存在的事"));
    }

    @Test
    void blankDescriptionBecomesNullRatherThanEmptyString() {
        LifeEventService.LifeEventView blank = new LifeEventService.LifeEventView(1L, "标题", "   ", null,
                null, null, null, "PENDING", List.of(), 0, null, null, null, null, null, null, null, 0, null,
                false, null);
        when(lifeEventService.listUserEvents(anyLong())).thenReturn(List.of(blank));

        ListEventsResult result = (ListEventsResult) tool.execute(new ListEventsRequest(""), contextFor(42L));

        EventItem item = result.events().get(0);
        assertNull(item.description());
        assertNull(item.targetDate());
    }

    @Test
    void wireNameAndDisplayNameFollowTheConvention() {
        assertEquals("listEventsFunction", tool.name());
        assertEquals("listEvents", tool.displayName());
    }

    @Test
    void referencesExposeTheEventIdForTheFrontendPanel() {
        ListEventsResult result = new ListEventsResult(2, List.of(
                new EventItem(7L, "参加蓝桥杯竞赛", "PENDING", "2026-09-07", null),
                new EventItem(9L, "期末考", "FOLLOWED_UP", "2026-09-06", null)),
                "已返回 2 个事件");

        List<Map<String, String>> items = tool.references(result);

        assertEquals(2, items.size());
        Map<String, String> first = items.get(0);
        assertEquals("event_memory", first.get("type"));
        assertEquals("7", first.get("eventId"));
        assertEquals("参加蓝桥杯竞赛", first.get("title"));
        assertEquals("PENDING", first.get("status"));
        assertEquals("2026-09-07", first.get("date"));
        assertEquals("listEvents", first.get("toolName"));
        assertTrue(first.get("snippet").contains("待跟进"));
        assertTrue(items.get(1).get("snippet").contains("已跟进"));
    }

    @Test
    void eventReferencesMustNotCarryADiaryId() {
        // 前端「N 条记录」按「有 diaryId 就计入日记记忆」过滤，带上会把事件串进日记计数
        ListEventsResult result = new ListEventsResult(1,
                List.of(new EventItem(7L, "参加蓝桥杯竞赛", "PENDING", "2026-09-07", null)), "已返回 1 个事件");

        Map<String, String> item = tool.references(result).get(0);

        assertFalse(item.containsKey("diaryId"), "event_memory items must not carry a diaryId");
    }

    @Test
    void referencesTolerateUnexpectedResults() {
        assertTrue(tool.references(null).isEmpty());
        assertTrue(tool.references("not-a-list-events-result").isEmpty());
    }
}
