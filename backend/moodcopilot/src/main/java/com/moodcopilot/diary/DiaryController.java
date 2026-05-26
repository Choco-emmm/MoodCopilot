package com.moodcopilot.diary;

import com.moodcopilot.common.ApiResponse;
import com.moodcopilot.common.RateLimitException;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.security.RateLimitService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/diaries")
public class DiaryController {

    private static final Logger log = LoggerFactory.getLogger(DiaryController.class);
    private final DiaryService diaryService;
    private final RateLimitService rateLimitService;

    public DiaryController(DiaryService diaryService, RateLimitService rateLimitService) {
        this.diaryService = diaryService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping
    public ApiResponse<DiaryView> create(@RequestBody CreateDiaryRequest request) {
        DiaryView diary = diaryService.create(request);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        UserEntity user = (auth != null && auth.getPrincipal() instanceof UserEntity u) ? u : null;

        if (!request.isAnalyze()) {
            diary = diary.withAnalysisStatus("skipped_user");
            log.info("用户主动关闭AI分析，diaryId={}，userId={}", diary.id(), diary.authorUserId());
        } else if (user != null) {
            try {
                rateLimitService.tryAcquire(user, RateLimitService.AiApiType.ANALYSIS);
                diary = diary.withAnalysisStatus("analyzing");
                diaryService.submitAiAnalysisTask(diary.id(), diary.authorUserId());
            } catch (RateLimitException e) {
                diary = diary.withAnalysisStatus("skipped_quota");
                log.info("AI分析限额已满，跳过分析，diaryId={}，userId={}", diary.id(), diary.authorUserId());
            }
        }

        return ApiResponse.ok(diary);
    }

    @GetMapping("/mine")
    public ApiResponse<Map<String, Object>> myDiaries(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = diaryService.myDiaries(page, size);
        return ApiResponse.ok(Map.of(
                "items", result.getRecords(),
                "total", result.getTotal(),
                "page", result.getCurrent(),
                "size", result.getSize()));
    }

    @GetMapping("/search")
    public ApiResponse<Map<String, Object>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String visibility,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = diaryService.searchDiaries(keyword, startDate, endDate, visibility, page, size);
        return ApiResponse.ok(Map.of(
                "items", result.getRecords(),
                "total", result.getTotal(),
                "page", result.getCurrent(),
                "size", result.getSize()));
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
                "size", size));
    }

    @GetMapping("/weekly-report")
    public ApiResponse<WeeklyReportView> weeklyReport(
            @RequestParam(defaultValue = "0") int weekOffset) {
        return ApiResponse.ok(diaryService.weeklyReport(weekOffset));
    }

    @PostMapping("/weekly-report/generate")
    public ApiResponse<WeeklyReportView> generateWeeklyReport(
            @RequestParam(defaultValue = "0") int weekOffset) {
        return ApiResponse.ok(diaryService.generateWeeklyAiSummary(weekOffset));
    }

    @GetMapping("/monthly-report")
    public ApiResponse<WeeklyReportView> monthlyReport(
            @RequestParam(defaultValue = "0") int monthOffset) {
        return ApiResponse.ok(diaryService.monthlyReport(monthOffset));
    }

    @PostMapping("/monthly-report/generate")
    public ApiResponse<WeeklyReportView> generateMonthlyReport(
            @RequestParam(defaultValue = "0") int monthOffset) {
        return ApiResponse.ok(diaryService.generateMonthlyAiSummary(monthOffset));
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
                "size", result.getSize()));
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<Map<String, Object>> userDiaries(
            @PathVariable long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = diaryService.userDiaries(userId, page, size);
        return ApiResponse.ok(Map.of(
                "items", result.getRecords(),
                "total", result.getTotal(),
                "page", result.getCurrent(),
                "size", result.getSize()));
    }

    @GetMapping("/{id}")
    public ApiResponse<DiaryView> get(@PathVariable("id") long id) {
        return ApiResponse.ok(diaryService.get(id));
    }

    @GetMapping("/{id}/similar")
    public ApiResponse<List<DiaryView>> similar(
            @PathVariable("id") long id,
            @RequestParam(name = "limit", defaultValue = "3") int limit) {
        return ApiResponse.ok(diaryService.similar(id, limit));
    }

    @PostMapping("/{id}/comments")
    public ApiResponse<DiaryView> addComment(
            @PathVariable("id") long id,
            @RequestBody CreateCommentRequest request) {
        return ApiResponse.ok(diaryService.addComment(id, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<DiaryView> updateDiary(
            @PathVariable("id") long id,
            @RequestBody UpdateDiaryRequest request) {
        return ApiResponse.ok(diaryService.updateDiary(id, request));
    }

    @GetMapping("/today-status")
    public ApiResponse<Map<String, Object>> todayStatus() {
        return ApiResponse.ok(diaryService.todayStatus());
    }

    @GetMapping("/today-match")
    public ApiResponse<DiaryView> todayMatch() {
        return ApiResponse.ok(diaryService.todayMatch());
    }

    @GetMapping("/coaching")
    public ApiResponse<Map<String, Object>> coachingPlan() {
        return ApiResponse.ok(diaryService.coachingPlan());
    }

    @GetMapping("/community-mood")
    public ApiResponse<Map<String, Integer>> communityMood() {
        return ApiResponse.ok(diaryService.communityMood());
    }

    @GetMapping("/{id}/encourage-candidates")
    public ApiResponse<List<String>> encourageCandidates(@PathVariable long id) {
        return ApiResponse.ok(diaryService.generateEncouragements(id));
    }

    @PostMapping("/{id}/resonance")
    public ApiResponse<DiaryView> resonate(
            @PathVariable("id") long id,
            @RequestBody(required = false) Map<String, String> body) {
        if (body != null && body.containsKey("message")) {
            return ApiResponse.ok(diaryService.sendEncouragement(id, body.get("message")));
        }
        return ApiResponse.ok(diaryService.resonate(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteDiary(@PathVariable long id) {
        diaryService.deleteDiary(id);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{diaryId}/comments/{commentId}")
    public ApiResponse<Void> deleteComment(
            @PathVariable long diaryId,
            @PathVariable long commentId) {
        diaryService.deleteComment(diaryId, commentId);
        return ApiResponse.ok(null);
    }
}
