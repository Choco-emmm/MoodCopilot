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
}
