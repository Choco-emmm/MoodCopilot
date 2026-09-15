package com.moodcopilot.ai.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.ai.MemoryExtractionService;
import com.moodcopilot.ai.MemoryExtractionService.MemoryAttribute;
import com.moodcopilot.ai.MemoryOrchestrator;
import com.moodcopilot.ai.tool.ToolExecutionContext;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.entity.UserProfileMemoryEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SaveMemoryToolTest {

    private static final String ARGS = """
            {"attributeKey":"讨厌的食物","attributeValue":"番茄","memoryType":"preference","evidence":"我讨厌吃番茄"}
            """;

    private final MemoryOrchestrator orchestrator = mock(MemoryOrchestrator.class);
    private final MemoryExtractionService memoryExtractionService = mock(MemoryExtractionService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SaveMemoryTool tool = new SaveMemoryTool(orchestrator, memoryExtractionService);

    private static ToolExecutionContext context(long userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        Authentication auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
        return new ToolExecutionContext(auth, null, List.of(), "帮我记一下：我讨厌吃番茄", 32L, List.of("日记节选"));
    }

    private static UserProfileMemoryEntity memory(String key, String value) {
        UserProfileMemoryEntity memory = new UserProfileMemoryEntity();
        memory.setAttributeKey(key);
        memory.setAttributeValue(value);
        return memory;
    }

    @Test
    void thisToolAsksForApprovalAndNamesItselfAsExpected() {
        assertTrue(tool.requiresApproval());
        assertEquals("saveMemoryFunction", tool.name());
        assertEquals("saveMemory", tool.displayName());
    }

    @Test
    void previewReportsAnAdditionWhenTheKeyIsNew() throws Exception {
        when(orchestrator.current(7L)).thenReturn(List.of(memory("喜欢的音乐", "后摇")));

        Map<String, Object> preview = tool.approvalPreview(objectMapper, ARGS, context(7L));

        assertEquals("讨厌的食物", preview.get("attributeKey"));
        assertEquals("", preview.get("oldValue"));
        assertEquals("番茄", preview.get("newValue"));
        assertEquals("added", preview.get("kind"));
    }

    @Test
    void previewCarriesTheOldValueSoTheUserCanCompare() throws Exception {
        when(orchestrator.current(7L)).thenReturn(List.of(memory("讨厌的食物", "香菜")));

        Map<String, Object> preview = tool.approvalPreview(objectMapper, ARGS, context(7L));

        assertEquals("香菜", preview.get("oldValue"));
        assertEquals("番茄", preview.get("newValue"));
        assertEquals("updated", preview.get("kind"));
    }

    @Test
    void approvalWritesAFormalMemoryThroughTheExplicitPath() {
        when(memoryExtractionService.buildChatUserEvidence(anyString(), any())).thenReturn("用户消息：帮我记一下：我讨厌吃番茄");

        Object result = tool.execute(request(), context(7L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MemoryAttribute>> attributes = ArgumentCaptor.forClass(List.class);
        verify(orchestrator).processExtractedMemories(eq(7L), attributes.capture(), eq("explicit"), isNull(),
                eq(32L), eq("用户消息：帮我记一下：我讨厌吃番茄"), isNull());

        assertEquals(1, attributes.getValue().size());
        MemoryAttribute attribute = attributes.getValue().get(0);
        assertEquals("讨厌的食物", attribute.attributeKey());
        assertEquals("番茄", attribute.attributeValue());
        // 用户的确认动作本身就是明确声明，不走候选区
        assertEquals("explicit", attribute.assertionType());
        assertEquals("我讨厌吃番茄", attribute.evidence());

        assertTrue(((SaveMemoryTool.SaveMemoryResult) result).success());
    }

    @Test
    void theEvidenceFallsBackToTheUsersOwnWordsThisTurn() {
        // 模型给的 evidence 可能落空甚至瞎编，兜底必须只取用户自己的内容
        tool.execute(request(), context(7L));

        verify(memoryExtractionService).buildChatUserEvidence("帮我记一下：我讨厌吃番茄", List.of("日记节选"));
    }

    @Test
    void blankKeyOrValueIsRejectedWithoutTouchingTheStore() {
        var result = (SaveMemoryTool.SaveMemoryResult) tool.execute(
                new SaveMemoryTool.SaveMemoryRequest("  ", "番茄", "preference", "x"), context(7L));

        assertFalse(result.success());
        assertTrue(result.note().contains("不能为空"));
        verify(orchestrator, never()).processExtractedMemories(anyLong(), any(), anyString(), any(), any(), any(), any());
    }

    @Test
    void withoutALoggedInUserTheWriteIsRefused() {
        var result = (SaveMemoryTool.SaveMemoryResult) tool.execute(
                request(), ToolExecutionContext.of(null));

        assertFalse(result.success());
        assertTrue(result.note().contains("登录"));
        verify(orchestrator, never()).processExtractedMemories(anyLong(), any(), anyString(), any(), any(), any(), any());
    }

    @Test
    void aPreviewQueryFailurePropagatesSoTheRegistryCanDegrade() {
        when(orchestrator.current(7L)).thenThrow(new IllegalStateException("库挂了"));

        // 取旧值失败必须往上抛：由 ChatToolRegistry.approvalPreview 兜成「只说工具名」，
        // 在这里悄悄吞掉会让弹框显示错误的「新增」。
        IllegalStateException thrown = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> tool.approvalPreview(objectMapper, ARGS, context(7L)));
        assertEquals("库挂了", thrown.getMessage());
    }

    @Test
    void argumentsMissingAFieldStillProduceAUsablePreview() throws Exception {
        // 模型偶尔漏字段；预览只要不炸就行，严格的校验归 execute
        Map<String, Object> preview = tool.approvalPreview(objectMapper,
                "{\"attributeKey\":\"讨厌的食物\"}", context(7L));

        assertEquals("讨厌的食物", preview.get("attributeKey"));
        assertEquals("added", preview.get("kind"));
        assertNull(preview.get("newValue"));
    }

    private static SaveMemoryTool.SaveMemoryRequest request() {
        return new SaveMemoryTool.SaveMemoryRequest("讨厌的食物", "番茄", "preference", "我讨厌吃番茄");
    }
}
