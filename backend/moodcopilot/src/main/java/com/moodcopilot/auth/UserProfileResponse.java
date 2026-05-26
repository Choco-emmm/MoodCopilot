package com.moodcopilot.auth;

public record UserProfileResponse(Long userId, String displayName, String avatar, String signature,
        String theme, String lightTheme, String darkTheme, String themeMode) {
}
