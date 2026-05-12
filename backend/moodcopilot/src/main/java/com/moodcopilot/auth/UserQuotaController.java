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

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserQuotaController {

    private final RateLimitService rateLimitService;

    public UserQuotaController(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @GetMapping("/quota")
    public ApiResponse<Map<String, Long>> quota(@AuthenticationPrincipal UserEntity user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录状态已失效");
        }
        return ApiResponse.ok(rateLimitService.getAllRemaining(user.getId()));
    }
}
