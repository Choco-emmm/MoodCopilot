package com.moodcopilot.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.mapper.UserMapper;
import com.moodcopilot.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserMapper userMapper, JwtTokenProvider jwtTokenProvider,
                       PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
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
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(token, user.getId(), user.getDisplayName());
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
        return new AuthResponse(token, user.getId(), user.getDisplayName());
    }
}
