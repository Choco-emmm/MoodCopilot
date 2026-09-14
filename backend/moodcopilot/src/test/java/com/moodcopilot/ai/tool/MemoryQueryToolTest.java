package com.moodcopilot.ai.tool;

import com.moodcopilot.ai.MemoryExtractionService;
import com.moodcopilot.ai.MemoryQueryRequest;
import com.moodcopilot.ai.MemoryQueryResult;
import com.moodcopilot.ai.RagMemoryService;
import com.moodcopilot.ai.tool.impl.MemoryQueryTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class MemoryQueryToolTest {

    private final MemoryQueryTool tool = new MemoryQueryTool(
            mock(MemoryExtractionService.class), mock(RagMemoryService.class));

    @Test
    void referencesOmitSnippetToAvoidDuplicatingTheKey() {
        // 前端模板是 {{ ref.snippet || ref.value }}；若这里带上 snippet = "key: value"，
        // 会渲染成「【key】key: value」。所以画像条目刻意不发 snippet。
        MemoryQueryResult result = new MemoryQueryResult(1,
                List.of(new MemoryQueryResult.MemoryItem("工作压力", "项目排期太紧", "2026-01-01T00:00:00")),
                "已返回长期画像条目");

        List<Map<String, String>> items = tool.references(result);

        assertEquals(1, items.size());
        Map<String, String> item = items.get(0);
        assertFalse(item.containsKey("snippet"), "profile_memory items must not carry a snippet");
        assertEquals("profile_memory", item.get("type"));
        assertEquals("工作压力", item.get("key"));
        assertEquals("项目排期太紧", item.get("value"));
        assertEquals("memoryQuery", item.get("toolName"));
    }

    @Test
    void referencesAreEmptyForAnEmptyResult() {
        MemoryQueryResult result = new MemoryQueryResult(0, List.of(), "当前暂无符合条件的长期画像条目");

        assertTrue(tool.references(result).isEmpty());
    }

    @Test
    void referencesTolerateUnknownResultTypes() {
        assertTrue(tool.references(null).isEmpty());
        assertTrue(tool.references("not-a-memory-query-result").isEmpty());
    }

    @Test
    void requestTypeIsTheDeclaredRecord() {
        assertEquals(MemoryQueryRequest.class, tool.requestType());
        assertEquals("memoryQueryFunction", tool.name());
        assertEquals("memoryQuery", tool.displayName());
    }
}
