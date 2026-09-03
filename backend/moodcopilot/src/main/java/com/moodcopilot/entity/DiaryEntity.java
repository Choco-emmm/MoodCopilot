package com.moodcopilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import java.time.LocalDateTime;

@TableName(value = "diaries", autoResultMap = true)
public class DiaryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long authorUserId;
    private String authorName;
    private String content;
    private String visibility;
    private Integer resonanceCount;
    @TableLogic
    @TableField("is_deleted")
    private Boolean isDeleted;
    private Boolean isPinned;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private MusicMeta musicMeta;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private java.util.List<String> images;
    @TableField(value = "image_meta", typeHandler = JacksonTypeHandler.class)
    private java.util.List<DiaryImageMeta> imageMeta;
    private String analysisStatus;
    private String analysisError;
    private String requestedModel;
    private String actualModel;
    private String fallbackReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAuthorUserId() {
        return authorUserId;
    }

    public void setAuthorUserId(Long authorUserId) {
        this.authorUserId = authorUserId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public Integer getResonanceCount() {
        return resonanceCount;
    }

    public void setResonanceCount(Integer resonanceCount) {
        this.resonanceCount = resonanceCount;
    }

    public Boolean getIsPinned() {
        return isPinned;
    }

    public void setIsPinned(Boolean isPinned) {
        this.isPinned = isPinned;
    }

    public MusicMeta getMusicMeta() {
        return musicMeta;
    }

    public void setMusicMeta(MusicMeta musicMeta) {
        this.musicMeta = musicMeta;
    }

    public java.util.List<String> getImages() {
        return images;
    }

    public void setImages(java.util.List<String> images) {
        this.images = images;
    }

    public java.util.List<DiaryImageMeta> getImageMeta() {
        return imageMeta;
    }

    public void setImageMeta(java.util.List<DiaryImageMeta> imageMeta) {
        this.imageMeta = imageMeta;
    }

    public String getAnalysisStatus() { return analysisStatus; }
    public void setAnalysisStatus(String analysisStatus) { this.analysisStatus = analysisStatus; }
    public String getAnalysisError() { return analysisError; }
    public void setAnalysisError(String analysisError) { this.analysisError = analysisError; }
    public String getRequestedModel() { return requestedModel; }
    public void setRequestedModel(String requestedModel) { this.requestedModel = requestedModel; }
    public String getActualModel() { return actualModel; }
    public void setActualModel(String actualModel) { this.actualModel = actualModel; }
    public String getFallbackReason() { return fallbackReason; }
    public void setFallbackReason(String fallbackReason) { this.fallbackReason = fallbackReason; }

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
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
