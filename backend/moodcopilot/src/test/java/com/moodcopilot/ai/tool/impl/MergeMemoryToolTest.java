package com.moodcopilot.ai.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.ai.MemoryExtractionService;
import com.moodcopilot.ai.MemoryOrchestrator;
import com.moodcopilot.ai.tool.ToolExecutionContext;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.entity.UserProfileMemoryEntity;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MergeMemoryToolTest {
    private final MemoryOrchestrator orchestrator = mock(MemoryOrchestrator.class);
    private final MergeMemoryTool tool = new MergeMemoryTool(orchestrator, mock(MemoryExtractionService.class));

    private static ToolExecutionContext context() {
        UserEntity user = new UserEntity();
        user.setId(7L);
        return new ToolExecutionContext(
                new UsernamePasswordAuthenticationToken(user, null, List.of()),
                null, List.of(), "把这两条合并一下", 34L, List.of());
    }

    private static UserProfileMemoryEntity memory(long id, String key, String value) {
        UserProfileMemoryEntity memory = new UserProfileMemoryEntity();
        memory.setId(id);
        memory.setAttributeKey(key);
        memory.setAttributeValue(value);
        return memory;
    }

    private static MergeMemoryTool.MergeMemoryRequest request(List<Long> sources, String key, String value) {
        return new MergeMemoryTool.MergeMemoryRequest(sources, key, value, "preference", "这俩重复了");
    }

    @Test
    void requiresApprovalAndDeclaresMemoryIds() {
        assertTrue(tool.requiresApproval());
        assertEquals("mergeMemoryFunction", tool.name());
        assertEquals("mergeMemory", tool.displayName());
        assertTrue(tool.required().contains("sourceIds"));
        assertFalse(tool.properties().containsKey("sourceKeys"));
    }

    @Test
    void previewUsesOnlyTheCurrentUsersRequestedMemories() throws Exception {
        when(orchestrator.current(7L)).thenReturn(List.of(
                memory(11L, "不喜欢吃的东西", "番茄"),
                memory(12L, "饮食禁忌", "香菜"),
                memory(13L, "喜欢的音乐", "后摇")));
        Map<String, Object> preview = tool.approvalPreview(new ObjectMapper(), """
                {"sourceIds":[11,12,999],"targetKey":"讨厌的食物",
                 "targetValue":"番茄、香菜","memoryType":"preference","evidence":"这俩重复了"}
                """, context());
        assertEquals("讨厌的食物", preview.get("attributeKey"));
        assertEquals("番茄、香菜", preview.get("newValue"));
        assertEquals("merged", preview.get("kind"));
        assertEquals(List.of(
                Map.of("attributeKey", "不喜欢吃的东西", "value", "番茄"),
                Map.of("attributeKey", "饮食禁忌", "value", "香菜")), preview.get("sources"));
        verify(orchestrator, atLeastOnce()).current(7L);
    }

    @Test
    void mergeDelegatesIdsEvidenceAndConversationToTheAuthoritativeOrchestrator() {
        var result = (MergeMemoryTool.MergeMemoryResult) tool.execute(
                request(List.of(11L, 12L), " 讨厌的食物 ", "番茄、香菜"), context());
        assertTrue(result.success());
        assertEquals("讨厌的食物", result.targetKey());
        assertEquals(List.of(11L, 12L), result.purgedIds());
        verify(orchestrator).mergeMemoriesByIds(7L, List.of(11L, 12L), "讨厌的食物", "番茄、香菜",
                "preference", false, "这俩重复了", 34L);
        verifyNoMoreInteractions(orchestrator);
    }

    @Test
    void nullSourcesArePassedAsAnEmptyListWithAnExplicitNote() {
        var result = (MergeMemoryTool.MergeMemoryResult) tool.execute(
                request(null, "讨厌的食物", "番茄"), context());
        assertTrue(result.success());
        assertTrue(result.purgedIds().isEmpty());
        assertNotNull(result.note());
        verify(orchestrator).mergeMemoriesByIds(7L, List.of(), "讨厌的食物", "番茄",
                "preference", false, "这俩重复了", 34L);
    }

    @Test
    void blankTargetOrValueNeverTouchesTheStore() {
        for (var request : List.of(request(List.of(11L), " ", "番茄"),
                request(List.of(11L), "讨厌的食物", " "))) {
            var result = (MergeMemoryTool.MergeMemoryResult) tool.execute(request, context());
            assertFalse(result.success());
            assertTrue(result.note().contains("不能为空"));
        }
        verifyNoInteractions(orchestrator);
    }

    @Test
    void withoutALoggedInUserTheMergeIsRefused() {
        var result = (MergeMemoryTool.MergeMemoryResult) tool.execute(
                request(List.of(11L), "讨厌的食物", "番茄"), ToolExecutionContext.of(null));
        assertFalse(result.success());
        assertTrue(result.note().contains("登录"));
        verifyNoInteractions(orchestrator);
    }

    @Test
    void orchestratorFailureCannotBeReportedAsSuccessful() {
        when(orchestrator.mergeMemoriesByIds(anyLong(), anyList(), anyString(), anyString(),
                anyString(), anyBoolean(), anyString(), anyLong())).thenThrow(new IllegalStateException("merge failed"));
        assertThrows(IllegalStateException.class,
                () -> tool.execute(request(List.of(11L), "讨厌的食物", "番茄"), context()));
    }
}
