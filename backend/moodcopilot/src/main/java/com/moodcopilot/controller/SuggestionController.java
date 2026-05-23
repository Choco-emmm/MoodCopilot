package com.moodcopilot.controller;

import com.moodcopilot.common.ApiResponse;
import com.moodcopilot.dto.SuggestionDTO;
import com.moodcopilot.dto.SuggestionRequest;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.service.SuggestionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/suggestions")
public class SuggestionController {

    private final SuggestionService suggestionService;

    public SuggestionController(SuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @PostMapping
    public ApiResponse<Void> submitSuggestion(
            @AuthenticationPrincipal UserEntity user,
            @Valid @RequestBody SuggestionRequest request) {
        suggestionService.submitSuggestion(user.getId(), request);
        return ApiResponse.ok();
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> listSuggestions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(suggestionService.listAllSuggestions(page, size));
    }
}
