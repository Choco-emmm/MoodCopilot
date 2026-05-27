package com.moodcopilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai.prompts")
public class AiPromptProperties {

    private String analysisSystemPrompt;
    private String weeklySystemPrompt;
    private String graphExtractionSystemPrompt;
    private String customSummarySystemPrompt;
    private String monthlySystemPrompt;
    private String reportGuidanceSystemPrompt;
    private String coachingSystemPrompt;
    private String userContextSystemPrompt;
    private String encouragementSystemPrompt;

    private String chatCompressionSystemPrompt;
    private String agentToolsPrompt;
    private String welcomeTopicsSystemPrompt;
    private String queryRewritePrompt;

    public String getAnalysisSystemPrompt() {
        return analysisSystemPrompt;
    }

    public void setAnalysisSystemPrompt(String analysisSystemPrompt) {
        this.analysisSystemPrompt = analysisSystemPrompt;
    }

    public String getWeeklySystemPrompt() {
        return weeklySystemPrompt;
    }

    public void setWeeklySystemPrompt(String weeklySystemPrompt) {
        this.weeklySystemPrompt = weeklySystemPrompt;
    }

    public String getGraphExtractionSystemPrompt() {
        return graphExtractionSystemPrompt;
    }

    public void setGraphExtractionSystemPrompt(String graphExtractionSystemPrompt) {
        this.graphExtractionSystemPrompt = graphExtractionSystemPrompt;
    }

    public String getCustomSummarySystemPrompt() {
        return customSummarySystemPrompt;
    }

    public void setCustomSummarySystemPrompt(String customSummarySystemPrompt) {
        this.customSummarySystemPrompt = customSummarySystemPrompt;
    }

    public String getMonthlySystemPrompt() {
        return monthlySystemPrompt;
    }

    public void setMonthlySystemPrompt(String monthlySystemPrompt) {
        this.monthlySystemPrompt = monthlySystemPrompt;
    }

    public String getReportGuidanceSystemPrompt() {
        return reportGuidanceSystemPrompt;
    }

    public void setReportGuidanceSystemPrompt(String reportGuidanceSystemPrompt) {
        this.reportGuidanceSystemPrompt = reportGuidanceSystemPrompt;
    }

    public String getCoachingSystemPrompt() {
        return coachingSystemPrompt;
    }

    public void setCoachingSystemPrompt(String coachingSystemPrompt) {
        this.coachingSystemPrompt = coachingSystemPrompt;
    }

    public String getUserContextSystemPrompt() {
        return userContextSystemPrompt;
    }

    public void setUserContextSystemPrompt(String userContextSystemPrompt) {
        this.userContextSystemPrompt = userContextSystemPrompt;
    }

    public String getEncouragementSystemPrompt() {
        return encouragementSystemPrompt;
    }

    public void setEncouragementSystemPrompt(String encouragementSystemPrompt) {
        this.encouragementSystemPrompt = encouragementSystemPrompt;
    }

    public String getChatCompressionSystemPrompt() {
        return chatCompressionSystemPrompt;
    }

    public void setChatCompressionSystemPrompt(String chatCompressionSystemPrompt) {
        this.chatCompressionSystemPrompt = chatCompressionSystemPrompt;
    }

    public String getAgentToolsPrompt() {
        return agentToolsPrompt;
    }

    public void setAgentToolsPrompt(String agentToolsPrompt) {
        this.agentToolsPrompt = agentToolsPrompt;
    }

    public String getWelcomeTopicsSystemPrompt() {
        return welcomeTopicsSystemPrompt;
    }

    public void setWelcomeTopicsSystemPrompt(String welcomeTopicsSystemPrompt) {
        this.welcomeTopicsSystemPrompt = welcomeTopicsSystemPrompt;
    }

    public String getQueryRewritePrompt() {
        return queryRewritePrompt;
    }

    public void setQueryRewritePrompt(String queryRewritePrompt) {
        this.queryRewritePrompt = queryRewritePrompt;
    }
}
