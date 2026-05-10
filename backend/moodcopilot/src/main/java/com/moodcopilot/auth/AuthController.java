package com.moodcopilot.auth;

import com.moodcopilot.common.ApiResponse;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.security.RateLimitService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RateLimitService rateLimitService;

    public AuthController(AuthService authService, RateLimitService rateLimitService) {
        this.authService = authService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<AuthResponse> me(@AuthenticationPrincipal UserEntity user) {
        return ApiResponse.ok(new AuthResponse(null, user.getId(), user.getDisplayName(), user.getAvatar(), user.getDailyNotifyEnabled()));
    }

    @PutMapping("/profile")
    public ApiResponse<AuthResponse> updateProfile(@AuthenticationPrincipal UserEntity user,
                                                    @RequestBody Map<String, String> body) {
        String displayName = body.get("displayName");
        String avatar = body.get("avatar");
        return ApiResponse.ok(authService.updateProfile(user.getId(), displayName, avatar));
    }

    @PostMapping("/avatar")
    public ApiResponse<Map<String, String>> uploadAvatar(@AuthenticationPrincipal UserEntity user,
                                                          @RequestParam("file") MultipartFile file) {
        String avatarUrl = authService.uploadAvatar(user.getId(), file);
        return ApiResponse.ok(Map.of("avatar", avatarUrl));
    }

    @PutMapping("/settings")
    public ApiResponse<Void> updateSettings(@AuthenticationPrincipal UserEntity user,
                                             @RequestBody Map<String, Object> body) {
        Boolean dailyNotifyEnabled = (Boolean) body.get("dailyNotifyEnabled");
        authService.updateSettings(user.getId(), dailyNotifyEnabled);
        return ApiResponse.ok(null);
    }

    @GetMapping("/quota")
    public ApiResponse<Map<String, Long>> quota(@AuthenticationPrincipal UserEntity user) {
        return ApiResponse.ok(rateLimitService.getAllRemaining(user.getId()));
    }
}
