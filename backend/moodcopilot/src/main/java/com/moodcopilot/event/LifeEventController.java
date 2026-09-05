package com.moodcopilot.event;

import com.moodcopilot.common.ApiResponse;
import com.moodcopilot.entity.UserEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.format.annotation.DateTimeFormat;

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

    @GetMapping("/{id}")
    public ApiResponse<LifeEventService.LifeEventView> getEvent(@AuthenticationPrincipal UserEntity user,
                                                                 @PathVariable Long id) {
        return ApiResponse.ok(lifeEventService.getEvent(user.getId(), id));
    }

    @GetMapping("/diaries")
    public ApiResponse<LifeEventService.LifeDiaryPage> listDiaries(
            @AuthenticationPrincipal UserEntity user,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(lifeEventService.listUserDiaryOptions(user.getId(), keyword, startDate, endDate, page, size));
    }

    @PostMapping
    public ApiResponse<LifeEventService.LifeEventView> createEvent(
            @AuthenticationPrincipal UserEntity user,
            @RequestBody LifeEventService.LifeEventUpsertRequest request) {
        return ApiResponse.ok(lifeEventService.createEvent(user.getId(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<LifeEventService.LifeEventView> updateEvent(
            @AuthenticationPrincipal UserEntity user,
            @PathVariable Long id,
            @RequestBody LifeEventService.LifeEventUpsertRequest request) {
        return ApiResponse.ok(lifeEventService.updateEvent(user.getId(), id, request));
    }

    @PutMapping("/{id}/diaries")
    public ApiResponse<LifeEventService.LifeEventView> updateEventDiaries(
            @AuthenticationPrincipal UserEntity user,
            @PathVariable Long id,
            @RequestBody Map<String, List<Long>> body) {
        List<Long> diaryIds = body == null ? List.of() : body.getOrDefault("diaryIds", List.of());
        return ApiResponse.ok(lifeEventService.updateEventDiaries(user.getId(), id, diaryIds));
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

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteEvent(@AuthenticationPrincipal UserEntity user, @PathVariable Long id) {
        lifeEventService.softDeleteEvent(user.getId(), id);
        return ApiResponse.ok();
    }

    @GetMapping("/pending-follow-up")
    public ApiResponse<Map<String, Object>> getPendingFollowUp(@AuthenticationPrincipal UserEntity user) {
        var eventOpt = lifeEventService.getPendingEventForFollowUp(user.getId());
        if (eventOpt.isEmpty()) {
            return ApiResponse.ok(Map.of("hasPending", false));
        }
        var e = eventOpt.get();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hasPending", true);
        result.put("eventId", e.getId());
        result.put("title", e.getTitle());
        result.put("targetDate", e.getTargetDate() != null ? e.getTargetDate().toString() : "");
        result.put("endDate", e.getEndDate() != null ? e.getEndDate().toString() : "");
        result.put("startTime", e.getStartTime() != null ? e.getStartTime().toString() : "");
        result.put("endTime", e.getEndTime() != null ? e.getEndTime().toString() : "");
        result.put("description", e.getDescription() != null ? e.getDescription() : "");
        result.put("temporalPhase", lifeEventService.currentTemporalPhase(e));
        result.put("nextFollowUpAt", e.getNextFollowUpAt() == null ? null : e.getNextFollowUpAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        result.put("followUpReason", e.getFollowUpReason());
        result.put("suggestedGreeting", "我一直惦记着你关于 " + e.getTitle() + " 的事，一切还顺利吗？");
        return ApiResponse.ok(result);
    }

    @PostMapping("/{id}/mark-followed-up")
    public ApiResponse<Void> markFollowedUp(@AuthenticationPrincipal UserEntity user, @PathVariable Long id) {
        lifeEventService.markEventFollowedUp(user.getId(), id);
        return ApiResponse.ok();
    }
}
