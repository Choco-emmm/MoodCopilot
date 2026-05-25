package com.moodcopilot.auth;

import com.moodcopilot.common.ApiResponse;
import com.moodcopilot.entity.UserEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/send-code")
    public ApiResponse<Void> sendVerificationCode(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "邮箱不能为空");
        }
        authService.sendVerificationCode(email);
        return ApiResponse.ok(null);
    }

    @PostMapping("/change-password/send-code")
    public ApiResponse<Void> sendPasswordChangeCode(@AuthenticationPrincipal UserEntity user) {
        authService.sendPasswordChangeCode(requireUser(user).getId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal UserEntity user,
            @RequestBody ChangePasswordRequest request) {
        authService.changePassword(requireUser(user).getId(), request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @GetMapping("/check-username")
    public ApiResponse<Map<String, Boolean>> checkUsername(@RequestParam String username) {
        return ApiResponse.ok(Map.of("available", authService.isUsernameAvailable(username)));
    }

    @GetMapping("/check-email")
    public ApiResponse<Map<String, Boolean>> checkEmail(@RequestParam String email) {
        return ApiResponse.ok(Map.of("available", authService.isEmailAvailable(email)));
    }

    @GetMapping("/me")
    public ApiResponse<AuthResponse> me(@AuthenticationPrincipal UserEntity user) {
        return ApiResponse.ok(authService.me(requireUser(user).getId()));
    }

    @GetMapping("/profile/{userId}")
    public ApiResponse<UserProfileResponse> profile(@AuthenticationPrincipal UserEntity user,
            @PathVariable Long userId) {
        requireUser(user);
        return ApiResponse.ok(authService.profile(userId));
    }

    @PostMapping("/update-profile")
    public ApiResponse<AuthResponse> updateProfile(@AuthenticationPrincipal UserEntity user,
            @RequestBody Map<String, String> body) {
        String displayName = body.get("displayName");
        String avatar = body.get("avatar");
        String signature = body.get("signature");
        return ApiResponse.ok(authService.updateProfile(requireUser(user).getId(), displayName, avatar, signature));
    }

    @PostMapping("/avatar")
    public ApiResponse<Map<String, String>> uploadAvatar(@AuthenticationPrincipal UserEntity user,
            @RequestParam("file") MultipartFile file) {
        String avatarUrl = authService.uploadAvatar(requireUser(user).getId(), file);
        return ApiResponse.ok(Map.of("avatar", avatarUrl));
    }

    @PutMapping("/settings")
    public ApiResponse<Void> updateSettings(@AuthenticationPrincipal UserEntity user,
            @RequestBody Map<String, Object> body) {
        Boolean dailyNotifyEnabled = (Boolean) body.get("dailyNotifyEnabled");
        Boolean profileNotifyEnabled = (Boolean) body.get("profileNotifyEnabled");
        String theme = (String) body.get("theme");
        String themeMode = (String) body.get("themeMode");
        String lightTheme = (String) body.get("lightTheme");
        String darkTheme = (String) body.get("darkTheme");
        authService.updateSettings(requireUser(user).getId(), dailyNotifyEnabled, profileNotifyEnabled,
                theme, themeMode, lightTheme, darkTheme);
        return ApiResponse.ok(null);
    }

    private UserEntity requireUser(UserEntity user) {
        if (user == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "登录状态已失效");
        }
        return user;
    }
}
