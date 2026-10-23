package com.moodcopilot.ai.tool.impl;

import com.moodcopilot.ai.*;
import com.moodcopilot.ai.tool.ToolExecutionContext;
import com.moodcopilot.entity.UserProfileMemoryEntity;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemoryQueryToolTest {
    @Test void retrievalReturnsOwnedCurrentIdsAndDatabaseValuesInsteadOfStaleVectorText() {
        var extraction = mock(MemoryExtractionService.class);
        var rag = mock(RagMemoryService.class);
        var memory = new UserProfileMemoryEntity(); memory.setId(22L); memory.setUserId(7L);
        memory.setAttributeKey("饮品偏好"); memory.setAttributeValue("喜欢茶"); memory.setStatus("active");
        when(extraction.listCurrentUserMemories()).thenReturn(List.of(memory));
        when(rag.search(7L, "饮品", 20, RagMemoryService.SOURCE_PROFILE)).thenReturn(List.of(
                new RagMemoryService.RagHit("旧值咖啡", 1D, "profile:7:22", null, "profile"),
                new RagMemoryService.RagHit("其他用户", 1D, "profile:8:22", null, "profile"),
                new RagMemoryService.RagHit("已经删除", 1D, "profile:21", null, "profile")));
        var user = new com.moodcopilot.entity.UserEntity(); user.setId(7L);
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(user, null, List.of());
        var result = (MemoryQueryResult)new MemoryQueryTool(extraction, rag)
                .execute(new MemoryQueryRequest("饮品", 20), ToolExecutionContext.of(auth));
        assertEquals(1, result.count()); assertEquals(22L, result.items().get(0).id());
        assertEquals("喜欢茶", result.items().get(0).attributeValue());
    }
}
