package com.moodcopilot.growth;

import com.moodcopilot.common.ApiResponse;
import com.moodcopilot.entity.UserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/growth")
public class GrowthController {

    private final UserGrowthService userGrowthService;

    public GrowthController(UserGrowthService userGrowthService) {
        this.userGrowthService = userGrowthService;
    }

    @PostMapping("/checkin")
    public ApiResponse<Map<String, Object>> checkIn(@AuthenticationPrincipal UserEntity user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录状态已失效");
        }
        int exp = userGrowthService.checkIn(user.getId());
        if (exp <= 0) {
            return ApiResponse.ok(Map.of("checkedIn", false, "exp", 0, "message", "今日已签到～"));
        }
        var status = userGrowthService.getGrowthStatus(user.getId());
        return ApiResponse.ok(Map.of(
                "checkedIn", true,
                "exp", exp,
                "streak", status.streak(),
                "totalExp", status.exp(),
                "level", status.level()));
    }

    @GetMapping("/status")
    public ApiResponse<UserGrowthService.GrowthStatus> status(@AuthenticationPrincipal UserEntity user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录状态已失效");
        }
        return ApiResponse.ok(userGrowthService.getGrowthStatus(user.getId()));
    }

    @GetMapping("/progress")
    public ApiResponse<List<UserGrowthService.DailyExpBar>> progress(@AuthenticationPrincipal UserEntity user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录状态已失效");
        }
        return ApiResponse.ok(userGrowthService.getTodayProgress(user.getId()));
    }

    @GetMapping("/checkins")
    public ApiResponse<boolean[]> checkins(@AuthenticationPrincipal UserEntity user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录状态已失效");
        }
        return ApiResponse.ok(userGrowthService.getMonthCheckins(user.getId()));
    }
}
