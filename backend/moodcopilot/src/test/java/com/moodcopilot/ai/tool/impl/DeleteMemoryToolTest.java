package com.moodcopilot.ai.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.ai.MemoryOrchestrator;
import com.moodcopilot.ai.tool.ToolExecutionContext;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.entity.UserProfileMemoryEntity;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeleteMemoryToolTest {

    private static final String ARGS = """
            {"attributeKey":"心理状态"}
            """;

    private final MemoryOrchestrator orchestrator = mock(MemoryOrchestrator.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DeleteMemoryTool tool = new DeleteMemoryTool(orchestrator);

    private static ToolExecutionContext context(long userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        Authentication auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
        return new ToolExecutionContext(auth, null, List.of(), "把心理状态删掉吧", 33L, List.of());
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
        assertEquals("deleteMemoryFunction", tool.name());
        assertEquals("deleteMemory", tool.displayName());
    }

    @Test
    void previewShowsTheValueAboutToBeLost() throws Exception {
        when(orchestrator.current(7L)).thenReturn(List.of(memory("心理状态", "自述已好转，状态稳定")));

        Map<String, Object> preview = tool.approvalPreview(objectMapper, ARGS, context(7L));

        // 用户得看清要删的是什么，所以旧值就是当前生效值
        assertEquals("心理状态", preview.get("attributeKey"));
        assertEquals("自述已好转，状态稳定", preview.get("oldValue"));
        // 删除没有「新值」，留空让前端不渲染 + 那一行
        assertEquals("", preview.get("newValue"));
        assertEquals("deleted", preview.get("kind"));
    }

    @Test
    void previewOfAnUnknownKeyCarriesNoValueInsteadOfFailing() throws Exception {
        // 键写错了不该在预览这里炸：严格判断归 execute，那里会回执「没找到」
        when(orchestrator.current(7L)).thenReturn(List.of(memory("讨厌的食物", "番茄")));

        Map<String, Object> preview = tool.approvalPreview(objectMapper, ARGS, context(7L));

        assertEquals("心理状态", preview.get("attributeKey"));
        assertEquals("", preview.get("oldValue"));
        assertEquals("deleted", preview.get("kind"));
    }

    @Test
    void approvalPurgesTheWholeKeyChainAndReportsHowMuchWentWithIt() {
        // 3 = 当前生效那条 + 两条历史版本；用户批准的是「删掉这个键」，不是「删掉当前值」
        when(orchestrator.purgeByKey(7L, "心理状态")).thenReturn(3);

        var result = (DeleteMemoryTool.DeleteMemoryResult) tool.execute(request(), context(7L));

        verify(orchestrator).purgeByKey(7L, "心理状态");
        assertTrue(result.success());
        assertEquals(3, result.removedVersions());
        assertTrue(result.note().contains("3"), "回执要告诉模型顺手清了几个历史版本");
    }

    @Test
    void deletingAKeyThatIsNotThereIsReportedAsDataNotAnException() {
        when(orchestrator.purgeByKey(7L, "心理状态")).thenReturn(0);

        var result = (DeleteMemoryTool.DeleteMemoryResult) tool.execute(request(), context(7L));

        assertFalse(result.success());
        assertEquals(0, result.removedVersions());
        assertTrue(result.note().contains("没有找到"));
    }

    @Test
    void blankKeyIsRejectedWithoutTouchingTheStore() {
        var result = (DeleteMemoryTool.DeleteMemoryResult) tool.execute(
                new DeleteMemoryTool.DeleteMemoryRequest("  "), context(7L));

        assertFalse(result.success());
        assertTrue(result.note().contains("不能为空"));
        verify(orchestrator, never()).purgeByKey(anyLong(), anyString());
    }

    @Test
    void withoutALoggedInUserTheDeleteIsRefused() {
        var result = (DeleteMemoryTool.DeleteMemoryResult) tool.execute(
                request(), ToolExecutionContext.of(null));

        assertFalse(result.success());
        assertTrue(result.note().contains("登录"));
        verify(orchestrator, never()).purgeByKey(anyLong(), anyString());
    }

    @Test
    void aPreviewQueryFailurePropagatesSoTheRegistryCanDegrade() {
        when(orchestrator.current(7L)).thenThrow(new IllegalStateException("库挂了"));

        // 取当前值失败必须往上抛：由 ChatToolRegistry.approvalPreview 兜成「只说工具名」。
        // 在这里悄悄吞掉，用户就会看到一个「不知道要删什么」的空弹框还照样批准。
        IllegalStateException thrown = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> tool.approvalPreview(objectMapper, ARGS, context(7L)));
        assertEquals("库挂了", thrown.getMessage());
    }

    @Test
    void argumentsMissingAFieldStillProduceAUsablePreview() throws Exception {
        Map<String, Object> preview = tool.approvalPreview(objectMapper, "{}", context(7L));

        assertEquals("deleted", preview.get("kind"));
        assertEquals("", preview.get("oldValue"));
    }

    private static DeleteMemoryTool.DeleteMemoryRequest request() {
        return new DeleteMemoryTool.DeleteMemoryRequest("心理状态");
    }
}
