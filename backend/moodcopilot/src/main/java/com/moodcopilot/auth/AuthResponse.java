package com.moodcopilot.auth;

import java.time.LocalDateTime;

public record AuthResponse(String token, Long userId, String displayName, String email, String avatar, String signature,
        Boolean dailyNotifyEnabled, String role, String inviteCode, Integer inviteQuota,
        Integer exp, Integer level, LocalDateTime proExpireTime,
        Integer nameChangeCount, Integer nameChangeWeek) {
}
