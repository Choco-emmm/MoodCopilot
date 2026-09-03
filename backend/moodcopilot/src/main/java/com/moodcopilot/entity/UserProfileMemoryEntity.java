package com.moodcopilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import java.time.LocalDate;

@TableName("user_profile_memory")
public class UserProfileMemoryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String attributeKey;
    private String attributeValue;
    private LocalDateTime updateTime;
    private Boolean isCore = false;
    private String memoryType = "preference";
    private String sourceType = "system";
    private Long sourceDiaryId;
    private Long sourceConversationId;
    private Double confidence = 0.5;
    private LocalDate validFrom;
    private LocalDate validUntil;
    private LocalDateTime lastEvidenceAt;
    private String status = "active";
    private Long previousMemoryId;
    private LocalDateTime supersededAt;
    private String supersededReason;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getAttributeKey() { return attributeKey; }
    public void setAttributeKey(String attributeKey) { this.attributeKey = attributeKey; }

    public String getAttributeValue() { return attributeValue; }
    public void setAttributeValue(String attributeValue) { this.attributeValue = attributeValue; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public Boolean getIsCore() { return isCore; }
    public void setIsCore(Boolean isCore) { this.isCore = isCore; }

    public String getMemoryType() { return memoryType; }
    public void setMemoryType(String memoryType) { this.memoryType = memoryType; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getSourceDiaryId() { return sourceDiaryId; }
    public void setSourceDiaryId(Long sourceDiaryId) { this.sourceDiaryId = sourceDiaryId; }
    public Long getSourceConversationId() { return sourceConversationId; }
    public void setSourceConversationId(Long sourceConversationId) { this.sourceConversationId = sourceConversationId; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }
    public LocalDateTime getLastEvidenceAt() { return lastEvidenceAt; }
    public void setLastEvidenceAt(LocalDateTime lastEvidenceAt) { this.lastEvidenceAt = lastEvidenceAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getPreviousMemoryId() { return previousMemoryId; }
    public void setPreviousMemoryId(Long previousMemoryId) { this.previousMemoryId = previousMemoryId; }
    public LocalDateTime getSupersededAt() { return supersededAt; }
    public void setSupersededAt(LocalDateTime supersededAt) { this.supersededAt = supersededAt; }
    public String getSupersededReason() { return supersededReason; }
    public void setSupersededReason(String supersededReason) { this.supersededReason = supersededReason; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
