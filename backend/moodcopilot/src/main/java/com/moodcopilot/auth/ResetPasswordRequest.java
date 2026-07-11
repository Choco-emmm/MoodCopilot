package com.moodcopilot.auth;

public record ResetPasswordRequest(
        String email,
        String verificationCode,
        String newPassword,
        String confirmNewPassword
) {}
