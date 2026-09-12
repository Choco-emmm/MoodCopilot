package com.moodcopilot.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.moodcopilot.common.ApiResponse;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.security.RateLimitService;
import com.moodcopilot.service.SystemConfigService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/config")
public class AdminConfigController {

    private final SystemConfigService systemConfigService;
    private final RateLimitService rateLimitService;

    public AdminConfigController(SystemConfigService systemConfigService, RateLimitService rateLimitService) {
        this.systemConfigService = systemConfigService;
        this.rateLimitService = rateLimitService;
    }

    public record QuotaConfigDto(int[][] quotaMatrix, Map<String, String> labels) {}

    @GetMapping("/ai-quota")
    public ApiResponse<QuotaConfigDto> getAiQuotaConfig(@AuthenticationPrincipal UserEntity user) {
        checkAdmin(user);
        
        int[][] defaultQuota = {
            {150, 15, 50, 15, 2, 30, 50, 50, 50},
            {15,  1,  5,  1, 2,  0,  0,  3,  2},
            {25,  2,  8,  2, 2,  3,  2,  5,  3},
            {35,  3,  12, 3, 2,  5,  4,  8,  5},
            {45,  4,  16, 4, 2,  8,  7,  12, 8},
            {55,  5,  20, 5, 2, 10, 11, 16, 12},
            {65,  6,  25, 6, 2, 12, 16, 20, 15}
        };
        
        Map<String, String> defaultLabels = Map.of(
            "CHAT_FLASH", "聊天 Flash",
            "CHAT_PRO", "聊天 Pro",
            "DIARY_FLASH", "日记分析 Flash",
            "DIARY_PRO", "日记分析 Pro",
            "CHAPTER_CONSOLIDATION", "章节重新整理",
            "RESONANCE", "共鸣检索",
            "REPORT", "报告生成",
            "IMAGE_UPLOAD", "图片上传",
            "IMAGE_ANALYSIS", "图片深度分析"
        );

        int[][] quotaMatrix = systemConfigService.getConfig("AI_QUOTA_CONFIG", new TypeReference<int[][]>() {}, defaultQuota);
        Map<String, String> labels = systemConfigService.getConfig("AI_API_LABELS", new TypeReference<Map<String, String>>() {}, defaultLabels);
        
        return ApiResponse.ok(new QuotaConfigDto(quotaMatrix, labels));
    }

    @PutMapping("/ai-quota")
    public ApiResponse<Void> updateAiQuotaConfig(@AuthenticationPrincipal UserEntity user, @RequestBody QuotaConfigDto configDto) {
        checkAdmin(user);
        
        if (configDto.quotaMatrix() != null) {
            systemConfigService.setConfig("AI_QUOTA_CONFIG", configDto.quotaMatrix());
        }
        if (configDto.labels() != null) {
            systemConfigService.setConfig("AI_API_LABELS", configDto.labels());
        }
        return ApiResponse.ok(null);
    }

    private void checkAdmin(UserEntity user) {
        if (user == null || user.getRole() == null || (!user.getRole().equalsIgnoreCase("ADMIN") && !user.getRole().equalsIgnoreCase("ROLE_ADMIN"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
        }
    }
}
