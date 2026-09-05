package com.moodcopilot.ai;

import java.util.List;
import java.util.Map;

/** Restricted, prompt-independent representation of the user's interaction preferences. */
public record EffectivePersona(
        String role,
        List<String> tone,
        List<String> behaviorFlags,
        List<String> allowedStylePreferences,
        String customTone,
        String customResponseStyle,
        String outputRequirement,
        Integer globalVersion,
        Integer conversationVersion,
        boolean turnOverridePresent,
        String effectivePersonaHash,
        Map<String, ResolutionTrace> resolutionTrace) {

    public EffectivePersona {
        role = role == null || role.isBlank() ? "personal_assistant" : role;
        tone = tone == null ? List.of() : List.copyOf(tone);
        behaviorFlags = behaviorFlags == null ? List.of() : List.copyOf(behaviorFlags);
        allowedStylePreferences = allowedStylePreferences == null ? List.of() : List.copyOf(allowedStylePreferences);
        customTone = PersonaPolicy.normalizeCustomTone(customTone);
        customResponseStyle = PersonaPolicy.normalizeCustomResponseStyle(customResponseStyle);
        outputRequirement = PersonaPolicy.normalizeOutputRequirement(outputRequirement);
        effectivePersonaHash = effectivePersonaHash == null ? "" : effectivePersonaHash;
        resolutionTrace = resolutionTrace == null ? Map.of() : Map.copyOf(resolutionTrace);
    }

    public EffectivePersona(String role, List<String> tone, List<String> behaviorFlags,
            List<String> allowedStylePreferences, Integer globalVersion, Integer conversationVersion,
            boolean turnOverridePresent, String effectivePersonaHash) {
        this(role, tone, behaviorFlags, allowedStylePreferences, null, null, null, globalVersion, conversationVersion,
                turnOverridePresent, effectivePersonaHash, Map.of());
    }

    public EffectivePersona(String role, List<String> tone, List<String> behaviorFlags,
            List<String> allowedStylePreferences, String customTone, Integer globalVersion,
            Integer conversationVersion, boolean turnOverridePresent, String effectivePersonaHash) {
        this(role, tone, behaviorFlags, allowedStylePreferences, customTone, null, null, globalVersion,
                conversationVersion, turnOverridePresent, effectivePersonaHash, Map.of());
    }

    public record ResolutionTrace(String scope, Integer version) {}
}
