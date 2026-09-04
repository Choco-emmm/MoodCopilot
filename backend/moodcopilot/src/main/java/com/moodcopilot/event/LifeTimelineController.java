package com.moodcopilot.event;

import com.moodcopilot.common.ApiResponse;
import com.moodcopilot.entity.UserEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/life-timeline")
public class LifeTimelineController {
    private final LifeChapterService service;

    public LifeTimelineController(LifeChapterService service) { this.service = service; }

    @GetMapping
    public ApiResponse<LifeChapterService.TimelinePage> list(@AuthenticationPrincipal UserEntity user,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean includeGaps) {
        return ApiResponse.ok(service.listTimeline(user.getId(), from, to, cursor, size, includeGaps));
    }

    @GetMapping("/{id}")
    public ApiResponse<LifeChapterService.ChapterView> get(@AuthenticationPrincipal UserEntity user, @PathVariable Long id) {
        return ApiResponse.ok(service.getTimelineStage(user.getId(), id));
    }

    @GetMapping("/{id}/sources")
    public ApiResponse<LifeChapterService.ChapterSources> sources(@AuthenticationPrincipal UserEntity user, @PathVariable Long id) {
        return ApiResponse.ok(service.getTimelineSources(user.getId(), id));
    }

    @GetMapping("/{id}/versions")
    public ApiResponse<List<LifeChapterService.ChapterVersionView>> versions(@AuthenticationPrincipal UserEntity user, @PathVariable Long id) {
        return ApiResponse.ok(service.listVersions(user.getId(), id));
    }

    @PostMapping("/{id}/refresh")
    public ApiResponse<Void> refresh(@AuthenticationPrincipal UserEntity user, @PathVariable Long id) {
        service.requestRefresh(user.getId(), id); return ApiResponse.ok();
    }

    @GetMapping("/candidates")
    public ApiResponse<List<LifeChapterService.TimelineCandidateView>> candidates(@AuthenticationPrincipal UserEntity user,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.listTimelineCandidates(user.getId(), status));
    }

    @PostMapping("/candidates/{id}/accept")
    public ApiResponse<Void> accept(@AuthenticationPrincipal UserEntity user, @PathVariable Long id) {
        service.acceptTimelineCandidate(user.getId(), id); return ApiResponse.ok();
    }

    @PostMapping("/candidates/{id}/reject")
    public ApiResponse<Void> reject(@AuthenticationPrincipal UserEntity user, @PathVariable Long id) {
        service.rejectTimelineCandidate(user.getId(), id); return ApiResponse.ok();
    }
}
