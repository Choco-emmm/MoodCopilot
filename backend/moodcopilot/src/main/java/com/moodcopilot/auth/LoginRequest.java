package com.moodcopilot.auth;

public record LoginRequest(String email, String password, String captchaToken) {
}
