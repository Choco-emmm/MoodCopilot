package com.moodcopilot.ai.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.ai.*;
import com.moodcopilot.ai.tool.*;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.entity.UserProfileMemoryEntity;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UpdateMemoryToolTest {
    @Test void approvalEditsHaveAnExplicitOriginAndCannotChangeTargetOrEvidence() throws Exception {
        var orchestrator = mock(MemoryOrchestrator.class);
        when(orchestrator.applyUpdate(7L, "run-a", "call-a", 22L, "喜欢梨", 32L)).thenReturn(MemoryWriteResult.applied(23L));
        var registry = new ChatToolRegistry(new ObjectMapper(), List.of(new UpdateMemoryTool(orchestrator)));
        var user = new UserEntity(); user.setId(7L);
        var auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
        var context = new ToolExecutionContext(auth, null, List.of(), "我喜欢香蕉", 32L, List.of(), "run-a", "call-a");
        String arguments = "{\"memoryId\":22,\"expectedValue\":\"喜欢苹果\",\"newValue\":\"喜欢香蕉\",\"evidence\":\"我喜欢香蕉\"}";
        var result = (Map<?,?>)registry.executeEdited(UpdateMemoryTool.NAME, arguments, context, "喜欢梨");
        assertEquals(true, result.get("userEdited"));
        assertEquals("USER_APPROVAL_EDIT", result.get("approvalSource"));
        assertEquals("喜欢梨", result.get("newValue"));
        assertTrue(arguments.contains("喜欢香蕉"));
        verify(orchestrator).applyUpdate(7L, "run-a", "call-a", 22L, "喜欢梨", 32L);
        assertThrows(IllegalArgumentException.class, () -> registry.executeEdited(SaveMemoryTool.NAME, arguments, context, "梨"));
    }

    @Test void previewValidationFailurePreservesAttributeAndReasonWhenMemoryExists() throws Exception {
        var orchestrator = mock(MemoryOrchestrator.class);
        when(orchestrator.prepareUpdate(7L, "run-a", "call-a", 22L, "喜欢苹果", "喜欢香蕉", "旧记忆", "把那条改了\n"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少有效的用户原文依据"));
        var memory = new UserProfileMemoryEntity();
        memory.setId(22L);
        memory.setAttributeKey("饮食偏好");
        memory.setAttributeValue("喜欢苹果");
        when(orchestrator.detail(7L, 22L)).thenReturn(memory);

        var tool = new UpdateMemoryTool(orchestrator);
        var user = new UserEntity(); user.setId(7L);
        var auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
        var context = new ToolExecutionContext(auth, null, List.of(), "把那条改了", 32L, List.of(), "run-a", "call-a");
        String arguments = "{\"memoryId\":22,\"expectedValue\":\"喜欢苹果\",\"newValue\":\"喜欢香蕉\",\"evidence\":\"旧记忆\"}";

        var preview = tool.approvalPreview(new ObjectMapper(), arguments, context);
        assertEquals(false, preview.get("canApprove"));
        assertEquals("饮食偏好", preview.get("attributeKey"));
        assertEquals("喜欢苹果", preview.get("oldValue"));
        assertEquals("喜欢香蕉", preview.get("newValue"));
        assertTrue(String.valueOf(preview.get("previewError")).contains("原话依据"));
    }

    @Test void missingMemoryInPreviewDegradesSafelyInRegistry() {
        var orchestrator = mock(MemoryOrchestrator.class);
        when(orchestrator.prepareUpdate(anyLong(), anyString(), anyString(), eq(999L), any(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "记忆记录不存在或无权操作"));
        when(orchestrator.detail(7L, 999L))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "记忆记录不存在或无权操作"));

        var registry = new ChatToolRegistry(new ObjectMapper(), List.of(new UpdateMemoryTool(orchestrator)));
        var user = new UserEntity(); user.setId(7L);
        var auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
        var context = new ToolExecutionContext(auth, null, List.of(), "改掉", 32L, List.of(), "run-a", "call-a");
        String arguments = "{\"memoryId\":999,\"expectedValue\":\"旧值\",\"newValue\":\"新值\",\"evidence\":\"改掉\"}";

        var preview = registry.approvalPreview(UpdateMemoryTool.NAME, arguments, context);
        assertEquals(false, preview.get("canApprove"));
        assertEquals("无法读取完整修改内容，请暂不修改后重试。", preview.get("previewError"));
    }

    @Test void systemPolicyForbidsExposingInternalIdsToUser() {
        assertTrue(SystemPolicy.text().contains("内部数字编号或 ID"));
    }
}
