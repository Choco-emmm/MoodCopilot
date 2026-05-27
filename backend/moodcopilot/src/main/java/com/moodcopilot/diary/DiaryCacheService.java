package com.moodcopilot.diary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.diary.DiaryView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.time.Duration;
import java.util.function.Supplier;

@Service
public class DiaryCacheService {

    private static final Logger log = LoggerFactory.getLogger(DiaryCacheService.class);
    
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public DiaryCacheService(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Page<DiaryView> getCachedPage(String cacheKey, Supplier<Page<DiaryView>> dbQuerySupplier) {
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<Page<DiaryView>>() {});
            }
        } catch (Exception e) {
            log.debug("Cache miss/error {}", cacheKey, e);
        }

        Page<DiaryView> result = dbQuerySupplier.get();

        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result), Duration.ofMinutes(5));
        } catch (Exception e) {
            log.debug("Cache write failed for {}", cacheKey, e);
        }
        return result;
    }
}
