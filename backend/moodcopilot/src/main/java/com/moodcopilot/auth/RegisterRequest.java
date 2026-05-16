package com.moodcopilot.auth;

public record RegisterRequest(
    String displayName,
    String email,
    String password,
    String inviteCode,
    String verificationCode
) {
}
