package com.moodcopilot.ai.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodcopilot.entity.DiaryEntity;
import com.moodcopilot.mapper.DiaryMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class AiTaskProducer {
    private final AiTaskService taskService;
    private final DiaryMapper diaryMapper;

    public AiTaskProducer(AiTaskService taskService, DiaryMapper diaryMapper) {
        this.taskService = taskService;
        this.diaryMapper = diaryMapper;
    }

    public String submitDiaryAnalysisTask(long diaryId, long userId, boolean useReasoning) {
        return submitDiaryAnalysisTask(diaryId, userId, useReasoning, false);
    }

    public String submitDiaryAnalysisTask(long diaryId, long userId, boolean useReasoning, boolean forceRetry) {
        DiaryEntity diary = diaryMapper.selectOne(new LambdaQueryWrapper<DiaryEntity>()
                .eq(DiaryEntity::getId, diaryId).eq(DiaryEntity::getAuthorUserId, userId));
        String content = diary == null || diary.getContent() == null ? "" : diary.getContent();
        String requestedModel = useReasoning ? "deepseek-v4-pro" : "deepseek-v4-flash";
        String contentHash = DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8));
        String operationKey = forceRetry
                ? "diary:" + diaryId + ":analysis:" + contentHash + ":" + requestedModel + ":retry:" + java.util.UUID.randomUUID()
                : "diary:" + diaryId + ":analysis:" + contentHash + ":" + requestedModel;
        return taskService.enqueue(userId, AiTaskMessage.TYPE_DIARY_ANALYSIS, String.valueOf(diaryId), contentHash,
                requestedModel, operationKey,
                Map.of("useReasoning", useReasoning), null);
    }

    public void cancelPendingDiaryAnalysisTasks(long diaryId, long userId) {
        taskService.cancelPendingDiaryAnalysisTasks(diaryId, userId);
    }

    public void submitAnalysisPostProcessTasks(long diaryId, long userId, String analysisVersion,
                                               String requestedModel, String parentTaskId) {
        submit(userId, AiTaskMessage.TYPE_MEMORY_EXTRACTION, diaryId, analysisVersion, requestedModel,
                "memory", parentTaskId);
        submit(userId, AiTaskMessage.TYPE_LIFE_EVENT_EXTRACTION, diaryId, analysisVersion, requestedModel,
                "event", parentTaskId);
        submit(userId, AiTaskMessage.TYPE_GRAPH_EXTRACTION, diaryId, analysisVersion, requestedModel,
                "graph", parentTaskId);
        submit(userId, AiTaskMessage.TYPE_DIARY_RAG_INDEX, diaryId, analysisVersion, requestedModel,
                "rag", parentTaskId);
        submit(userId, AiTaskMessage.TYPE_REPORT_INVALIDATION, diaryId, analysisVersion, requestedModel,
                "report", parentTaskId);
        submit(userId, AiTaskMessage.TYPE_NOTIFICATION, diaryId, analysisVersion, requestedModel,
                "notification", parentTaskId);
    }

    public void submitMemoryRagTask(long diaryId, long userId, String analysisVersion, String parentTaskId) {
        submit(userId, AiTaskMessage.TYPE_MEMORY_RAG_INDEX, diaryId, analysisVersion, null,
                "memory-rag", parentTaskId);
    }

    public void submitGraphRagTask(long diaryId, long userId, String analysisVersion, String parentTaskId) {
        submit(userId, AiTaskMessage.TYPE_GRAPH_RAG_INDEX, diaryId, analysisVersion, null,
                "graph-rag", parentTaskId);
    }

    public void submitLifeChapterRefreshTask(Long chapterId, Long userId, String sourceSnapshotHash) {
        taskService.enqueue(userId, AiTaskMessage.TYPE_LIFE_CHAPTER_REFRESH, String.valueOf(chapterId),
                sourceSnapshotHash, null, "chapter:" + chapterId + ":refresh:" + sourceSnapshotHash,
                Map.of(), null);
    }

    private void submit(long userId, String taskType, long diaryId, String analysisVersion,
                        String requestedModel, String suffix, String parentTaskId) {
        taskService.enqueue(userId, taskType, String.valueOf(diaryId), analysisVersion, requestedModel,
                "diary:" + diaryId + ":" + suffix + ":" + analysisVersion, Map.of(), parentTaskId);
    }
}
