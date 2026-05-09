package com.moodcopilot.follow;

import com.moodcopilot.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/follows")
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    @PostMapping("/{userId}")
    public ApiResponse<Void> follow(@PathVariable long userId) {
        followService.follow(userId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Void> unfollow(@PathVariable long userId) {
        followService.unfollow(userId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{userId}/status")
    public ApiResponse<Map<String, Boolean>> status(@PathVariable long userId) {
        return ApiResponse.ok(Map.of("following", followService.isFollowing(userId)));
    }
}
