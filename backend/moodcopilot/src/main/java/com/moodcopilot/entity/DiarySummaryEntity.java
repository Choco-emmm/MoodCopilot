package com.moodcopilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("diary_summaries")
public class DiarySummaryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String reportType;
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private String aiSummary;
    private String insightsJson;
    private String suggestionsJson;
    private String followUpPrompt;
    private String moodsJson;
    private String topicsJson;
    private Integer diaryCount;
    private String diaryIds;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public String getAiSummary() { return aiSummary; }
    public void setAiSummary(String aiSummary) { this.aiSummary = aiSummary; }

    public String getInsightsJson() { return insightsJson; }
    public void setInsightsJson(String insightsJson) { this.insightsJson = insightsJson; }

    public String getSuggestionsJson() { return suggestionsJson; }
    public void setSuggestionsJson(String suggestionsJson) { this.suggestionsJson = suggestionsJson; }

    public String getFollowUpPrompt() { return followUpPrompt; }
    public void setFollowUpPrompt(String followUpPrompt) { this.followUpPrompt = followUpPrompt; }

    public String getMoodsJson() { return moodsJson; }
    public void setMoodsJson(String moodsJson) { this.moodsJson = moodsJson; }

    public String getTopicsJson() { return topicsJson; }
    public void setTopicsJson(String topicsJson) { this.topicsJson = topicsJson; }

    public Integer getDiaryCount() { return diaryCount; }
    public void setDiaryCount(Integer diaryCount) { this.diaryCount = diaryCount; }

    public String getDiaryIds() { return diaryIds; }
    public void setDiaryIds(String diaryIds) { this.diaryIds = diaryIds; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
