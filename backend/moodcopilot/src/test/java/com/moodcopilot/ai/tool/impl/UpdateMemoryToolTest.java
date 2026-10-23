package com.moodcopilot.ai.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.ai.*;
import com.moodcopilot.ai.tool.*;
import com.moodcopilot.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
}
