package com.moodcopilot.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import java.time.LocalDateTime;
import java.util.List;

@TableName(value = "diary_analysis", autoResultMap = true)
public class DiaryAnalysisEntity {

    @TableId
    private Long diaryId;
    private String moodLabel;
    private Integer moodIntensity;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> topicLabelsJson;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> secondaryMoodsJson;
    private String summary;
    private String feedback;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getDiaryId() { return diaryId; }
    public void setDiaryId(Long diaryId) { this.diaryId = diaryId; }

    public String getMoodLabel() { return moodLabel; }
    public void setMoodLabel(String moodLabel) { this.moodLabel = moodLabel; }

    public Integer getMoodIntensity() { return moodIntensity; }
    public void setMoodIntensity(Integer moodIntensity) { this.moodIntensity = moodIntensity; }

    public List<String> getTopicLabelsJson() { return topicLabelsJson; }
    public void setTopicLabelsJson(List<String> topicLabelsJson) { this.topicLabelsJson = topicLabelsJson; }

    public List<String> getSecondaryMoodsJson() { return secondaryMoodsJson; }
    public void setSecondaryMoodsJson(List<String> secondaryMoodsJson) { this.secondaryMoodsJson = secondaryMoodsJson; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
