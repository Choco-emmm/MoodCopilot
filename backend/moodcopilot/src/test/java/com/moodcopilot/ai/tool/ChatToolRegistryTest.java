package com.moodcopilot.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.ai.GraphSearchRequest;
import com.moodcopilot.ai.MemoryExtractionService;
import com.moodcopilot.ai.RagMemoryService;
import com.moodcopilot.ai.VisionService;
import com.moodcopilot.ai.tool.impl.DiaryImageAnalysisTool;
import com.moodcopilot.ai.tool.impl.DiarySearchTool;
import com.moodcopilot.ai.tool.impl.GraphSearchTool;
import com.moodcopilot.ai.tool.impl.ListEventsTool;
import com.moodcopilot.ai.tool.impl.MemoryQueryTool;
import com.moodcopilot.ai.tool.impl.ReadDiaryTool;
import com.moodcopilot.ai.tool.impl.ReadImageTextTool;
import com.moodcopilot.ai.tool.impl.ReportSnapshotTool;
import com.moodcopilot.ai.tool.impl.UpdateEventStatusTool;
import com.moodcopilot.ai.tool.impl.UserStatsTool;
import com.moodcopilot.diary.DiaryService;
import com.moodcopilot.diary.UserStatsRequest;
import com.moodcopilot.event.LifeEventService;
import com.moodcopilot.mapper.DiaryKnowledgeGraphMapper;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.security.RateLimitService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ChatToolRegistryTest {

    private static final List<String> EXPECTED_NAMES = List.of(
            "diarySearchFunction",
            "readDiaryFunction",
            "userStatsFunction",
            "reportSnapshotFunction",
            "memoryQueryFunction",
            "graphSearchFunction",
            "diaryImageAnalysisFunction",
            "readImageTextFunction",
            "listEventsFunction",
            "updateEventStatusFunction");

    private final DiaryService diaryService = mock(DiaryService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private ChatToolRegistry registry() {
        return new ChatToolRegistry(objectMapper, List.of(
                new DiarySearchTool(diaryService, mock(RagMemoryService.class)),
                new ReadDiaryTool(mock(DiaryMapper.class)),
                new UserStatsTool(diaryService),
                new ReportSnapshotTool(diaryService),
                new MemoryQueryTool(mock(MemoryExtractionService.class), mock(RagMemoryService.class)),
                new GraphSearchTool(mock(DiaryKnowledgeGraphMapper.class), mock(RagMemoryService.class)),
                new DiaryImageAnalysisTool(mock(DiaryMapper.class), mock(VisionService.class),
                        mock(RateLimitService.class)),
                new ReadImageTextTool(mock(VisionService.class)),
                new ListEventsTool(mock(LifeEventService.class)),
                new UpdateEventStatusTool(mock(LifeEventService.class))));
    }

    @Test
    void toolsDeclareExpectedWireNamesInOrder() {
        assertEquals(EXPECTED_NAMES, registry().tools().stream().map(ChatTool::name).toList());
    }

    @Test
    void displayNameDropsTheFunctionSuffix() {
        List<String> displayNames = registry().tools().stream().map(ChatTool::displayName).toList();
        assertEquals(List.of("diarySearch", "readDiary", "userStats", "reportSnapshot", "memoryQuery",
                "graphSearch", "diaryImageAnalysis", "readImageText", "listEvents", "updateEventStatus"),
                displayNames);
    }

    @Test
    void schemasUseTheOpenAiFunctionEnvelope() {
        List<Map<String, Object>> schemas = registry().schemas();
        assertEquals(EXPECTED_NAMES.size(), schemas.size());

        for (Map<String, Object> schema : schemas) {
            assertEquals("function", schema.get("type"));

            @SuppressWarnings("unchecked")
            Map<String, Object> function = (Map<String, Object>) schema.get("function");
            assertTrue(EXPECTED_NAMES.contains(function.get("name")), "unexpected name " + function.get("name"));
            assertNotNull(function.get("description"));
            assertEquals(Boolean.TRUE, function.get("strict"));

            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = (Map<String, Object>) function.get("parameters");
            assertEquals("object", parameters.get("type"));
            assertEquals(Boolean.FALSE, parameters.get("additionalProperties"));
            assertTrue(parameters.get("properties") instanceof Map);
            assertTrue(parameters.get("required") instanceof List);
        }
    }

    @Test
    void schemasCarryTheExpectedRequiredLists() {
        Map<String, List<String>> requiredByName = new java.util.LinkedHashMap<>();
        for (Map<String, Object> schema : registry().schemas()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> function = (Map<String, Object>) schema.get("function");
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = (Map<String, Object>) function.get("parameters");
            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) parameters.get("required");
            requiredByName.put((String) function.get("name"), required);
        }

        assertEquals(List.of("keyword", "startDate", "endDate"), requiredByName.get("diarySearchFunction"));
        assertEquals(List.of("diaryId"), requiredByName.get("readDiaryFunction"));
        assertEquals(List.of("days"), requiredByName.get("userStatsFunction"));
        assertEquals(List.of("period", "offset"), requiredByName.get("reportSnapshotFunction"));
        assertEquals(List.of("keyword", "limit"), requiredByName.get("memoryQueryFunction"));
        assertEquals(List.of("keyword", "limit"), requiredByName.get("graphSearchFunction"));
        assertEquals(List.of("diaryIds", "prompt"), requiredByName.get("diaryImageAnalysisFunction"));
        assertEquals(List.of("focus"), requiredByName.get("readImageTextFunction"));
        assertEquals(List.of("keyword"), requiredByName.get("listEventsFunction"));
        assertEquals(List.of("eventId", "status", "note"), requiredByName.get("updateEventStatusFunction"));
    }

    @Test
    void graphSearchUsesASingleWireName() {
        // 收敛前 flash 注册成 graphSearch、schema 与执行器里写 graphSearchFunction，这里断言已统一
        assertEquals("graphSearchFunction", com.moodcopilot.ai.GraphSearchFunctionSupport.NAME);
        assertTrue(EXPECTED_NAMES.contains(com.moodcopilot.ai.GraphSearchFunctionSupport.NAME));
    }

    @Test
    void executeParsesArgumentsAndDispatchesToTheMatchingTool() throws Exception {
        ToolExecutionContext context = ToolExecutionContext.of(null);

        registry().execute("userStatsFunction", "{\"days\":7}", context);

        ArgumentCaptor<UserStatsRequest> captor = ArgumentCaptor.forClass(UserStatsRequest.class);
        verify(diaryService).getOwnMoodStats(captor.capture());
        assertEquals(7, captor.getValue().days());
    }

    @Test
    void blankArgumentsParseAsAnEmptyObjectInsteadOfFailing() throws Exception {
        ToolExecutionContext context = ToolExecutionContext.of(null);

        registry().execute("userStatsFunction", "", context);
        registry().execute("userStatsFunction", null, context);

        ArgumentCaptor<UserStatsRequest> captor = ArgumentCaptor.forClass(UserStatsRequest.class);
        verify(diaryService, org.mockito.Mockito.times(2)).getOwnMoodStats(captor.capture());
        captor.getAllValues().forEach(request -> assertNull(request.days()));
    }

    @Test
    void unknownToolNameIsRejected() {
        ChatToolRegistry registry = registry();

        assertThrows(IllegalArgumentException.class,
                () -> registry.execute("nope", "{}", ToolExecutionContext.of(null)));
        assertThrows(IllegalArgumentException.class, () -> registry.description("nope"));
    }

    @Test
    void executeParsedSkipsJsonRoundTrip() throws Exception {
        ToolExecutionContext context = ToolExecutionContext.of(null);

        registry().executeParsed("userStatsFunction", new UserStatsRequest(3), context);

        ArgumentCaptor<UserStatsRequest> captor = ArgumentCaptor.forClass(UserStatsRequest.class);
        verify(diaryService).getOwnMoodStats(captor.capture());
        assertEquals(3, captor.getValue().days());
    }

    @Test
    void toolsWithoutReferencesProduceNoEventItems() {
        ChatToolRegistry registry = registry();
        assertEquals(List.of(), registry.references("userStatsFunction", new Object()));
        assertEquals(List.of(), registry.references("reportSnapshotFunction", new Object()));
        assertEquals(List.of(), registry.references("updateEventStatusFunction", new Object()));
    }

    @Test
    void graphReferencesAreCompactedAndNonBlank() {
        // 前端图卡要求 snippet 非空，160 字符截断不能把它变成空串
        String longContent = "头部 关系 尾部 " + "很长的内容".repeat(60);
        var result = new com.moodcopilot.ai.GraphSearchResult(1,
                List.of(new com.moodcopilot.ai.GraphSearchResult.GraphItem(longContent, "2026-01-01", 5L)),
                "已返回");

        List<Map<String, String>> items = registry().references("graphSearchFunction", result);

        assertEquals(1, items.size());
        String snippet = items.get(0).get("snippet");
        assertFalse(snippet.isBlank());
        assertTrue(snippet.length() <= 161, "snippet should be compacted, was " + snippet.length());
        assertEquals("graphSearch", items.get(0).get("toolName"));
        assertEquals("5", items.get(0).get("diaryId"));
    }

    @Test
    void emitIsSilentWithoutASink() {
        // 非流式路径不传 sseSink，不应该抛异常
        registry().emit("userStatsFunction", new Object(), null);
        registry().emit("userStatsFunction", null, null);
    }

    @Test
    void registryRejectsDuplicateToolNames() {
        ChatTool<?> duplicated = new UserStatsTool(diaryService);
        assertThrows(IllegalStateException.class,
                () -> new ChatToolRegistry(objectMapper, List.of(duplicated, duplicated)));
    }
}
