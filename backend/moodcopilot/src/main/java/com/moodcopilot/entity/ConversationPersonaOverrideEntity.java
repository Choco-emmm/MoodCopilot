package com.moodcopilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("conversation_persona_overrides")
public class ConversationPersonaOverrideEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long conversationId;
    private Integer version;
    private String role;
    private String toneJson;
    private String behaviorFlagsJson;
    private String disabledBehaviorFlagsJson;
    private String customDescription;
    private String customTone;
    private String customResponseStyle;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getToneJson() { return toneJson; }
    public void setToneJson(String toneJson) { this.toneJson = toneJson; }
    public String getBehaviorFlagsJson() { return behaviorFlagsJson; }
    public void setBehaviorFlagsJson(String behaviorFlagsJson) { this.behaviorFlagsJson = behaviorFlagsJson; }
    public String getDisabledBehaviorFlagsJson() { return disabledBehaviorFlagsJson; }
    public void setDisabledBehaviorFlagsJson(String disabledBehaviorFlagsJson) { this.disabledBehaviorFlagsJson = disabledBehaviorFlagsJson; }
    public String getCustomDescription() { return customDescription; }
    public void setCustomDescription(String customDescription) { this.customDescription = customDescription; }
    public String getCustomTone() { return customTone; }
    public void setCustomTone(String customTone) { this.customTone = customTone; }
    public String getCustomResponseStyle() { return customResponseStyle; }
    public void setCustomResponseStyle(String customResponseStyle) { this.customResponseStyle = customResponseStyle; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
