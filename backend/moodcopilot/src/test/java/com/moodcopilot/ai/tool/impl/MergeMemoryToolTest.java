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
import org.mockito.InOrder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MergeMemoryToolTest {

    private static final String ARGS = """
            {"sourceKeys":["不喜欢吃的东西","饮食禁忌"],"targetKey":"讨厌的食物",
             "targetValue":"番茄、香菜","memoryType":"preference","evidence":"这俩重复了"}
            """;

    private final MemoryOrchestrator orchestrator = mock(MemoryOrchestrator.class);
    private final MemoryExtractionService memoryExtractionService = mock(MemoryExtractionService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MergeMemoryTool tool = new MergeMemoryTool(orchestrator, memoryExtractionService);

    private static ToolExecutionContext context(long userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        Authentication auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
        return new ToolExecutionContext(auth, null, List.of(), "把这两条合并一下", 34L, List.of());
    }

    private static UserProfileMemoryEntity memory(String key, String value) {
        UserProfileMemoryEntity memory = new UserProfileMemoryEntity();
        memory.setAttributeKey(key);
        memory.setAttributeValue(value);
        return memory;
    }

    private static MergeMemoryTool.MergeMemoryRequest request(List<String> sources, String targetKey) {
        return new MergeMemoryTool.MergeMemoryRequest(sources, targetKey, "番茄、香菜", "preference", "这俩重复了");
    }

    @Test
    void thisToolAsksForApprovalAndNamesItselfAsExpected() {
        assertTrue(tool.requiresApproval());
        assertEquals("mergeMemoryFunction", tool.name());
        assertEquals("mergeMemory", tool.displayName());
    }

    @Test
    void previewListsEverySourceSoTheUserSeesWhatDisappears() throws Exception {
        when(orchestrator.current(7L)).thenReturn(List.of(
                memory("不喜欢吃的东西", "番茄"),
                memory("饮食禁忌", "香菜"),
                memory("喜欢的音乐", "后摇")));

        Map<String, Object> preview = tool.approvalPreview(objectMapper, ARGS, context(7L));

        assertEquals("讨厌的食物", preview.get("attributeKey"));
        assertEquals("番茄、香菜", preview.get("newValue"));
        assertEquals("merged", preview.get("kind"));

        // 源不止一条，塞不进一个 oldValue 字符串 —— 额外给一份结构让前端逐条渲染
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sources = (List<Map<String, Object>>) preview.get("sources");
        assertEquals(List.of("不喜欢吃的东西", "饮食禁忌"),
                sources.stream().map(source -> source.get("attributeKey")).toList());
        assertEquals(List.of("番茄", "香菜"), sources.stream().map(source -> source.get("value")).toList());
    }

    @Test
    void theTargetIsWrittenBeforeTheSourcesArePurged() {
        // 顺序反过来、且 targetKey 又在 sources 里的话，会把刚写好的合并结果当场删掉
        when(orchestrator.purgeByKey(eq(7L), anyString())).thenReturn(1);

        tool.execute(request(List.of("不喜欢吃的东西", "饮食禁忌"), "讨厌的食物"), context(7L));

        InOrder order = inOrder(orchestrator);
        order.verify(orchestrator).processExtractedMemories(eq(7L), any(), eq("explicit"), any(), any(), any(), any());
        order.verify(orchestrator).purgeByKey(7L, "不喜欢吃的东西");
        order.verify(orchestrator).purgeByKey(7L, "饮食禁忌");
    }

    @Test
    void theTargetKeyIsNeverPurgedEvenIfTheModelListsItAsASource() {
        tool.execute(request(List.of("喜欢的食物", "讨厌的食物"), "讨厌的食物"), context(7L));

        verify(orchestrator, never()).purgeByKey(7L, "讨厌的食物");
        verify(orchestrator).purgeByKey(7L, "喜欢的食物");
    }

    @Test
    void repeatingASourcePurgesItOnlyOnce() {
        when(orchestrator.purgeByKey(eq(7L), anyString())).thenReturn(1);

        tool.execute(request(List.of("饮食禁忌", "饮食禁忌", " 饮食禁忌 "), "讨厌的食物"), context(7L));

        verify(orchestrator, times(1)).purgeByKey(7L, "饮食禁忌");
    }

    @Test
    void theMergedValueIsWrittenThroughTheExplicitPath() {
        // 与 saveMemory 同一个入口：敏感数据拦截与类型归一都在那里，本工具不该另搞一套
        tool.execute(request(List.of("不喜欢吃的东西"), "讨厌的食物"), context(7L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MemoryAttribute>> attributes = ArgumentCaptor.forClass(List.class);
        verify(orchestrator).processExtractedMemories(eq(7L), attributes.capture(), eq("explicit"), any(),
                eq(34L), any(), any());

        MemoryAttribute attribute = attributes.getValue().get(0);
        assertEquals("讨厌的食物", attribute.attributeKey());
        assertEquals("番茄、香菜", attribute.attributeValue());
        assertEquals("explicit", attribute.assertionType());
    }

    @Test
    void sourcesThatCouldNotBeFoundAreReportedBackAsADataNote() {
        when(orchestrator.purgeByKey(eq(7L), anyString())).thenReturn(0);

        var result = (MergeMemoryTool.MergeMemoryResult) tool.execute(
                request(List.of("根本不存在的键"), "讨厌的食物"), context(7L));

        // 目标写成功了，所以整体仍然算成功；但要说清楚源没清掉，别让模型以为合并完整生效了
        assertTrue(result.success());
        assertTrue(result.purgedKeys().isEmpty());
        assertTrue(result.note().contains("没有找到"));
    }

    @Test
    void blankTargetIsRejectedWithoutTouchingTheStore() {
        var result = (MergeMemoryTool.MergeMemoryResult) tool.execute(
                new MergeMemoryTool.MergeMemoryRequest(List.of("A"), "  ", "番茄", "preference", "e"), context(7L));

        assertFalse(result.success());
        assertTrue(result.note().contains("不能为空"));
        verify(orchestrator, never()).processExtractedMemories(anyLong(), any(), anyString(), any(), any(), any(), any());
        verify(orchestrator, never()).purgeByKey(anyLong(), anyString());
    }

    @Test
    void withoutALoggedInUserTheMergeIsRefused() {
        var result = (MergeMemoryTool.MergeMemoryResult) tool.execute(
                request(List.of("A"), "B"), ToolExecutionContext.of(null));

        assertFalse(result.success());
        assertTrue(result.note().contains("登录"));
        verify(orchestrator, never()).purgeByKey(anyLong(), anyString());
    }
}
