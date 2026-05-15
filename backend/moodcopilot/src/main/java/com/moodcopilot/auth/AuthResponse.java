package com.moodcopilot.auth;

public record AuthResponse(String token, Long userId, String displayName, String avatar, Boolean dailyNotifyEnabled, String role, String inviteCode, Integer inviteQuota) {
}
