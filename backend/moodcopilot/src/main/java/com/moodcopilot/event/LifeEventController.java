package com.moodcopilot.event;

import com.moodcopilot.common.ApiResponse;
import com.moodcopilot.entity.UserEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/life-events")
public class LifeEventController {

    private final LifeEventService lifeEventService;

    public LifeEventController(LifeEventService lifeEventService) {
        this.lifeEventService = lifeEventService;
    }

    @GetMapping
    public ApiResponse<List<LifeEventService.LifeEventView>> listEvents(@AuthenticationPrincipal UserEntity user) {
        return ApiResponse.ok(lifeEventService.listUserEvents(user.getId()));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<LifeEventService.LifeEventView> updateStatus(
            @AuthenticationPrincipal UserEntity user,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String status = body != null ? body.get("status") : null;
        String note = body != null ? body.get("note") : null;
        return ApiResponse.ok(lifeEventService.updateEventStatus(user.getId(), id, status, note));
    }

    @GetMapping("/pending-follow-up")
    public ApiResponse<Map<String, Object>> getPendingFollowUp(@AuthenticationPrincipal UserEntity user) {
        var eventOpt = lifeEventService.getPendingEventForFollowUp(user.getId());
        if (eventOpt.isEmpty()) {
            return ApiResponse.ok(Map.of("hasPending", false));
        }
        var e = eventOpt.get();
        return ApiResponse.ok(Map.of(
                "hasPending", true,
                "eventId", e.getId(),
                "title", e.getTitle(),
                "targetDate", e.getTargetDate() != null ? e.getTargetDate().toString() : "",
                "description", e.getDescription() != null ? e.getDescription() : "",
                "suggestedGreeting", "我一直惦记着你关于 " + e.getTitle() + " 的事，一切还顺利吗？"));
    }

    @PostMapping("/{id}/mark-followed-up")
    public ApiResponse<Void> markFollowedUp(@AuthenticationPrincipal UserEntity user, @PathVariable Long id) {
        lifeEventService.markEventFollowedUp(user.getId(), id);
        return ApiResponse.ok();
    }
}
