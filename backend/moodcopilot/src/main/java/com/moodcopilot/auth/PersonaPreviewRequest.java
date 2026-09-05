package com.moodcopilot.auth;

public record PersonaPreviewRequest(PersonaUpdateRequest persona, String sampleMessage, Boolean useReasoning) {
}
