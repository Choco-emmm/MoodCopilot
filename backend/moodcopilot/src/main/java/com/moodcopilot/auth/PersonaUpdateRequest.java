package com.moodcopilot.auth;

import java.util.List;

public record PersonaUpdateRequest(
        String role,
        List<String> tone,
        List<String> behaviorFlags,
        List<String> disabledBehaviorFlags,
        String customDescription,
        String customTone,
        String customResponseStyle) {
    public PersonaUpdateRequest(String role, List<String> tone, List<String> behaviorFlags,
            String customDescription, String customTone) {
        this(role, tone, behaviorFlags, null, customDescription, customTone, null);
    }

    public PersonaUpdateRequest(String role, List<String> tone, List<String> behaviorFlags,
            String customDescription) {
        this(role, tone, behaviorFlags, null, customDescription, null, null);
    }
}
