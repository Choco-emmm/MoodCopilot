package com.moodcopilot.auth;

public record UserProfileResponse(Long userId, String displayName, String avatar, String signature) {
}
