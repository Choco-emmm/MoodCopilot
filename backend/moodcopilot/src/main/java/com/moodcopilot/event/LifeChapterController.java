package com.moodcopilot.event;

import com.moodcopilot.common.ApiResponse;
import com.moodcopilot.entity.UserEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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
}
