package com.moodcopilot.auth;

import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.mapper.UserMapper;
import com.moodcopilot.security.JwtAuthenticationFilter;
import com.moodcopilot.security.RateLimitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerMockMvcTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private RateLimitService rateLimitService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UserMapper userMapper;

    @BeforeEach
    void setUpAuthentication() {
        authenticate(user());
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void uploadAvatarReturnsAvatarUrl() throws Exception {
        when(authService.uploadAvatar(eq(7L), any())).thenReturn("/uploads/avatars/7-1710000000000.png");

        mockMvc.perform(multipart("/api/auth/avatar")
                        .file(new MockMultipartFile("file", "avatar.png", "image/png", new byte[] { 1, 2, 3 }))
                        .with(authentication(authUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatar").value("/uploads/avatars/7-1710000000000.png"));
    }

    @Test
    void uploadAvatarRejectsTooLargeFile() throws Exception {
        when(authService.uploadAvatar(eq(7L), any()))
                .thenThrow(new ResponseStatusException(BAD_REQUEST, "文件大小不能超过 2MB"));

        mockMvc.perform(multipart("/api/auth/avatar")
                        .file(new MockMultipartFile("file", "avatar.png", "image/png", new byte[2 * 1024 * 1024 + 1]))
                        .with(authentication(authUser())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadAvatarRejectsUnsupportedContentType() throws Exception {
        when(authService.uploadAvatar(eq(7L), any()))
                .thenThrow(new ResponseStatusException(BAD_REQUEST, "仅支持 JPEG/PNG/WebP 格式"));

        mockMvc.perform(multipart("/api/auth/avatar")
                        .file(new MockMultipartFile("file", "avatar.txt", "text/plain", "hello".getBytes()))
                        .with(authentication(authUser())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void meReturnsCurrentAvatar() throws Exception {
        UserEntity user = user();
        user.setAvatar("/uploads/avatars/7-1710000000000.png");
        authenticate(user);

        mockMvc.perform(get("/api/auth/me")
                        .with(authentication(new UsernamePasswordAuthenticationToken(user, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(7))
                .andExpect(jsonPath("$.data.avatar").value("/uploads/avatars/7-1710000000000.png"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void quotaDelegatesToRateLimitService() throws Exception {
        when(rateLimitService.getAllRemaining(7L)).thenReturn(Map.of("CHAT", 10L));

        mockMvc.perform(get("/api/auth/quota").with(authentication(authUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.CHAT").value(10));
    }

    private UsernamePasswordAuthenticationToken authUser() {
        return new UsernamePasswordAuthenticationToken(user(), null);
    }

    private void authenticate(UserEntity user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
    }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setDisplayName("测试用户");
        user.setRole("USER");
        user.setDailyNotifyEnabled(true);
        return user;
    }
}
