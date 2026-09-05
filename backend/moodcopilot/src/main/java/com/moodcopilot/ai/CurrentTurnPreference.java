package com.moodcopilot.ai;

import java.util.List;

/** Explicit, request-scoped output preference. It is never inferred or persisted. */
public record CurrentTurnPreference(
        String temporaryResponseStyle,
        List<String> enabledBehaviorFlags,
        List<String> disabledBehaviorFlags,
        String outputRequirement) {
    public CurrentTurnPreference {
        enabledBehaviorFlags = enabledBehaviorFlags == null ? List.of() : enabledBehaviorFlags.stream()
                .filter(value -> value != null).toList();
        disabledBehaviorFlags = disabledBehaviorFlags == null ? List.of() : disabledBehaviorFlags.stream()
                .filter(value -> value != null).toList();
    }

    public boolean isPresent() {
        return (temporaryResponseStyle != null && !temporaryResponseStyle.isBlank())
                || !enabledBehaviorFlags.isEmpty() || !disabledBehaviorFlags.isEmpty()
                || (outputRequirement != null && !outputRequirement.isBlank());
    }
}
