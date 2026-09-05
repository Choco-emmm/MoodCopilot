package com.moodcopilot.event;

import com.moodcopilot.common.ApiResponse;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.security.RateLimitService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/life-chapters")
public class LifeChapterController {

    private final LifeChapterService lifeChapterService;
    private final RateLimitService rateLimitService;

    public LifeChapterController(LifeChapterService lifeChapterService, RateLimitService rateLimitService) {
        this.lifeChapterService = lifeChapterService;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping
    public ApiResponse<List<LifeChapterService.ChapterView>> listChapters(
            @AuthenticationPrincipal UserEntity user) {
        return ApiResponse.ok(lifeChapterService.listUserChapters(user.getId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<LifeChapterService.ChapterView> getChapter(
            @AuthenticationPrincipal UserEntity user, @PathVariable Long id) {
        return ApiResponse.ok(lifeChapterService.getChapter(user.getId(), id));
    }

    @GetMapping("/{id}/versions")
    public ApiResponse<List<LifeChapterService.ChapterVersionView>> versions(
            @AuthenticationPrincipal UserEntity user, @PathVariable Long id) {
        return ApiResponse.ok(lifeChapterService.listVersions(user.getId(), id));
    }

    @GetMapping("/{id}/sources")
    public ApiResponse<LifeChapterService.ChapterSources> sources(
            @AuthenticationPrincipal UserEntity user, @PathVariable Long id) {
        return ApiResponse.ok(lifeChapterService.sources(user.getId(), id));
    }

    @PostMapping("/{id}/refresh")
    public ApiResponse<Void> refresh(@AuthenticationPrincipal UserEntity user, @PathVariable Long id) {
        rateLimitService.tryAcquire(user, RateLimitService.AiApiType.CHAPTER_CONSOLIDATION);
        lifeChapterService.requestRefresh(user.getId(), id);
        return ApiResponse.ok();
    }

}
