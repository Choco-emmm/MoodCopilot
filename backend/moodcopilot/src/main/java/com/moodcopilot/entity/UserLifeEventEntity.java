package com.moodcopilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@TableName("user_life_events")
public class UserLifeEventEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String description;

    private LocalDate targetDate;

    private LocalDate endDate;

    private LocalTime startTime;

    private LocalTime endTime;

    private String temporalPhase;

    private LocalDateTime nextFollowUpAt;

    private LocalDateTime lastFollowUpAt;

    private Integer followUpCount;

    private String followUpReason;

    private Boolean followUpCompleted;

    private java.math.BigDecimal importance;

    private String status;

    private String diaryIdsJson;

    private String titleAliasesJson;

    private Long lastDiaryId;

    private String followUpNote;

    private LocalDateTime createdAt;

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public void setTargetDate(LocalDate targetDate) {
        this.targetDate = targetDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public String getTemporalPhase() {
        return temporalPhase;
    }

    public void setTemporalPhase(String temporalPhase) {
        this.temporalPhase = temporalPhase;
    }

    public LocalDateTime getNextFollowUpAt() {
        return nextFollowUpAt;
    }

    public void setNextFollowUpAt(LocalDateTime nextFollowUpAt) {
        this.nextFollowUpAt = nextFollowUpAt;
    }

    public LocalDateTime getLastFollowUpAt() {
        return lastFollowUpAt;
    }

    public void setLastFollowUpAt(LocalDateTime lastFollowUpAt) {
        this.lastFollowUpAt = lastFollowUpAt;
    }

    public Integer getFollowUpCount() {
        return followUpCount;
    }

    public void setFollowUpCount(Integer followUpCount) {
        this.followUpCount = followUpCount;
    }

    public String getFollowUpReason() {
        return followUpReason;
    }

    public void setFollowUpReason(String followUpReason) {
        this.followUpReason = followUpReason;
    }

    public Boolean getFollowUpCompleted() {
        return followUpCompleted;
    }

    public void setFollowUpCompleted(Boolean followUpCompleted) {
        this.followUpCompleted = followUpCompleted;
    }

    public java.math.BigDecimal getImportance() {
        return importance;
    }

    public void setImportance(java.math.BigDecimal importance) {
        this.importance = importance;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDiaryIdsJson() {
        return diaryIdsJson;
    }

    public void setDiaryIdsJson(String diaryIdsJson) {
        this.diaryIdsJson = diaryIdsJson;
    }

    public String getTitleAliasesJson() {
        return titleAliasesJson;
    }

    public void setTitleAliasesJson(String titleAliasesJson) {
        this.titleAliasesJson = titleAliasesJson;
    }

    public Long getLastDiaryId() {
        return lastDiaryId;
    }

    public void setLastDiaryId(Long lastDiaryId) {
        this.lastDiaryId = lastDiaryId;
    }

    public String getFollowUpNote() {
        return followUpNote;
    }

    public void setFollowUpNote(String followUpNote) {
        this.followUpNote = followUpNote;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
