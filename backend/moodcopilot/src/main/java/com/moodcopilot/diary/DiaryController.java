package com.moodcopilot.diary;

import com.moodcopilot.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/diaries")
public class DiaryController {
    private final DiaryService diaryService;

    public DiaryController(DiaryService diaryService) {
        this.diaryService = diaryService;
    }

    @PostMapping
    public ApiResponse<DiaryView> create(@RequestBody CreateDiaryRequest request) {
        DiaryView diary = diaryService.create(request);
        diaryService.runAiAnalysis(diary.id(), diary.content());
        return ApiResponse.ok(diary);
    }

    @GetMapping("/mine")
    public ApiResponse<List<DiaryView>> myDiaries() {
        return ApiResponse.ok(diaryService.myDiaries());
    }

    @GetMapping("/public")
    public ApiResponse<List<DiaryView>> publicDiaries() {
        return ApiResponse.ok(diaryService.publicDiaries());
    }

    @GetMapping("/{id}")
    public ApiResponse<DiaryView> get(@PathVariable("id") long id) {
        return ApiResponse.ok(diaryService.get(id));
    }

    @GetMapping("/{id}/similar")
    public ApiResponse<List<DiaryView>> similar(
            @PathVariable("id") long id,
            @RequestParam(name = "limit", defaultValue = "3") int limit
    ) {
        return ApiResponse.ok(diaryService.similar(id, limit));
    }

    @PostMapping("/{id}/comments")
    public ApiResponse<DiaryView> addComment(
            @PathVariable("id") long id,
            @RequestBody CreateCommentRequest request
    ) {
        return ApiResponse.ok(diaryService.addComment(id, request));
    }

    @PostMapping("/{id}/resonance")
    public ApiResponse<DiaryView> resonate(@PathVariable("id") long id) {
        return ApiResponse.ok(diaryService.resonate(id));
    }
}
