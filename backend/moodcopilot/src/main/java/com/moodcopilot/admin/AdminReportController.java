package com.moodcopilot.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moodcopilot.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    private final AdminReportService adminReportService;

    public AdminReportController(AdminReportService adminReportService) {
        this.adminReportService = adminReportService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<AdminReportView> reportPage = adminReportService.list(status, page, size);
        return ApiResponse.ok(Map.of(
                "items", reportPage.getRecords(),
                "total", reportPage.getTotal(),
                "page", reportPage.getCurrent(),
                "size", reportPage.getSize()
        ));
    }

    @PostMapping("/{id}/resolve")
    public ApiResponse<Void> resolve(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        adminReportService.resolve(id, note(body));
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<Void> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        adminReportService.reject(id, note(body));
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/hide-target")
    public ApiResponse<Void> hideTarget(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        adminReportService.hideTarget(id, note(body));
        return ApiResponse.ok();
    }

    private String note(Map<String, String> body) {
        return body == null ? null : body.get("note");
    }
}
