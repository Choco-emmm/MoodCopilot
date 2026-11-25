package com.moodcopilot.controller;

import com.moodcopilot.common.ApiResponse;
import com.moodcopilot.dto.CreateSuggestionReplyRequest;
import com.moodcopilot.dto.SuggestionDTO;
import com.moodcopilot.dto.SuggestionRequest;
import com.moodcopilot.dto.UpdateSuggestionStatusRequest;
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
    public ApiResponse<SuggestionDTO> submitSuggestion(
            @AuthenticationPrincipal UserEntity user,
            @Valid @RequestBody SuggestionRequest request) {
        return ApiResponse.ok(suggestionService.submitSuggestion(user, request));
    }

    @GetMapping("/mine")
    public ApiResponse<Map<String, Object>> listMySuggestions(
            @AuthenticationPrincipal UserEntity user,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(suggestionService.listMySuggestions(user, page, size));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> listSuggestions(
            @AuthenticationPrincipal UserEntity user,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(suggestionService.listAllSuggestions(user, page, size, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<SuggestionDTO> getSuggestion(
            @AuthenticationPrincipal UserEntity user,
            @PathVariable Long id) {
        return ApiResponse.ok(suggestionService.getSuggestion(user, id));
    }

    @PostMapping("/{id}/replies")
    public ApiResponse<SuggestionDTO> addReply(
            @AuthenticationPrincipal UserEntity user,
            @PathVariable Long id,
            @Valid @RequestBody CreateSuggestionReplyRequest request) {
        return ApiResponse.ok(suggestionService.addReply(user, id, request));
    }

    @DeleteMapping("/{id}/replies/{replyId}")
    public ApiResponse<SuggestionDTO> deleteReply(
            @AuthenticationPrincipal UserEntity user,
            @PathVariable Long id,
            @PathVariable Long replyId) {
        return ApiResponse.ok(suggestionService.deleteReply(user, id, replyId));
    }

    @RequestMapping(value = "/{id}/status", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SuggestionDTO> updateStatus(
            @AuthenticationPrincipal UserEntity user,
            @PathVariable Long id,
            @Valid @RequestBody UpdateSuggestionStatusRequest request) {
        return ApiResponse.ok(suggestionService.updateStatus(user, id, request.status()));
    }
}
