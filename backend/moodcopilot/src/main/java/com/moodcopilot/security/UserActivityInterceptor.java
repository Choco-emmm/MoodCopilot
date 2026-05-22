package com.moodcopilot.security;

import com.moodcopilot.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

public class UserActivityInterceptor implements HandlerInterceptor {

    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public UserActivityInterceptor(UserMapper userMapper, StringRedisTemplate stringRedisTemplate) {
        this.userMapper = userMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !authentication.getPrincipal().equals("anonymousUser")) {
            Long userId = null;
            try {
                userId = Long.valueOf(authentication.getName());
            } catch (NumberFormatException e) {
                return true;
            }

            if (userId != null) {
                String cacheKey = "user:activity:update_time:" + userId;
                if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(cacheKey))) {
                    try {
                        userMapper.updateLastActiveTime(userId, LocalDateTime.now());
                        stringRedisTemplate.opsForValue().set(cacheKey, "1", 5, TimeUnit.MINUTES);
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
        }
        return true;
    }
}
