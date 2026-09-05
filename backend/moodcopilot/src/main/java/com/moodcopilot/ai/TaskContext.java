package com.moodcopilot.ai;

import java.util.List;
import java.util.Set;

/** Request-scoped task framing. It is never persisted as persona or memory. */
public record TaskContext(String taskType, String instruction, List<String> outputHints) {
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "GENERAL", "CODING", "LEARNING", "WRITING", "TRANSLATION", "PLANNING", "EMOTIONAL_SUPPORT");

    public TaskContext {
        taskType = taskType == null || taskType.isBlank() ? "GENERAL" : taskType.toUpperCase(java.util.Locale.ROOT);
        if (!ALLOWED_TYPES.contains(taskType)) taskType = "GENERAL";
        instruction = instruction == null ? "" : instruction;
        outputHints = outputHints == null ? List.of() : outputHints.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.replaceAll("[\\p{Cntrl}&&[^\\n]]", "").trim())
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    /**
     * Source-compatible bridge for pre-existing call sites. The fourth value is
     * intentionally discarded: turn-scoped Persona preferences belong to
     * {@link TurnPersonaOverrideResolver}, never to task classification.
     */
    @Deprecated(forRemoval = false)
    public TaskContext(String taskType, String instruction, List<String> outputHints,
            PersonaCompiler.PersonaUpdateRequestLike ignoredTurnOverride) {
        this(taskType, instruction, outputHints);
    }
}
