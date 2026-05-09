package com.moodcopilot.diary;

import com.moodcopilot.common.ApiResponse;

import java.util.List;
import java.util.Map;

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

    @GetMapping("/following")
    public ApiResponse<Map<String, Object>> followingDiaries(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = diaryService.followingDiaries(page, size);
        return ApiResponse.ok(Map.of(
                "items", result.getRecords(),
                "total", result.getTotal(),
                "page", page,
                "size", size
        ));
    }

    @GetMapping("/weekly-report")
    public ApiResponse<WeeklyReportView> weeklyReport(
            @RequestParam(defaultValue = "0") int weekOffset) {
        return ApiResponse.ok(diaryService.weeklyReport(weekOffset));
    }

    @GetMapping("/public")
    public ApiResponse<java.util.Map<String, Object>> publicDiaries(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = diaryService.publicDiaries(page, size);
        return ApiResponse.ok(java.util.Map.of(
                "items", result.getRecords(),
                "total", result.getTotal(),
                "page", result.getCurrent(),
                "size", result.getSize()
        ));
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
