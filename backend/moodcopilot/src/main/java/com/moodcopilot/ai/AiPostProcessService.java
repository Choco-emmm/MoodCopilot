package com.moodcopilot.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodcopilot.ai.mq.AiTaskMessage;
import com.moodcopilot.ai.mq.AiTaskProducer;
import com.moodcopilot.entity.DiaryEntity;
import com.moodcopilot.entity.DiaryKnowledgeGraphEntity;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.event.LifeEventService;
import com.moodcopilot.event.LifeChapterService;
import com.moodcopilot.mapper.DiaryKnowledgeGraphMapper;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.mapper.UserMapper;
import com.moodcopilot.notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AiPostProcessService {
    private final DiaryMapper diaryMapper;
    private final DiaryKnowledgeGraphMapper graphMapper;
    private final AiAnalysisService aiAnalysisService;
    private final VisionService visionService;
    private final MemoryExtractionService memoryExtractionService;
    private final LifeEventService lifeEventService;
    private final RagMemoryService ragMemoryService;
    private final NotificationService notificationService;
    private final UserMapper userMapper;
    private final TransactionTemplate transactionTemplate;
    private final AiTaskProducer aiTaskProducer;
    private final LifeChapterService lifeChapterService;

    public AiPostProcessService(DiaryMapper diaryMapper, DiaryKnowledgeGraphMapper graphMapper,
                                AiAnalysisService aiAnalysisService, VisionService visionService,
                                MemoryExtractionService memoryExtractionService, LifeEventService lifeEventService,
                                RagMemoryService ragMemoryService, NotificationService notificationService,
                                UserMapper userMapper, TransactionTemplate transactionTemplate,
                                AiTaskProducer aiTaskProducer, LifeChapterService lifeChapterService) {
        this.diaryMapper = diaryMapper;
        this.graphMapper = graphMapper;
        this.aiAnalysisService = aiAnalysisService;
        this.visionService = visionService;
        this.memoryExtractionService = memoryExtractionService;
        this.lifeEventService = lifeEventService;
        this.ragMemoryService = ragMemoryService;
        this.notificationService = notificationService;
        this.userMapper = userMapper;
        this.transactionTemplate = transactionTemplate;
        this.aiTaskProducer = aiTaskProducer;
        this.lifeChapterService = lifeChapterService;
    }

    public void process(String taskType, long diaryId, long userId) {
        process(taskType, diaryId, userId, null, null);
    }

    public void process(String taskType, long diaryId, long userId, String analysisVersion, String parentTaskId) {
        if (AiTaskMessage.TYPE_MEMORY_RAG_INDEX.equals(taskType)) {
            ragMemoryService.indexUserProfile(userId, memoryExtractionService.listUserMemories(userId));
            return;
        }
        DiaryEntity diary = diaryMapper.selectOne(new LambdaQueryWrapper<DiaryEntity>()
                .eq(DiaryEntity::getId, diaryId).eq(DiaryEntity::getAuthorUserId, userId));
        if (diary == null || Boolean.TRUE.equals(diary.getIsDeleted())) {
            throw new IllegalArgumentException("diary not found or not owned");
        }
        String imageDescriptions = needsImageDescriptions(taskType) ? describeImages(diary) : "";
        switch (taskType) {
            case AiTaskMessage.TYPE_MEMORY_EXTRACTION -> {
                    memoryExtractionService.extractAndSyncMemoryForDiary(userId, diaryId, diary.getContent(),
                            diary.getMusicMeta(), imageDescriptions,
                            diary.getCreatedAt() == null ? null : diary.getCreatedAt().toLocalDate());
                aiTaskProducer.submitMemoryRagTask(diaryId, userId, analysisVersion, parentTaskId);
            }
            case AiTaskMessage.TYPE_LIFE_EVENT_EXTRACTION ->
                    lifeEventService.extractAndTrackLifeEvents(userId, diaryId, diary.getContent(), diary.getCreatedAt());
            case AiTaskMessage.TYPE_DIARY_RAG_INDEX -> {
                ragMemoryService.indexDiary(userId, diaryId, diary.getContent(), diary.getMusicMeta());
                if (imageDescriptions != null && !imageDescriptions.isBlank()) {
                    ragMemoryService.indexDiaryImages(userId, diaryId, imageDescriptions);
                }
            }
            case AiTaskMessage.TYPE_GRAPH_EXTRACTION -> {
                processGraph(diary, userId, imageDescriptions);
                aiTaskProducer.submitGraphRagTask(diaryId, userId, analysisVersion, parentTaskId);
            }
            case AiTaskMessage.TYPE_GRAPH_RAG_INDEX -> indexGraph(diaryId, userId);
            case AiTaskMessage.TYPE_REPORT_INVALIDATION -> invalidateReports(userId);
            case AiTaskMessage.TYPE_NOTIFICATION -> notificationService.notifyGlobalEvent(userId,
                    "AI_ANALYSIS_COMPLETE", Map.of("diaryId", diaryId, "message", "日记分析已完成"));
            default -> throw new IllegalArgumentException("unsupported post-process task: " + taskType);
        }
        if (AiTaskMessage.TYPE_MEMORY_EXTRACTION.equals(taskType)) {
            lifeChapterService.markDirtyForDiary(userId, diaryId);
        }
    }

    private boolean needsImageDescriptions(String taskType) {
        return AiTaskMessage.TYPE_MEMORY_EXTRACTION.equals(taskType)
                || AiTaskMessage.TYPE_DIARY_RAG_INDEX.equals(taskType)
                || AiTaskMessage.TYPE_GRAPH_EXTRACTION.equals(taskType);
    }

    private void indexGraph(long diaryId, long userId) {
        graphMapper.selectList(new LambdaQueryWrapper<DiaryKnowledgeGraphEntity>()
                        .eq(DiaryKnowledgeGraphEntity::getDiaryId, diaryId)
                        .eq(DiaryKnowledgeGraphEntity::getUserId, userId))
                .forEach(entity -> ragMemoryService.indexKnowledgeGraph(userId, diaryId, entity.getId(),
                        entity.getHeadEntity(), entity.getRelation(), entity.getTailEntity()));
    }

    private void processGraph(DiaryEntity diary, long userId, String imageDescriptions) {
        long diaryId = diary.getId();
        List<AiAnalysisService.KnowledgeTriple> triples = aiAnalysisService.extractKnowledgeGraph(
                diary.getContent(), diary.getMusicMeta(), imageDescriptions);
        List<DiaryKnowledgeGraphEntity> oldTriples = graphMapper.selectList(new LambdaQueryWrapper<DiaryKnowledgeGraphEntity>()
                .eq(DiaryKnowledgeGraphEntity::getDiaryId, diaryId).eq(DiaryKnowledgeGraphEntity::getUserId, userId));
        List<DiaryKnowledgeGraphEntity> newEntities = new ArrayList<>();
        transactionTemplate.executeWithoutResult(status -> {
            graphMapper.delete(new LambdaQueryWrapper<DiaryKnowledgeGraphEntity>()
                    .eq(DiaryKnowledgeGraphEntity::getDiaryId, diaryId).eq(DiaryKnowledgeGraphEntity::getUserId, userId));
            for (AiAnalysisService.KnowledgeTriple triple : triples) {
                DiaryKnowledgeGraphEntity entity = new DiaryKnowledgeGraphEntity();
                entity.setUserId(userId); entity.setDiaryId(diaryId); entity.setHeadEntity(triple.head());
                entity.setRelation(triple.relation()); entity.setTailEntity(triple.tail());
                entity.setTailPolarity(triple.tailPolarity() == null ? 0 : triple.tailPolarity());
                entity.setCreatedAt(java.time.LocalDateTime.now()); graphMapper.insert(entity); newEntities.add(entity);
            }
        });
        oldTriples.forEach(old -> ragMemoryService.deleteKnowledgeGraph(old.getId()));
        UserEntity user = userMapper.selectById(userId);
        if (user != null && Boolean.TRUE.equals(user.getProfileNotifyEnabled())) {
            Set<String> oldSet = new HashSet<>();
            oldTriples.forEach(t -> oldSet.add(t.getHeadEntity() + "|" + t.getRelation() + "|" + t.getTailEntity()));
            Set<String> newSet = new HashSet<>();
            triples.forEach(t -> newSet.add(t.head() + "|" + t.relation() + "|" + t.tail()));
            if (!oldSet.equals(newSet)) {
                notificationService.notifyGlobalEvent(userId, "GRAPH_UPDATED", Map.of(
                        "message", "AI 已更新了新的事件因果关系", "diaryId", diaryId));
            }
        }
    }

    private String describeImages(DiaryEntity diary) {
        if (diary.getImages() == null || diary.getImages().isEmpty()) return "";
        return visionService.describeImages(diary.getImages(), diary.getImageMeta());
    }

    private void invalidateReports(long userId) {
        List<String> keys = new ArrayList<>();
        keys.add("coaching:" + userId);
        for (int offset = -4; offset <= 3; offset++) keys.add("report:%d:%d".formatted(userId, offset));
        for (int offset = -5; offset <= 0; offset++) keys.add("report:monthly:%d:%d".formatted(userId, offset));
        ragMemoryService.deleteKeys(keys);
    }
}
