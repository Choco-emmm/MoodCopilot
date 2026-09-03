package com.moodcopilot.ai;

import com.moodcopilot.entity.UserProfileMemoryEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContextPlannerTest {

    @Test
    void planKeepsSourcesSeparatedAndExcludesNonShortTermMemoriesFromShortTermBlock() {
        MemoryOrchestrator orchestrator = mock(MemoryOrchestrator.class);
        UserProfileMemoryEntity shortTerm = new UserProfileMemoryEntity();
        shortTerm.setMemoryType("short_term_state");
        shortTerm.setAttributeKey("当前状态");
        shortTerm.setAttributeValue("最近有些紧张");
        UserProfileMemoryEntity preference = new UserProfileMemoryEntity();
        preference.setMemoryType("preference");
        preference.setAttributeKey("社交偏好");
        preference.setAttributeValue("偏好安静交流");
        when(orchestrator.current(7L)).thenReturn(List.of(shortTerm, preference));

        ContextPlanner.ContextPlan plan = new ContextPlanner(orchestrator)
                .plan(7L, "核心画像", List.of("用户引用的日记"), "召回的历史经历");

        String context = plan.context();
        assertTrue(context.contains("<core_memory>\n核心画像\n</core_memory>"));
        assertTrue(context.contains("<short_term_state>\n- 当前状态：最近有些紧张\n</short_term_state>"));
        assertTrue(context.contains("<user_diary>\n用户引用的日记\n</user_diary>"));
        assertTrue(context.contains("<retrieved_experiences>\n召回的历史经历\n</retrieved_experiences>"));
        assertTrue(!context.contains("社交偏好"));
    }
}
