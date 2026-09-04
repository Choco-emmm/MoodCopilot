package com.moodcopilot.ai;

import com.moodcopilot.entity.UserProfileMemoryEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContextPlannerTest {

    @Test
    void planKeepsSourcesSeparatedAndIncludesOrdinaryFormalMemoryOutsideShortTermBlock() {
        MemoryOrchestrator orchestrator = mock(MemoryOrchestrator.class);
        UserProfileMemoryEntity shortTerm = new UserProfileMemoryEntity();
        shortTerm.setMemoryType("short_term_state");
        shortTerm.setAttributeKey("当前状态");
        shortTerm.setAttributeValue("最近有些紧张");
        shortTerm.setStatus("active");
        UserProfileMemoryEntity preference = new UserProfileMemoryEntity();
        preference.setMemoryType("preference");
        preference.setAttributeKey("社交偏好");
        preference.setAttributeValue("偏好安静交流");
        when(orchestrator.current(7L)).thenReturn(List.of(shortTerm, preference));

        ContextPlanner.ContextPlan plan = new ContextPlanner(orchestrator)
                .plan(7L, "核心画像", List.of("用户引用的日记"), "召回的历史经历");

        String context = plan.context();
        assertTrue(context.contains("<core_memory>"));
        assertTrue(context.contains("核心画像"));
        assertTrue(context.contains("<short_term_state>"));
        assertTrue(context.contains("当前状态：最近有些紧张"));
        assertTrue(context.contains("<user_references>"));
        assertTrue(context.contains("用户引用的日记"));
        assertTrue(context.contains("<retrieved_context purpose=\"CHAT\">"));
        assertTrue(context.contains("召回的历史经历"));
        assertTrue(context.contains("<conversation_context>"));
        assertTrue(!context.contains("<long_term_memory>"));
        assertTrue(context.contains("社交偏好：偏好安静交流"));
    }

    @Test
    void keepsOrdinaryFormalMemoryAndMarksConflictingValues() {
        MemoryOrchestrator orchestrator = mock(MemoryOrchestrator.class);
        UserProfileMemoryEntity first = memory(1L, "工作方式", "偏好独立完成");
        UserProfileMemoryEntity second = memory(2L, "工作方式", "偏好团队协作");
        when(orchestrator.current(7L)).thenReturn(List.of(first, second));

        String context = new ContextPlanner(orchestrator).planEnvelope(
                7L, null, "", List.of(), List.of(), ContextPurpose.CHAT).context();

        assertTrue(context.contains("工作方式：偏好独立完成"));
        assertTrue(context.contains("工作方式：偏好团队协作"));
        assertTrue(context.contains("conflict=\"true\""));
    }

    private UserProfileMemoryEntity memory(Long id, String key, String value) {
        UserProfileMemoryEntity memory = new UserProfileMemoryEntity();
        memory.setId(id);
        memory.setUserId(7L);
        memory.setAttributeKey(key);
        memory.setAttributeValue(value);
        memory.setMemoryType("preference");
        memory.setIsCore(false);
        memory.setStatus("active");
        return memory;
    }
}
