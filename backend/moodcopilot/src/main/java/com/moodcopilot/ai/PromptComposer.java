package com.moodcopilot.ai;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Single composition boundary for model-facing system text. Context remains structured
 * until the renderer boundary; this class only combines policy, task, persona and context.
 */
@Component
public class PromptComposer {
    private final PersonaPromptSupport personaPromptSupport;
    private final PromptRenderer promptRenderer;

    @Autowired
    public PromptComposer(PersonaPromptSupport personaPromptSupport, PromptRenderer promptRenderer) {
        this.personaPromptSupport = personaPromptSupport;
        this.promptRenderer = promptRenderer;
    }

    /** Compatibility constructor for small pure unit tests. */
    public PromptComposer(PersonaPromptSupport personaPromptSupport) {
        this(personaPromptSupport, new XmlPromptRenderer());
    }

    public String compose(String basePrompt, EffectivePersona persona, TaskContext taskContext,
            ContextPurpose purpose, String renderedContext) {
        StringBuilder result = new StringBuilder();
        result.append(personaPromptSupport.decorate(basePrompt, persona, taskContext, purpose));
        if (renderedContext != null && !renderedContext.isBlank()) {
            result.append("\n\n").append(renderedContext);
        }
        return result.toString();
    }

    /**
     * Structured composition entry point. Callers keep ContextEnvelope structured until
     * this boundary; only the renderer is allowed to turn it into model-facing text.
     */
    public String compose(String basePrompt, EffectivePersona persona, TaskContext taskContext,
            ContextPurpose purpose, ContextEnvelope envelope) {
        return compose(basePrompt, persona, taskContext, purpose, renderContext(envelope));
    }

    public String compose(String basePrompt, Long userId, TaskContext taskContext,
            ContextPurpose purpose, String renderedContext) {
        StringBuilder result = new StringBuilder(
                personaPromptSupport.decorate(basePrompt, userId, taskContext, purpose));
        if (renderedContext != null && !renderedContext.isBlank()) {
            result.append("\n\n").append(renderedContext);
        }
        return result.toString();
    }

    public String compose(String basePrompt, Long userId, TaskContext taskContext,
            ContextPurpose purpose, ContextEnvelope envelope) {
        return compose(basePrompt, userId, taskContext, purpose, renderContext(envelope));
    }

    public String composeForCurrentUser(String basePrompt, TaskContext taskContext,
            ContextPurpose purpose, String renderedContext) {
        StringBuilder result = new StringBuilder(
                personaPromptSupport.decorateForCurrentUser(basePrompt, taskContext, purpose));
        if (renderedContext != null && !renderedContext.isBlank()) {
            result.append("\n\n").append(renderedContext);
        }
        return result.toString();
    }

    public String composeForCurrentUser(String basePrompt, TaskContext taskContext,
            ContextPurpose purpose, ContextEnvelope envelope) {
        return composeForCurrentUser(basePrompt, taskContext, purpose, renderContext(envelope));
    }

    public String renderContext(ContextEnvelope envelope) {
        return envelope == null ? "" : promptRenderer.render(envelope);
    }
}
