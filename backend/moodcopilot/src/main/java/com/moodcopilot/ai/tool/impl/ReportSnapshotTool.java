package com.moodcopilot.ai.tool.impl;

import com.moodcopilot.ai.ReportSnapshotFunctionSupport;
import com.moodcopilot.ai.tool.ChatTool;
import com.moodcopilot.ai.tool.ToolExecutionContext;
import com.moodcopilot.diary.DiaryService;
import com.moodcopilot.diary.ReportSnapshotRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReportSnapshotTool extends ChatTool<ReportSnapshotRequest> {

    private final DiaryService diaryService;

    public ReportSnapshotTool(DiaryService diaryService) {
        this.diaryService = diaryService;
    }

    @Override
    public String name() {
        return ReportSnapshotFunctionSupport.NAME;
    }

    @Override
    public String description() {
        return "读取当前登录用户周报或月报的关键指标。period 可选 week/month，offset 可选（默认0）。返回主导象限、正向占比、高能量占比和日记数。";
    }

    @Override
    public Class<ReportSnapshotRequest> requestType() {
        return ReportSnapshotRequest.class;
    }

    @Override
    public LinkedHashMap<String, Object> properties() {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put("period", Map.of("type", "string", "description", "报告周期：week 或 month"));
        props.put("offset", Map.of("type", "integer", "description", "偏移量，0=当前，-1=上一期"));
        return props;
    }

    @Override
    public List<String> required() {
        return List.of("period", "offset");
    }

    @Override
    public Object execute(ReportSnapshotRequest request, ToolExecutionContext context) {
        return diaryService.getOwnReportSnapshot(request);
    }
}
