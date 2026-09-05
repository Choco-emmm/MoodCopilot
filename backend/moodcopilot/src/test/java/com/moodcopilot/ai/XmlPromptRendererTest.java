package com.moodcopilot.ai;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlPromptRendererTest {

    @Test
    void rendersPeerContextBlocksAndEscapesData() {
        ContextSource source = new ContextSource("USER_DIARY", "2014", "user", "original",
                Instant.parse("2026-09-04T04:30:00Z"), null,
                ContextSource.TrustLevel.SUPPORTING, 1006L);
        ContextEnvelope envelope = new ContextEnvelope("ctx", null, 1006L, ContextPurpose.CHAT,
                Instant.now(), "2", List.of(), List.of(), List.of(),
                List.of(new ContextItem("<not-an-instruction>", source, 0.2, 20, false)), List.of(), List.of());

        String rendered = new XmlPromptRenderer().render(envelope);

        assertTrue(rendered.contains("<conversation_context>"));
        assertTrue(rendered.contains("<retrieved_context purpose=\"CHAT\">"));
        assertTrue(rendered.contains("&lt;not-an-instruction&gt;"));
        assertTrue(rendered.contains("用户日记原文"));
        assertFalse(rendered.contains("<long_term_memory>"));
        assertFalse(rendered.contains("<retrieved_experiences>"));
    }

    @Test
    void itemBudgetDoesNotCutXmlTags() {
        String content = "x".repeat(10000);
        ContextSource source = new ContextSource("USER_DIARY", "1", "user", "original", null,
                null, ContextSource.TrustLevel.SUPPORTING, 1L);
        ContextEnvelope envelope = new ContextEnvelope("ctx", null, 1L, ContextPurpose.CHAT,
                Instant.now(), "2", List.of(), List.of(), List.of(),
                List.of(new ContextItem(content, source, 0.1, 20, false)), List.of(), List.of());

        String rendered = new XmlPromptRenderer().render(envelope);

        assertTrue(rendered.contains("</retrieved_context>"));
        assertTrue(rendered.endsWith("</conversation_context>"));
    }

    @Test
    void rendersReferencePurposeAsStructuredMetadata() {
        ContextSource source = new ContextSource("USER_DIARY", "2014", "user", "original", null,
                null, ContextSource.TrustLevel.AUTHORITATIVE, 1006L);
        UserReference reference = new UserReference("<quoted>内容</quoted>", source,
                ReferencePurpose.ANALYZE, 1D, 60, false);
        ContextEnvelope envelope = new ContextEnvelope("ctx", null, 1006L, ContextPurpose.CHAT,
                Instant.now(), "2", List.of(), List.of(), List.of(reference), List.of(), List.of(), List.of());

        String rendered = new XmlPromptRenderer().render(envelope);

        assertTrue(rendered.contains("<user_references>"));
        assertTrue(rendered.contains("reference_purpose=\"ANALYZE\""));
        assertTrue(rendered.contains("&lt;quoted&gt;内容&lt;/quoted&gt;"));
        assertTrue(rendered.contains("trust=\"AUTHORITATIVE\""));
    }
}
