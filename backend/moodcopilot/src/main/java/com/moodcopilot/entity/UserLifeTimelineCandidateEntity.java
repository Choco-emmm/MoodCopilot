package com.moodcopilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("user_life_timeline_candidates")
public class UserLifeTimelineCandidateEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long leftChapterId;
    private Long rightChapterId;
    private LocalDate suggestedStartDate;
    private LocalDate suggestedEndDate;
    private String reason;
    private java.math.BigDecimal confidence;
    private String sourceDiaryIdsJson;
    private String sourceEventIdsJson;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { userId = value; }
    public Long getLeftChapterId() { return leftChapterId; }
    public void setLeftChapterId(Long value) { leftChapterId = value; }
    public Long getRightChapterId() { return rightChapterId; }
    public void setRightChapterId(Long value) { rightChapterId = value; }
    public LocalDate getSuggestedStartDate() { return suggestedStartDate; }
    public void setSuggestedStartDate(LocalDate value) { suggestedStartDate = value; }
    public LocalDate getSuggestedEndDate() { return suggestedEndDate; }
    public void setSuggestedEndDate(LocalDate value) { suggestedEndDate = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public java.math.BigDecimal getConfidence() { return confidence; }
    public void setConfidence(java.math.BigDecimal value) { confidence = value; }
    public String getSourceDiaryIdsJson() { return sourceDiaryIdsJson; }
    public void setSourceDiaryIdsJson(String value) { sourceDiaryIdsJson = value; }
    public String getSourceEventIdsJson() { return sourceEventIdsJson; }
    public void setSourceEventIdsJson(String value) { sourceEventIdsJson = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime value) { resolvedAt = value; }
}
