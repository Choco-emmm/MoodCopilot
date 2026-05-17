package com.moodcopilot.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.mapper.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserMapper userMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userMapper = userMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        String uri = request.getRequestURI();
        if (token != null) {
            log.debug("JWT Token 提取成功，uri={}, tokenPrefix={}...", uri, token.substring(0, Math.min(8, token.length())));
        } else {
            log.debug("JWT Token 未提取到，uri={}", uri);
        }
        if (token != null && jwtTokenProvider.validateToken(token)) {
            Long userId = jwtTokenProvider.getUserId(token);
            UserEntity user = userMapper.selectById(userId);
            if (user != null) {
                if (user.getStatus() != null && user.getStatus() == 0) {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    new ObjectMapper().writeValue(response.getWriter(),
                            Map.of("code", 403, "message", "该账号已被封禁，请联系管理员"));
                    return;
                }
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        // 优先从 Authorization header 提取
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        // WebSocket 握手无法携带自定义 header，从 query 参数提取
        if (request.getRequestURI().startsWith("/ws/")) {
            String token = request.getParameter("token");
            if (StringUtils.hasText(token)) {
                return token.trim();
            }
        }
        return null;
    }
}
