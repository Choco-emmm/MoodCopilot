package com.moodcopilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("user_life_chapter_versions")
public class UserLifeChapterVersionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long chapterId;
    private Integer version;
    private String title;
    private String themeSummary;
    private String dominantMoodsJson;
    private String growthReflection;
    private String sourceSnapshotHash;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getThemeSummary() { return themeSummary; }
    public void setThemeSummary(String themeSummary) { this.themeSummary = themeSummary; }
    public String getDominantMoodsJson() { return dominantMoodsJson; }
    public void setDominantMoodsJson(String dominantMoodsJson) { this.dominantMoodsJson = dominantMoodsJson; }
    public String getGrowthReflection() { return growthReflection; }
    public void setGrowthReflection(String growthReflection) { this.growthReflection = growthReflection; }
    public String getSourceSnapshotHash() { return sourceSnapshotHash; }
    public void setSourceSnapshotHash(String sourceSnapshotHash) { this.sourceSnapshotHash = sourceSnapshotHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
