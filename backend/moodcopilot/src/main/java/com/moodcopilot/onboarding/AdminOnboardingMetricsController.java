package com.moodcopilot.onboarding;

import com.moodcopilot.common.ApiResponse;
import com.moodcopilot.entity.UserEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/admin/onboarding")
public class AdminOnboardingMetricsController {

    private final AdminOnboardingMetricsService adminOnboardingMetricsService;

    public AdminOnboardingMetricsController(AdminOnboardingMetricsService adminOnboardingMetricsService) {
        this.adminOnboardingMetricsService = adminOnboardingMetricsService;
    }

    @GetMapping("/metrics")
    public ApiResponse<AdminOnboardingMetricsView> getMetrics(
            @AuthenticationPrincipal UserEntity user,
            @RequestParam(value = "guideVersion", required = false) String rawGuideVersion) {
        requireAdmin(user);
        Integer guideVersion = parseOptionalGuideVersion(rawGuideVersion);
        return ApiResponse.ok(adminOnboardingMetricsService.getMetrics(guideVersion));
    }

    private void requireAdmin(UserEntity user) {
        if (user == null || user.getId() == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "登录状态已失效");
        }
        String role = user.getRole();
        if (role == null || (!"ADMIN".equalsIgnoreCase(role.trim()) && !"ROLE_ADMIN".equalsIgnoreCase(role.trim()))) {
            throw new ResponseStatusException(FORBIDDEN, "需要管理员权限");
        }
    }

    private Integer parseOptionalGuideVersion(String rawGuideVersion) {
        if (rawGuideVersion == null || rawGuideVersion.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(rawGuideVersion.trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(BAD_REQUEST, "不支持的引导版本");
        }
    }
}
