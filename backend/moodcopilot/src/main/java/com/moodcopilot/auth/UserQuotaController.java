package com.moodcopilot.auth;

import com.moodcopilot.common.ApiResponse;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.security.RateLimitService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserQuotaController {

    private final RateLimitService rateLimitService;

    public UserQuotaController(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    public record QuotaResponse(int exp, int level, LocalDateTime proExpireTime, Map<String, Long> quotas, Map<String, Integer> maxQuotas) {}

    @GetMapping("/quota")
    public ApiResponse<QuotaResponse> quota(@AuthenticationPrincipal UserEntity user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录状态已失效");
        }
        Map<String, Long> quotas = rateLimitService.getAllRemaining(user);
        Map<String, Integer> maxQuotas = new java.util.HashMap<>();
        boolean isPro = user.getProExpireTime() != null && user.getProExpireTime().isAfter(LocalDateTime.now());
        for (RateLimitService.AiApiType type : RateLimitService.AiApiType.values()) {
            maxQuotas.put(type.name(), RateLimitService.getDynamicLimit(type, user.getLevel(), isPro));
        }
        return ApiResponse.ok(new QuotaResponse(
                user.getExp() != null ? user.getExp() : 0,
                user.getLevel() != null ? user.getLevel() : 1,
                user.getProExpireTime(),
                quotas,
                maxQuotas));
    }
}
