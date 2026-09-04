package com.moodcopilot.event;

import com.moodcopilot.common.ApiResponse;
import com.moodcopilot.entity.UserEntity;
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

    public LifeChapterController(LifeChapterService lifeChapterService) {
        this.lifeChapterService = lifeChapterService;
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
        lifeChapterService.requestRefresh(user.getId(), id);
        return ApiResponse.ok();
    }

}
