package com.moodcopilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("user_onboarding")
public class UserOnboardingEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String guideKey;
    private Integer guideVersion;
    private String scope;
    private String status;
    private LocalDateTime completedAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getGuideKey() {
        return guideKey;
    }

    public void setGuideKey(String guideKey) {
        this.guideKey = guideKey;
    }

    public Integer getGuideVersion() {
        return guideVersion;
    }

    public void setGuideVersion(Integer guideVersion) {
        this.guideVersion = guideVersion;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
