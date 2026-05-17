package com.moodcopilot.notification;

import com.moodcopilot.common.ApiResponse;
import com.moodcopilot.entity.NotificationEntity;
import com.moodcopilot.entity.UserEntity;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final StringRedisTemplate redisTemplate;

    public NotificationController(NotificationService notificationService,
                                  StringRedisTemplate redisTemplate) {
        this.notificationService = notificationService;
        this.redisTemplate = redisTemplate;
    }

    @PostMapping("/ws-ticket")
    public ApiResponse<Map<String, String>> createWsTicket(@AuthenticationPrincipal UserEntity user) {
        String ticket = UUID.randomUUID().toString();
        String key = "ws_ticket:" + ticket;
        redisTemplate.opsForValue().set(key, String.valueOf(user.getId()), Duration.ofSeconds(30));
        return ApiResponse.ok(Map.of("ticket", ticket));
    }

    @GetMapping
    public ApiResponse<List<NotificationEntity>> list(
            @AuthenticationPrincipal UserEntity user,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(notificationService
                .getNotifications(user.getId(), page, size)
                .getRecords());
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount(@AuthenticationPrincipal UserEntity user) {
        return ApiResponse.ok(Map.of("count", notificationService.getUnreadCount(user.getId())));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id, @AuthenticationPrincipal UserEntity user) {
        notificationService.markAsRead(id, user.getId());
        return ApiResponse.ok(null);
    }
}
