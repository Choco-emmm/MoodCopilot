package com.moodcopilot.summary;

import com.moodcopilot.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/summaries")
public class SummaryController {

    private final SummaryService summaryService;

    public SummaryController(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @PostMapping
    public ApiResponse<SummaryView> create(@RequestBody Map<String, String> body) {
        LocalDate startDate = LocalDate.parse(body.get("startDate"));
        LocalDate endDate = LocalDate.parse(body.get("endDate"));
        return ApiResponse.ok(summaryService.create(startDate, endDate));
    }

    @GetMapping
    public ApiResponse<java.util.List<SummaryView>> list(@RequestParam(required = false) String type) {
        return ApiResponse.ok(summaryService.list(type));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        summaryService.delete(id);
        return ApiResponse.ok(null);
    }
}
