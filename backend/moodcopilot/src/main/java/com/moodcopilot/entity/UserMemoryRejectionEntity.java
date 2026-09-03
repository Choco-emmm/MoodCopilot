package com.moodcopilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("user_memory_rejections")
public class UserMemoryRejectionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String memoryType;
    private String normalizedKey;
    private String normalizedValue;
    private String rejectionType;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getMemoryType() { return memoryType; }
    public void setMemoryType(String memoryType) { this.memoryType = memoryType; }
    public String getNormalizedKey() { return normalizedKey; }
    public void setNormalizedKey(String normalizedKey) { this.normalizedKey = normalizedKey; }
    public String getNormalizedValue() { return normalizedValue; }
    public void setNormalizedValue(String normalizedValue) { this.normalizedValue = normalizedValue; }
    public String getRejectionType() { return rejectionType; }
    public void setRejectionType(String rejectionType) { this.rejectionType = rejectionType; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
