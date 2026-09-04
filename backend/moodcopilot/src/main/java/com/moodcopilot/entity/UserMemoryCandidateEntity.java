package com.moodcopilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("user_memory_candidates")
public class UserMemoryCandidateEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String attributeKey;
    private String normalizedValue;
    private String attributeValue;
    private String memoryType;
    private String sourceType;
    private Double confidence;
    private Boolean isCore;
    private String status;
    private String evidenceSummary;
    private Long sourceDiaryId;
    private Long sourceConversationId;
    private LocalDate validFrom;
    private LocalDate validUntil;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long mergedIntoId;
    private String mergeReason;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getAttributeKey() { return attributeKey; }
    public void setAttributeKey(String attributeKey) { this.attributeKey = attributeKey; }
    public String getNormalizedValue() { return normalizedValue; }
    public void setNormalizedValue(String normalizedValue) { this.normalizedValue = normalizedValue; }
    public String getAttributeValue() { return attributeValue; }
    public void setAttributeValue(String attributeValue) { this.attributeValue = attributeValue; }
    public String getMemoryType() { return memoryType; }
    public void setMemoryType(String memoryType) { this.memoryType = memoryType; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public Boolean getIsCore() { return isCore; }
    public void setIsCore(Boolean isCore) { this.isCore = isCore; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getEvidenceSummary() { return evidenceSummary; }
    public void setEvidenceSummary(String evidenceSummary) { this.evidenceSummary = evidenceSummary; }
    public Long getSourceDiaryId() { return sourceDiaryId; }
    public void setSourceDiaryId(Long sourceDiaryId) { this.sourceDiaryId = sourceDiaryId; }
    public Long getSourceConversationId() { return sourceConversationId; }
    public void setSourceConversationId(Long sourceConversationId) { this.sourceConversationId = sourceConversationId; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Long getMergedIntoId() { return mergedIntoId; }
    public void setMergedIntoId(Long mergedIntoId) { this.mergedIntoId = mergedIntoId; }
    public String getMergeReason() { return mergeReason; }
    public void setMergeReason(String mergeReason) { this.mergeReason = mergeReason; }
}
