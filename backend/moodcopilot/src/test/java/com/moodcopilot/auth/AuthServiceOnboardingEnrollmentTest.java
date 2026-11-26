package com.moodcopilot.auth;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.mapper.UserMapper;
import com.moodcopilot.notification.NotificationService;
import com.moodcopilot.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceOnboardingEnrollmentTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JavaMailSender javaMailSender;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ImageCaptchaApplication captchaApplication;
    @Mock
    private NotificationService notificationService;

    private TestableAuthService authService;

    @BeforeEach
    void setup() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(jwtTokenProvider.generateToken(any(), any())).thenReturn("jwt-token");
        authService = new TestableAuthService(
                userMapper, jwtTokenProvider, passwordEncoder, Path.of("uploads"),
                javaMailSender, redisTemplate, captchaApplication, notificationService);
    }

    @Test
    void registerSetsOnboardingEnrolledVersionToOneOnInsert() {
        when(valueOperations.get("email:code:alice@example.com")).thenReturn("123456");
        when(userMapper.exists(any())).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed");
        doAnswer(invocation -> {
            UserEntity inserted = invocation.getArgument(0);
            inserted.setId(101L);
            return 1;
        }).when(userMapper).insert(any(UserEntity.class));

        authService.register(new RegisterRequest("alice01", "alice@example.com", "secret123", "123456", null));

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getOnboardingEnrolledVersion());
    }

    @Test
    void wxLoginSetsOnboardingEnrolledVersionOnlyForNewlyCreatedUser() {
        authService.nextWechatSession = Map.of("openid", "wx-new-openid");
        when(userMapper.findByWxOpenId("wx-new-openid")).thenReturn(null);
        doAnswer(invocation -> {
            UserEntity inserted = invocation.getArgument(0);
            inserted.setId(201L);
            return 1;
        }).when(userMapper).insert(any(UserEntity.class));

        authService.wxLogin("code-new");

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getOnboardingEnrolledVersion());
    }

    @Test
    void wxLoginDoesNotResetExistingUserEnrollment() {
        authService.nextWechatSession = Map.of("openid", "wx-old-openid");
        UserEntity existing = new UserEntity();
        existing.setId(202L);
        existing.setEmail("old@wx.com");
        existing.setStatus(1);
        existing.setOnboardingEnrolledVersion(0);
        when(userMapper.findByWxOpenId("wx-old-openid")).thenReturn(existing);

        authService.wxLogin("code-existing");

        verify(userMapper, never()).insert(any(UserEntity.class));
        verify(userMapper, never()).updateById(any(UserEntity.class));
        assertEquals(0, existing.getOnboardingEnrolledVersion());
    }

    @Test
    void loginResetPasswordAndBindEmailDoNotResetExistingUserEnrollment() {
        UserEntity existing = new UserEntity();
        existing.setId(301L);
        existing.setEmail("legacy@example.com");
        existing.setPasswordHash("hashed");
        existing.setStatus(1);
        existing.setOnboardingEnrolledVersion(0);

        // Ordinary login
        when(valueOperations.get("login:locked:legacy@example.com")).thenReturn(null);
        when(userMapper.selectOne(any())).thenReturn(existing);
        when(passwordEncoder.matches("secret123", "hashed")).thenReturn(true);
        authService.login(new LoginRequest("legacy@example.com", "secret123", null));
        assertEquals(0, existing.getOnboardingEnrolledVersion());

        // Reset password
        when(valueOperations.get("email:reset:code:legacy@example.com")).thenReturn("654321");
        when(passwordEncoder.encode("newpass123")).thenReturn("new-hashed");
        authService.resetPassword(new ResetPasswordRequest("legacy@example.com", "654321", "newpass123", "newpass123"));
        assertEquals(0, existing.getOnboardingEnrolledVersion());

        // Non-merge bindEmail
        when(valueOperations.get("email:bind:code:bound@example.com")).thenReturn("999999");
        when(userMapper.selectOne(any())).thenReturn(null);
        when(userMapper.selectById(301L)).thenReturn(existing);
        authService.bindEmail(301L, "bound@example.com", "999999");
        assertEquals(0, existing.getOnboardingEnrolledVersion());
    }

    private static final class TestableAuthService extends AuthService {
        Map<?, ?> nextWechatSession = Map.of();

        TestableAuthService(UserMapper userMapper, JwtTokenProvider jwtTokenProvider,
                            PasswordEncoder passwordEncoder, Path uploadRoot,
                            JavaMailSender javaMailSender, StringRedisTemplate redisTemplate,
                            ImageCaptchaApplication captchaApplication, NotificationService notificationService) {
            super(userMapper, jwtTokenProvider, passwordEncoder, uploadRoot,
                    javaMailSender, redisTemplate, captchaApplication, notificationService,
                    "noreply@test.local", false, "app-id", "app-secret");
        }

        @Override
        Map<?, ?> fetchWechatSession(String code) {
            return nextWechatSession;
        }
    }
}
