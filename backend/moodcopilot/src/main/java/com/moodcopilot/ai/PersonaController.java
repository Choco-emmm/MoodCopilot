package com.moodcopilot.ai;

import com.moodcopilot.auth.PersonaPreviewRequest;
import com.moodcopilot.auth.PersonaResponse;
import com.moodcopilot.auth.PersonaUpdateRequest;
import com.moodcopilot.common.ApiResponse;
import com.moodcopilot.entity.UserEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class PersonaController {
    private final PersonaService personaService;

    public PersonaController(PersonaService personaService) {
        this.personaService = personaService;
    }

    @GetMapping("/api/auth/ai-persona")
    public ApiResponse<PersonaResponse> get(@AuthenticationPrincipal UserEntity user) {
        return ApiResponse.ok(personaService.current(requireUser(user).getId()));
    }

    @PutMapping("/api/auth/ai-persona")
    public ApiResponse<PersonaResponse> save(@AuthenticationPrincipal UserEntity user,
            @RequestBody PersonaUpdateRequest request) {
        return ApiResponse.ok(personaService.saveGlobal(requireUser(user).getId(), request));
    }

    @PostMapping("/api/auth/ai-persona/preview")
    public ApiResponse<String> preview(@AuthenticationPrincipal UserEntity user,
            @RequestBody PersonaPreviewRequest request) {
        return ApiResponse.ok(personaService.preview(requireUser(user), request));
    }

    @GetMapping("/api/chat/conversations/{conversationId}/persona")
    public ApiResponse<PersonaResponse> getOverride(@AuthenticationPrincipal UserEntity user,
            @PathVariable Long conversationId) {
        return ApiResponse.ok(personaService.currentOverride(requireUser(user).getId(), conversationId));
    }

    @PutMapping("/api/chat/conversations/{conversationId}/persona")
    public ApiResponse<PersonaResponse> saveOverride(@AuthenticationPrincipal UserEntity user,
            @PathVariable Long conversationId, @RequestBody PersonaUpdateRequest request) {
        return ApiResponse.ok(personaService.saveOverride(requireUser(user).getId(), conversationId, request));
    }

    @DeleteMapping("/api/chat/conversations/{conversationId}/persona")
    public ApiResponse<Void> deleteOverride(@AuthenticationPrincipal UserEntity user,
            @PathVariable Long conversationId) {
        personaService.deleteOverride(requireUser(user).getId(), conversationId);
        return ApiResponse.ok(null);
    }

    private UserEntity requireUser(UserEntity user) {
        if (user == null) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED, "登录状态已失效");
        return user;
    }
}
