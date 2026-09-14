package com.moodcopilot.ai.tool.impl;

import com.moodcopilot.ai.UserStatsFunctionSupport;
import com.moodcopilot.ai.tool.ChatTool;
import com.moodcopilot.ai.tool.ToolExecutionContext;
import com.moodcopilot.diary.DiaryService;
import com.moodcopilot.diary.UserStatsRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UserStatsTool extends ChatTool<UserStatsRequest> {

    private final DiaryService diaryService;

    public UserStatsTool(DiaryService diaryService) {
        this.diaryService = diaryService;
    }

    @Override
    public String name() {
        return UserStatsFunctionSupport.NAME;
    }

    @Override
    public String description() {
        return "统计当前登录用户最近 N 天（默认 14 天）的日记与情绪分布，返回总日记数、情绪计数和高频主题。适合回答「我最近总是什么心情」这类问题。";
    }

    @Override
    public Class<UserStatsRequest> requestType() {
        return UserStatsRequest.class;
    }

    @Override
    public LinkedHashMap<String, Object> properties() {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put("days", Map.of("type", "integer", "description", "统计最近多少天，默认 14"));
        return props;
    }

    @Override
    public List<String> required() {
        return List.of("days");
    }

    @Override
    public Object execute(UserStatsRequest request, ToolExecutionContext context) {
        return diaryService.getOwnMoodStats(request);
    }
}
