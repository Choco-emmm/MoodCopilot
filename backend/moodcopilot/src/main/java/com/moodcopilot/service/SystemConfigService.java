package com.moodcopilot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.SystemConfigEntity;
import com.moodcopilot.mapper.SystemConfigMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class SystemConfigService {
    private final SystemConfigMapper systemConfigMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public SystemConfigService(SystemConfigMapper systemConfigMapper, StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.systemConfigMapper = systemConfigMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public <T> T getConfig(String key, TypeReference<T> typeReference, T defaultValue) {
        String cacheKey = "sys_config:" + key;
        String val = redisTemplate.opsForValue().get(cacheKey);
        if (val != null) {
            try {
                return objectMapper.readValue(val, typeReference);
            } catch (JsonProcessingException e) {
                // ignore and fetch from DB
            }
        }

        SystemConfigEntity entity = systemConfigMapper.selectOne(new LambdaQueryWrapper<SystemConfigEntity>()
                .eq(SystemConfigEntity::getConfigKey, key));

        if (entity != null && entity.getConfigValue() != null) {
            try {
                T result = objectMapper.readValue(entity.getConfigValue(), typeReference);
                redisTemplate.opsForValue().set(cacheKey, entity.getConfigValue());
                return result;
            } catch (JsonProcessingException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public <T> void setConfig(String key, T value) {
        try {
            String jsonValue = objectMapper.writeValueAsString(value);
            SystemConfigEntity entity = systemConfigMapper.selectOne(new LambdaQueryWrapper<SystemConfigEntity>()
                    .eq(SystemConfigEntity::getConfigKey, key));
            
            if (entity == null) {
                entity = new SystemConfigEntity();
                entity.setConfigKey(key);
                entity.setConfigValue(jsonValue);
                entity.setCreateTime(LocalDateTime.now());
                entity.setUpdateTime(LocalDateTime.now());
                systemConfigMapper.insert(entity);
            } else {
                entity.setConfigValue(jsonValue);
                entity.setUpdateTime(LocalDateTime.now());
                systemConfigMapper.updateById(entity);
            }
            String cacheKey = "sys_config:" + key;
            redisTemplate.opsForValue().set(cacheKey, jsonValue);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize config", e);
        }
    }
}
