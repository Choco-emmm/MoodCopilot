package com.moodcopilot.auth;

import java.time.LocalDateTime;
import java.util.List;

public record PersonaResponse(
        Long id,
        Integer version,
        String role,
        List<String> tone,
        List<String> behaviorFlags,
        List<String> disabledBehaviorFlags,
        String customDescription,
        String customTone,
        String customResponseStyle,
        LocalDateTime updatedAt) {
    public PersonaResponse(Long id, Integer version, String role, List<String> tone,
            List<String> behaviorFlags, String customDescription, String customTone,
            LocalDateTime updatedAt) {
        this(id, version, role, tone, behaviorFlags, List.of(), customDescription, customTone, null, updatedAt);
    }

    public PersonaResponse(Long id, Integer version, String role, List<String> tone,
            List<String> behaviorFlags, String customDescription, LocalDateTime updatedAt) {
        this(id, version, role, tone, behaviorFlags, List.of(), customDescription, null, null, updatedAt);
    }
}
