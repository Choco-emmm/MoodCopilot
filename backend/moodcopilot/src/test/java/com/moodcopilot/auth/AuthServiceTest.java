package com.moodcopilot.auth;

import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.mapper.UserMapper;
import com.moodcopilot.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private PasswordEncoder passwordEncoder;

    @TempDir
    private Path uploadRoot;

    @Test
    void uploadAvatarAllowsTwoMegabytesAndUsesCacheSafeFilename() throws Exception {
        when(userMapper.selectById(7L)).thenReturn(user());
        AuthService service = new AuthService(userMapper, jwtTokenProvider, passwordEncoder, uploadRoot);
        byte[] payload = new byte[2 * 1024 * 1024];

        String avatar = service.uploadAvatar(7L,
                new MockMultipartFile("file", "avatar.png", "image/png", payload));

        assertThat(avatar).startsWith("/uploads/avatars/7-").endsWith(".png");
        assertThat(Files.exists(uploadRoot.resolve(avatar.replace("/uploads/", "")))).isTrue();
        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).updateById(userCaptor.capture());
        assertThat(userCaptor.getValue().getAvatar()).isEqualTo(avatar);
    }

    @Test
    void uploadAvatarRejectsFilesOverTwoMegabytes() {
        AuthService service = new AuthService(userMapper, jwtTokenProvider, passwordEncoder, uploadRoot);

        assertThatThrownBy(() -> service.uploadAvatar(7L,
                new MockMultipartFile("file", "avatar.png", "image/png", new byte[2 * 1024 * 1024 + 1])))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("2MB");
    }

    @Test
    void uploadAvatarRejectsUnsupportedContentType() {
        AuthService service = new AuthService(userMapper, jwtTokenProvider, passwordEncoder, uploadRoot);

        assertThatThrownBy(() -> service.uploadAvatar(7L,
                new MockMultipartFile("file", "avatar.txt", "text/plain", "hello".getBytes())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("JPEG/PNG/WebP");
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
