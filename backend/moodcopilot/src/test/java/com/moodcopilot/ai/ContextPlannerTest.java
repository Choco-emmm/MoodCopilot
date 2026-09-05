package com.moodcopilot.ai;

import com.moodcopilot.entity.UserProfileMemoryEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContextPlannerTest {

    @Test
    void planKeepsSourcesSeparatedAndDoesNotLoadOrdinaryMemoryIntoChat() {
        MemoryOrchestrator orchestrator = mock(MemoryOrchestrator.class);
        UserProfileMemoryEntity shortTerm = new UserProfileMemoryEntity();
        shortTerm.setMemoryType("short_term_state");
        shortTerm.setUserId(7L);
        shortTerm.setAttributeKey("当前状态");
        shortTerm.setAttributeValue("最近有些紧张");
        shortTerm.setStatus("active");
        UserProfileMemoryEntity preference = new UserProfileMemoryEntity();
        preference.setMemoryType("preference");
        preference.setUserId(7L);
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
        assertTrue(context.contains("provenance type=\"USER_MESSAGE\""));
        assertTrue(context.contains("<retrieved_context purpose=\"CHAT\">"));
        assertTrue(context.contains("召回的历史经历"));
        assertTrue(context.contains("<conversation_context>"));
        assertTrue(!context.contains("<long_term_memory>"));
        assertTrue(!context.contains("社交偏好：偏好安静交流"));
        assertTrue(plan.envelope().coreMemory().stream()
                .anyMatch(item -> "SYSTEM_SUMMARY".equals(item.source().sourceType())
                        && item.source().trustLevel() == ContextSource.TrustLevel.UNTRUSTED));
    }

    @Test
    void keepsOrdinaryFormalMemoryAndMarksConflictingValuesOutsideChat() {
        MemoryOrchestrator orchestrator = mock(MemoryOrchestrator.class);
        UserProfileMemoryEntity first = memory(1L, "工作方式", "偏好独立完成");
        UserProfileMemoryEntity second = memory(2L, "工作方式", "偏好团队协作");
        when(orchestrator.current(7L)).thenReturn(List.of(first, second));

        String context = new ContextPlanner(orchestrator).planEnvelope(
                7L, null, "", List.of(), List.of(), ContextPurpose.DIARY_ANALYSIS).context();

        assertTrue(context.contains("工作方式：偏好独立完成"));
        assertTrue(context.contains("工作方式：偏好团队协作"));
        assertTrue(context.contains("conflict=\"true\""));
    }

    @Test
    void marksConflictsWhenValuesCrossCoreAndOrdinaryBuckets() {
        MemoryOrchestrator orchestrator = mock(MemoryOrchestrator.class);
        UserProfileMemoryEntity core = memory(1L, "沟通方式", "偏好直接反馈");
        core.setIsCore(true);
        UserProfileMemoryEntity ordinary = memory(2L, "沟通方式", "偏好委婉表达");
        when(orchestrator.current(7L)).thenReturn(List.of(core, ordinary));

        String context = new ContextPlanner(orchestrator).planEnvelope(
                7L, null, "", List.of(), List.of(), ContextPurpose.DIARY_ANALYSIS).context();

        assertTrue(context.contains("偏好直接反馈"));
        assertTrue(context.contains("偏好委婉表达"));
        assertTrue(context.contains("conflict=\"true\""));
    }

    @Test
    void doesNotInjectSensitiveExplicitReferences() {
        MemoryOrchestrator orchestrator = mock(MemoryOrchestrator.class);
        when(orchestrator.current(7L)).thenReturn(List.of());

        String context = new ContextPlanner(orchestrator).planEnvelope(
                7L, null, "", List.of("api_key=secret-value", "普通引用"), List.of(), ContextPurpose.CHAT).context();

        assertTrue(context.contains("普通引用"));
        assertTrue(!context.contains("secret-value"));
    }

    @Test
    void carriesExplicitReferencePurposeIntoEnvelope() {
        MemoryOrchestrator orchestrator = mock(MemoryOrchestrator.class);
        when(orchestrator.current(7L)).thenReturn(List.of());

        ContextPlanner.ContextPlan plan = new ContextPlanner(orchestrator).planEnvelopeWithReferencePurpose(
                7L, null, "", List.of("需要重点分析的记录"), ReferencePurpose.ANALYZE,
                List.of(), ContextPurpose.CHAT, List.of());

        assertTrue(plan.envelope().userReferences().get(0).referencePurpose() == ReferencePurpose.ANALYZE);
        assertTrue(plan.context().contains("reference_purpose=\"ANALYZE\""));
    }

    @Test
    void keepsServerResolvedDiaryReferenceAsAnAuthoritativeSource() {
        MemoryOrchestrator orchestrator = mock(MemoryOrchestrator.class);
        when(orchestrator.current(7L)).thenReturn(List.of());
        UserReference diary = new UserReference("日记正文", new ContextSource(
                "USER_DIARY", "2014", "user", "original", null, null,
                ContextSource.TrustLevel.AUTHORITATIVE, 7L), ReferencePurpose.RECALL, 1D, 60, false);

        ContextPlanner.ContextPlan plan = new ContextPlanner(orchestrator)
                .planEnvelopeWithReferencePurpose(7L, 99L, "", List.of(), ReferencePurpose.DISCUSS,
                        List.of(), ContextPurpose.CHAT, List.of(), List.of(diary));

        assertTrue(plan.envelope().userReferences().get(0).source().sourceType().equals("USER_DIARY"));
        assertTrue(plan.context().contains("source_id=\"2014\""));
        assertTrue(plan.context().contains("reference_purpose=\"RECALL\""));
    }

    @Test
    void deduplicatesResolvedAndLegacyReferencesWithinTheReferenceBudget() {
        MemoryOrchestrator orchestrator = mock(MemoryOrchestrator.class);
        when(orchestrator.current(7L)).thenReturn(List.of());
        UserReference diary = new UserReference("同一篇日记正文", new ContextSource(
                "USER_DIARY", "2014", "user", "original", null, null,
                ContextSource.TrustLevel.AUTHORITATIVE, 7L), ReferencePurpose.DISCUSS, 1D, 60, false);

        ContextPlanner.ContextPlan plan = new ContextPlanner(orchestrator)
                .planEnvelopeWithReferencePurpose(7L, null, "", List.of("同一篇日记正文", "第二条引用"),
                        ReferencePurpose.DISCUSS, List.of(), ContextPurpose.CHAT, List.of(), List.of(diary));

        assertTrue(plan.envelope().userReferences().stream()
                .filter(reference -> "同一篇日记正文".equals(reference.content())).count() == 1);
        assertTrue(plan.envelope().userReferences().size() == 2);
    }

    @Test
    void codingTaskDoesNotLoadImplicitPrivateContextButKeepsExplicitReference() {
        MemoryOrchestrator orchestrator = mock(MemoryOrchestrator.class);
        UserProfileMemoryEntity core = memory(1L, "工作方式", "偏好独立完成");
        core.setIsCore(true);
        when(orchestrator.current(7L)).thenReturn(List.of(core));
        UserReference diary = new UserReference("这是一篇用户明确引用的日记", new ContextSource(
                "USER_DIARY", "2014", "user", "original", null, null,
                ContextSource.TrustLevel.AUTHORITATIVE, 7L), ReferencePurpose.DISCUSS, 1D, 60, false);

        ContextPlanner.ContextPlan plan = new ContextPlanner(orchestrator).planEnvelopeWithReferencePurpose(
                7L, 99L, "旧的核心背景", List.of(), ReferencePurpose.DISCUSS, List.of(),
                ContextPurpose.CHAT, List.of(new ContextItem("阶段背景", new ContextSource(
                        "LIFE_SEGMENT", "active", "user", "chapter_summary", null, "chapter",
                        ContextSource.TrustLevel.SUPPORTING, 7L), 1D, 30, false)),
                List.of(diary), new TaskContext("CODING", "回答编程问题", List.of(), null));

        assertTrue(plan.envelope().coreMemory().isEmpty());
        assertTrue(plan.envelope().shortTermState().isEmpty());
        assertTrue(plan.envelope().timelineContext().isEmpty());
        assertTrue(plan.envelope().userReferences().stream()
                .anyMatch(reference -> "2014".equals(reference.source().sourceId())));
        assertTrue(!plan.context().contains("旧的核心背景"));
    }

    @Test
    void excludesShortTermStateAtItsExclusiveValidUntilBoundary() {
        MemoryOrchestrator orchestrator = mock(MemoryOrchestrator.class);
        UserProfileMemoryEntity state = memory(1L, "当前状态", "临近截止日期的短期状态");
        state.setMemoryType("short_term_state");
        state.setValidFrom(LocalDate.now().minusDays(1));
        state.setValidUntil(LocalDate.now());
        when(orchestrator.current(7L)).thenReturn(List.of(state));

        ContextPlanner.ContextPlan plan = new ContextPlanner(orchestrator).planEnvelope(
                7L, null, "", List.of(), List.of(), ContextPurpose.CHAT);

        assertTrue(plan.envelope().shortTermState().isEmpty());
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
