package com.moodcopilot.onboarding;

import com.moodcopilot.common.ApiResponse;
import com.moodcopilot.entity.UserEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/onboarding")
public class OnboardingController {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @GetMapping
    public ApiResponse<OnboardingStateView> getState(
            @AuthenticationPrincipal UserEntity user,
            @RequestParam(value = "scope", required = false) String scope) {
        return ApiResponse.ok(onboardingService.getState(requireUser(user).getId(), scope));
    }

    @PostMapping("/events")
    public ApiResponse<OnboardingStateView> recordEvent(
            @AuthenticationPrincipal UserEntity user,
            @RequestBody OnboardingEventRequest request) {
        return ApiResponse.ok(onboardingService.recordEvent(requireUser(user).getId(), request));
    }

    @PostMapping("/dismiss")
    public ApiResponse<OnboardingStateView> dismiss(
            @AuthenticationPrincipal UserEntity user,
            @RequestBody OnboardingDismissRequest request) {
        return ApiResponse.ok(onboardingService.dismiss(requireUser(user).getId(), request));
    }

    private UserEntity requireUser(UserEntity user) {
        if (user == null || user.getId() == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "登录状态已失效");
        }
        return user;
    }
}
