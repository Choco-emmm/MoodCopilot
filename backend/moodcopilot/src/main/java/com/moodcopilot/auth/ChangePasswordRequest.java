package com.moodcopilot.auth;

public record ChangePasswordRequest(
        String oldPassword,
        String newPassword,
        String confirmNewPassword,
        String verificationCode) {
}
