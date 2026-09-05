package com.moodcopilot.ai;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromptComposerTest {
    @Test
    void expandsPersonaFlagsIntoExecutableGuidance() {
        PersonaPromptSupport support = new PersonaPromptSupport(mock(PersonaService.class));
        EffectivePersona persona = new EffectivePersona(
                "personal_assistant",
                List.of("analytical", "humorous"),
                List.of("LESS_REASSURANCE"),
                List.<String>of(),
                3,
                2,
                false,
                "hash");

        String prompt = support.decorate("base", persona,
                new TaskContext("GENERAL", "直接回答", List.of(), null),
                ContextPurpose.CHAT);

        assertTrue(prompt.contains("<behavior_flags>LESS_REASSURANCE</behavior_flags>"));
        assertTrue(prompt.contains("减少安慰、鼓励和情绪包装"));
        assertTrue(prompt.contains("优先拆分问题、说明依据和因果关系"));
        assertTrue(prompt.contains("只在合适且不冒犯的场合使用轻微幽默"));
    }

    @Test
    void rendersStructuredEnvelopeOnlyAtTheComposerBoundary() {
        PersonaPromptSupport support = mock(PersonaPromptSupport.class);
        when(support.decorate("base", (EffectivePersona) null, null, ContextPurpose.CHAT))
                .thenReturn("policy-and-persona");
        PromptRenderer renderer = envelope -> "<conversation_context><item>safe</item></conversation_context>";
        PromptComposer composer = new PromptComposer(support, renderer);
        ContextEnvelope envelope = new ContextEnvelope("ctx", 9L, 7L, ContextPurpose.CHAT,
                Instant.now(), "2", List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        String result = composer.compose("base", (EffectivePersona) null, null, ContextPurpose.CHAT, envelope);

        assertTrue(result.startsWith("policy-and-persona"));
        assertTrue(result.contains("<conversation_context>"));
        assertTrue(result.contains("safe"));
    }
}
