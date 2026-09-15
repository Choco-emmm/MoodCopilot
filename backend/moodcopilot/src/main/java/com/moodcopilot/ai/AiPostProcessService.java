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
import com.moodcopilot.mapper.DiaryAnalysisMapper;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.mapper.UserMapper;
import com.moodcopilot.notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiPostProcessService {
    private final DiaryMapper diaryMapper;
    private final DiaryAnalysisMapper diaryAnalysisMapper;
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
        this(diaryMapper, null, graphMapper, aiAnalysisService, visionService, memoryExtractionService,
                lifeEventService, ragMemoryService, notificationService, userMapper, transactionTemplate,
                aiTaskProducer, lifeChapterService);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AiPostProcessService(DiaryMapper diaryMapper, DiaryAnalysisMapper diaryAnalysisMapper,
                                DiaryKnowledgeGraphMapper graphMapper,
                                AiAnalysisService aiAnalysisService, VisionService visionService,
                                MemoryExtractionService memoryExtractionService, LifeEventService lifeEventService,
                                RagMemoryService ragMemoryService, NotificationService notificationService,
                                UserMapper userMapper, TransactionTemplate transactionTemplate,
                                AiTaskProducer aiTaskProducer, LifeChapterService lifeChapterService) {
        this.diaryMapper = diaryMapper;
        this.diaryAnalysisMapper = diaryAnalysisMapper;
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
        String imageDescriptions = needsImageDescriptions(taskType, diaryId) ? describeImages(diary) : "";
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

    private boolean needsImageDescriptions(String taskType, long diaryId) {
        boolean memorySignalsAlreadySaved = false;
        if (AiTaskMessage.TYPE_MEMORY_EXTRACTION.equals(taskType) && diaryAnalysisMapper != null) {
            var analysis = diaryAnalysisMapper.selectById(diaryId);
            memorySignalsAlreadySaved = analysis != null && analysis.getMemorySignalsJson() != null;
        }
        return (AiTaskMessage.TYPE_MEMORY_EXTRACTION.equals(taskType) && !memorySignalsAlreadySaved)
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
                userId, diary.getContent(), diary.getMusicMeta(), imageDescriptions);
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
            Map<String, Map<String, Object>> oldByKey = new LinkedHashMap<>();
            oldTriples.forEach(t -> oldByKey.put(tripleKey(t.getHeadEntity(), t.getRelation(), t.getTailEntity()),
                    tripleNode(t.getHeadEntity(), t.getRelation(), t.getTailEntity())));
            Map<String, Map<String, Object>> newByKey = new LinkedHashMap<>();
            triples.forEach(t -> newByKey.put(tripleKey(t.head(), t.relation(), t.tail()),
                    tripleNode(t.head(), t.relation(), t.tail())));
            if (!oldByKey.keySet().equals(newByKey.keySet())) {
                List<Map<String, Object>> added = newByKey.entrySet().stream()
                        .filter(e -> !oldByKey.containsKey(e.getKey())).map(Map.Entry::getValue).toList();
                List<Map<String, Object>> deleted = oldByKey.entrySet().stream()
                        .filter(e -> !newByKey.containsKey(e.getKey())).map(Map.Entry::getValue).toList();
                List<String> changes = new ArrayList<>();
                if (!added.isEmpty()) changes.add("新增 " + added.size() + " 条");
                if (!deleted.isEmpty()) changes.add("移除 " + deleted.size() + " 条");
                notificationService.notifyGlobalEvent(userId, "GRAPH_UPDATED", Map.of(
                        "message", String.join("、", changes) + "因果关系",
                        "diaryId", diaryId,
                        "diff", Map.of("added", added, "deleted", deleted)));
            }
        }
    }

    private static String tripleKey(String head, String relation, String tail) {
        return head + "|" + relation + "|" + tail;
    }

    /** 弹窗按 head/relation/tail 渲染变更行，字段名要和前端约定的一致。 */
    private static Map<String, Object> tripleNode(String head, String relation, String tail) {
        Map<String, Object> node = new HashMap<>();
        node.put("head", head);
        node.put("relation", relation);
        node.put("tail", tail);
        return node;
    }

    private String describeImages(DiaryEntity diary) {
        if (diary.getImages() == null || diary.getImages().isEmpty()) return "";
        return visionService.describeImages(diary.getImages(), diary.getImageMeta(), diary.getContent());
    }

    private void invalidateReports(long userId) {
        List<String> keys = new ArrayList<>();
        keys.add("coaching:" + userId);
        for (int offset = -4; offset <= 3; offset++) keys.add("report:%d:%d".formatted(userId, offset));
        for (int offset = -5; offset <= 0; offset++) keys.add("report:monthly:%d:%d".formatted(userId, offset));
        ragMemoryService.deleteKeys(keys);
    }
}
