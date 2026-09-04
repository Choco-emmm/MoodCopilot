package com.moodcopilot.ai;

/** Renders a structured context envelope for a specific model input format. */
public interface PromptRenderer {
    String render(ContextEnvelope envelope);
}
