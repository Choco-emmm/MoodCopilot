package com.moodcopilot.auth;

public record AuthResponse(String token, Long userId, String displayName, String email, String avatar, String signature,
        Boolean dailyNotifyEnabled, String role, String inviteCode, Integer inviteQuota) {
}
