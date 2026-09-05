package com.moodcopilot.ai;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromptComposerTest {
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
