package com.moodcopilot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/**
 * 全局 IP 级滑动窗口限流。
 * 分两档：auth 敏感端点 10 次/分钟，普通 API 60 次/分钟，静态资源豁免。
 */
public class IpRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IpRateLimitFilter.class);
    private static final String PREFIX = "ratelimit:ip:";
    private static final int AUTH_LIMIT = 10;
    private static final int API_LIMIT = 60;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redis;

    public IpRateLimitFilter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (isExempt(path)) {
            chain.doFilter(request, response);
            return;
        }

        String ip = resolveClientIp(request);
        String category = isAuthEndpoint(path) ? "auth" : "api";
        int limit = category.equals("auth") ? AUTH_LIMIT : API_LIMIT;
        String key = PREFIX + ip + ":" + category;

        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW.toMillis();
        String member = now + ":" + UUID.randomUUID().toString().substring(0, 8);

        try {
            redis.opsForZSet().removeRangeByScore(key, 0, windowStart);
            redis.opsForZSet().add(key, member, now);
            redis.expire(key, WINDOW.plusSeconds(10));

            Long count = redis.opsForZSet().zCard(key);
            if (count != null && count > limit) {
                log.warn("IP 限流触发 ip={} path={} count={} limit={}", ip, path, count, limit);
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(WINDOW.toSeconds()));
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
                return;
            }
        } catch (Exception e) {
            log.warn("IP 限流 Redis 操作异常，放行: {}", e.getMessage());
        }

        chain.doFilter(request, response);
    }

    private boolean isExempt(String path) {
        return path.startsWith("/uploads/")
                || path.startsWith("/api/uploads/")
                || path.equals("/api/health")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/ws/");
    }

    private boolean isAuthEndpoint(String path) {
        return path.equals("/api/auth/login")
                || path.equals("/api/auth/register")
                || path.equals("/api/auth/send-code");
    }

    /**
     * 获取客户端真实 IP，优先从反向代理头读取。
     */
    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) {
            return ip.trim();
        }
        ip = request.getRemoteAddr();
        return ip != null ? ip : "unknown";
    }
}
