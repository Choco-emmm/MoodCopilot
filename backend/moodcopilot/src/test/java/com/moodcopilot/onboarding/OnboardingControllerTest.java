package com.moodcopilot.onboarding;

import com.moodcopilot.common.GlobalExceptionHandler;
import com.moodcopilot.entity.UserEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OnboardingControllerTest {

    private OnboardingService onboardingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        onboardingService = mock(OnboardingService.class);
        OnboardingController controller = new OnboardingController(onboardingService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousRequestsReturn401() throws Exception {
        mockMvc.perform(get("/api/onboarding"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(post("/api/onboarding/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"WELCOME_COMPLETED\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(post("/api/onboarding/dismiss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"WELCOME\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void authenticatedEndpointsReturnApiResponseCodeZero() throws Exception {
        authenticate(7L);
        OnboardingStateView view = sampleView(1, "COMPLETED", "IN_PROGRESS");
        when(onboardingService.getState(7L, "WEB")).thenReturn(view);
        when(onboardingService.recordEvent(eq(7L), any())).thenReturn(view);
        when(onboardingService.dismiss(eq(7L), any())).thenReturn(sampleView(1, "SKIPPED", "SKIPPED"));

        mockMvc.perform(get("/api/onboarding").param("scope", "WEB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.enrolledVersion").value(1))
                .andExpect(jsonPath("$.data.guideVersion").value(1))
                .andExpect(jsonPath("$.data.welcomeStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.tasks.FIRST_DIARY").value("COMPLETED"));

        mockMvc.perform(post("/api/onboarding/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"WELCOME_COMPLETED\",\"guideVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.welcomeStatus").value("COMPLETED"));

        mockMvc.perform(post("/api/onboarding/dismiss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"CHECKLIST\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.checklistStatus").value("SKIPPED"));
    }

    @Test
    void propagatesBadRequestForbiddenAndNotFoundFromService() throws Exception {
        authenticate(7L);

        when(onboardingService.recordEvent(eq(7L), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的引导事件类型"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "无权上报他人日记的分析查看事件"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "日记不存在"));

        mockMvc.perform(post("/api/onboarding/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"FIRST_DIARY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(post("/api/onboarding/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"ANALYSIS_VIEWED\",\"diaryId\":999}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(post("/api/onboarding/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"ANALYSIS_VIEWED\",\"diaryId\":404}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    private void authenticate(Long userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private static OnboardingStateView sampleView(int enrolledVersion, String welcome, String checklist) {
        Map<String, String> tasks = new LinkedHashMap<>();
        tasks.put("FIRST_DIARY", "COMPLETED");
        tasks.put("FIRST_ANALYSIS_VIEWED", "NOT_STARTED");
        tasks.put("FIRST_CHAT_REPLY", "NOT_STARTED");
        tasks.put("MEMORY_CENTER_VISITED", "NOT_STARTED");
        tasks.put("THEME_EXPLORED", "NOT_STARTED");
        return new OnboardingStateView(enrolledVersion, 1, welcome, checklist, tasks, Map.of());
    }
}
