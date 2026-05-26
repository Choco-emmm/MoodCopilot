package com.moodcopilot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import com.moodcopilot.security.UserActivityInterceptor;
import com.moodcopilot.mapper.UserMapper;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public WebMvcConfig(UserMapper userMapper, StringRedisTemplate stringRedisTemplate) {
        this.userMapper = userMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new UserActivityInterceptor(userMapper, stringRedisTemplate))
                .addPathPatterns("/api/**");
    }
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        CacheControl avatarCache = CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/")
                .setCacheControl(avatarCache);
        registry.addResourceHandler("/api/uploads/**")
                .addResourceLocations("file:uploads/")
                .setCacheControl(avatarCache);
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(100);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("mvc-async-");
        executor.initialize();

        configurer.setTaskExecutor(executor);
        configurer.setDefaultTimeout(300000L);
    }
}
