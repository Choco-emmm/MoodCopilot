package com.moodcopilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("user_life_chapters")
public class UserLifeChapterEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String themeSummary;

    private LocalDate startDate;

    private LocalDate endDate;

    private String dominantMoodsJson;

    private String growthReflection;

    private Integer diaryCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String lifecycleStatus;
    private String generationStatus;
    private Integer currentVersion;
    private String sourceSnapshotHash;
    private LocalDateTime dirtySince;
    private LocalDateTime lastGeneratedAt;
    private String lastGenerationError;
    private Long lockVersion;
    private String segmentType;
    private Boolean isOpen;
    private String boundaryReason;
    private java.math.BigDecimal boundaryConfidence;
    private LocalDateTime lastSourceAt;
    private Long previousChapterId;
    private Long nextChapterId;

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

    public String getThemeSummary() {
        return themeSummary;
    }

    public void setThemeSummary(String themeSummary) {
        this.themeSummary = themeSummary;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getDominantMoodsJson() {
        return dominantMoodsJson;
    }

    public void setDominantMoodsJson(String dominantMoodsJson) {
        this.dominantMoodsJson = dominantMoodsJson;
    }

    public String getGrowthReflection() {
        return growthReflection;
    }

    public void setGrowthReflection(String growthReflection) {
        this.growthReflection = growthReflection;
    }

    public Integer getDiaryCount() {
        return diaryCount;
    }

    public void setDiaryCount(Integer diaryCount) {
        this.diaryCount = diaryCount;
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

    public String getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(String lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }
    public String getGenerationStatus() { return generationStatus; }
    public void setGenerationStatus(String generationStatus) { this.generationStatus = generationStatus; }
    public Integer getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(Integer currentVersion) { this.currentVersion = currentVersion; }
    public String getSourceSnapshotHash() { return sourceSnapshotHash; }
    public void setSourceSnapshotHash(String sourceSnapshotHash) { this.sourceSnapshotHash = sourceSnapshotHash; }
    public LocalDateTime getDirtySince() { return dirtySince; }
    public void setDirtySince(LocalDateTime dirtySince) { this.dirtySince = dirtySince; }
    public LocalDateTime getLastGeneratedAt() { return lastGeneratedAt; }
    public void setLastGeneratedAt(LocalDateTime lastGeneratedAt) { this.lastGeneratedAt = lastGeneratedAt; }
    public String getLastGenerationError() { return lastGenerationError; }
    public void setLastGenerationError(String lastGenerationError) { this.lastGenerationError = lastGenerationError; }
    public Long getLockVersion() { return lockVersion; }
    public void setLockVersion(Long lockVersion) { this.lockVersion = lockVersion; }
    public String getSegmentType() { return segmentType; }
    public void setSegmentType(String value) { segmentType = value; }
    public Boolean getIsOpen() { return isOpen; }
    public void setIsOpen(Boolean value) { isOpen = value; }
    public String getBoundaryReason() { return boundaryReason; }
    public void setBoundaryReason(String value) { boundaryReason = value; }
    public java.math.BigDecimal getBoundaryConfidence() { return boundaryConfidence; }
    public void setBoundaryConfidence(java.math.BigDecimal value) { boundaryConfidence = value; }
    public LocalDateTime getLastSourceAt() { return lastSourceAt; }
    public void setLastSourceAt(LocalDateTime value) { lastSourceAt = value; }
    public Long getPreviousChapterId() { return previousChapterId; }
    public void setPreviousChapterId(Long value) { previousChapterId = value; }
    public Long getNextChapterId() { return nextChapterId; }
    public void setNextChapterId(Long value) { nextChapterId = value; }
}
