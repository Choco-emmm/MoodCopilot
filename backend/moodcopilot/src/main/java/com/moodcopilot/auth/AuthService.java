package com.moodcopilot.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.mapper.UserMapper;
import com.moodcopilot.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Locale;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AuthService {

    private static final long MAX_AVATAR_SIZE = 10L * 1024 * 1024;

    private final UserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final Path uploadRoot;

    @Autowired
    public AuthService(UserMapper userMapper, JwtTokenProvider jwtTokenProvider,
            PasswordEncoder passwordEncoder) {
        this(userMapper, jwtTokenProvider, passwordEncoder, Path.of("uploads"));
    }

    AuthService(UserMapper userMapper, JwtTokenProvider jwtTokenProvider,
            PasswordEncoder passwordEncoder, Path uploadRoot) {
        this.userMapper = userMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.uploadRoot = uploadRoot;
    }

    public AuthResponse register(RegisterRequest request) {
        if (request.displayName() == null || request.displayName().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "用户名不能为空");
        }
        if (request.email() == null || !request.email().contains("@")) {
            throw new ResponseStatusException(BAD_REQUEST, "邮箱格式不正确");
        }
        if (request.password() == null || request.password().length() < 6) {
            throw new ResponseStatusException(BAD_REQUEST, "密码至少6位");
        }

        boolean exists = userMapper.exists(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getEmail, request.email()));
        if (exists) {
            throw new ResponseStatusException(BAD_REQUEST, "邮箱已被注册");
        }

        UserEntity user = new UserEntity();
        user.setDisplayName(request.displayName().trim());
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(1);
        user.setRole("USER");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail());
        return response(token, user);
    }

    public AuthResponse login(LoginRequest request) {
        if (request.email() == null || request.email().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "请输入邮箱");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "请输入密码");
        }

        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getEmail, request.email().trim().toLowerCase()));
        if (user == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "邮箱或密码错误");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(UNAUTHORIZED, "邮箱或密码错误");
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail());
        return response(token, user);
    }

    public AuthResponse updateProfile(Long userId, String displayName, String avatar) {
        UserEntity user = userMapper.selectById(userId);
        if (displayName != null && !displayName.isBlank()) {
            user.setDisplayName(displayName.trim());
        }
        if (avatar != null) {
            user.setAvatar(avatar);
        }
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return response(null, user);
    }

    public void updateSettings(Long userId, Boolean dailyNotifyEnabled) {
        UserEntity user = userMapper.selectById(userId);
        user.setDailyNotifyEnabled(dailyNotifyEnabled);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    public String uploadAvatar(Long userId, MultipartFile file) {
        if (file.isEmpty() || file.getSize() > MAX_AVATAR_SIZE) {
            throw new ResponseStatusException(BAD_REQUEST, "文件大小不能超过 2MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png")
                && !contentType.equals("image/webp"))) {
            throw new ResponseStatusException(BAD_REQUEST, "仅支持 JPEG/PNG/WebP 格式");
        }
        String ext = contentType.equals("image/png") ? "png" : contentType.equals("image/webp") ? "webp" : "jpg";
        String filename = userId + "-" + System.currentTimeMillis() + "." + ext;
        Path uploadDir = uploadRoot.resolve("avatars");
        try {
            Files.createDirectories(uploadDir);
            Files.write(uploadDir.resolve(filename), file.getBytes(), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "头像上传失败");
        }
        String avatarUrl = "/api/uploads/avatars/" + filename;
        UserEntity user = userMapper.selectById(userId);
        user.setAvatar(avatarUrl);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return avatarUrl;
    }

    public AuthResponse me(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "登录状态已失效");
        }
        return response(null, user);
    }

    private AuthResponse response(String token, UserEntity user) {
        String role = user.getRole() == null || user.getRole().isBlank() ? "USER" : user.getRole();
        return new AuthResponse(token, user.getId(), user.getDisplayName(), normalizeAvatar(user.getAvatar()),
                user.getDailyNotifyEnabled(), role);
    }

    private String normalizeAvatar(String avatar) {
        if (avatar == null || avatar.isBlank()) {
            return avatar;
        }
        String normalized = avatar.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("data:")) {
            return normalized;
        }
        if (normalized.startsWith("/api/uploads/")) {
            return normalized;
        }
        if (normalized.startsWith("/uploads/")) {
            return "/api" + normalized;
        }
        if (normalized.startsWith("uploads/")) {
            return "/api/" + normalized;
        }
        return normalized;
    }
}
