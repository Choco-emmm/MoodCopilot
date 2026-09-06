package com.moodcopilot.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.DiaryKnowledgeGraphEntity;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.graph.GraphService;
import com.moodcopilot.mapper.UserMapper;
import com.moodcopilot.mapper.DiaryKnowledgeGraphMapper;
import com.moodcopilot.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GraphConsolidationService {

    private static final Logger log = LoggerFactory.getLogger(GraphConsolidationService.class);
    private static final String RESULT_KEY_PREFIX = "ai:consolidation:graph:result:";

    private static final String CONSOLIDATION_PROMPT = """
            你是一个可审计的图谱去重助手。只允许合并完全相同或明确同义的三元组，不得删除事实、改写因果含义或合并相反极性。
            每个结果必须带上来自输入的 sourceTripleIds 和 sourceDiaryIds。无法证明重复的关系请原样保留，不要输出新的无来源事实。
            只输出 JSON：{"triples":[{"headEntity":"...","relation":"...","tailEntity":"...","tailPolarity":1,"sourceTripleIds":[1,2],"sourceDiaryIds":[10,11],"operation":"MERGE"}]}
            operation 只能是 MERGE、DEDUP、NORMALIZE。不要输出 markdown 或解释文字。
            """;

    private final ChatClient chatClient;
    private final DiaryKnowledgeGraphMapper graphMapper;
    private final GraphService graphService;
    private final ObjectMapper objectMapper;
    private final TransactionOperations transactionOperations;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final RagMemoryService ragMemoryService;
    private final NotificationService notificationService;
    private final UserMapper userMapper;
    private final PromptComposer promptComposer;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ContextMetadataRecorder contextMetadataRecorder;

    public GraphConsolidationService(@Qualifier("analysisChatClient") ChatClient chatClient,
            DiaryKnowledgeGraphMapper graphMapper,
            GraphService graphService,
            ObjectMapper objectMapper,
            TransactionOperations transactionOperations,
            org.springframework.data.redis.core.StringRedisTemplate redisTemplate,
            RagMemoryService ragMemoryService,
            NotificationService notificationService,
            UserMapper userMapper,
            PromptComposer promptComposer) {
        this.chatClient = chatClient;
        this.graphMapper = graphMapper;
        this.graphService = graphService;
        this.objectMapper = objectMapper;
        this.transactionOperations = transactionOperations;
        this.redisTemplate = redisTemplate;
        this.ragMemoryService = ragMemoryService;
        this.notificationService = notificationService;
        this.userMapper = userMapper;
        this.promptComposer = promptComposer;
    }

    public record ConsolidatedGraphResponse(@JsonProperty("triples") List<ConsolidatedTriple> triples) {
    }

    public record ConsolidatedTriple(@JsonProperty("headEntity") String headEntity,
            @JsonProperty("relation") String relation,
            @JsonProperty("tailEntity") String tailEntity,
            @JsonProperty("tailPolarity") Integer tailPolarity,
            @JsonProperty("sourceTripleIds") List<Long> sourceTripleIds,
            @JsonProperty("sourceDiaryIds") List<Long> sourceDiaryIds,
            @JsonProperty("operation") String operation) {
    }

    public List<ConsolidatedTriple> previewConsolidation(Long userId) {
        reserveConsolidation(userId);
        return previewConsolidationInternal(userId);
    }

    public void reserveConsolidation(Long userId) {
        String today = java.time.LocalDate.now().toString();
        String redisKey = "graph:consolidate:count:" + userId + ":" + today;
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1) {
            redisTemplate.expire(redisKey, java.time.Duration.ofDays(1));
        }
        if (count != null && count > 2) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "每天最多只能进行2次关系图谱整理");
        }
    }

    public List<ConsolidatedTriple> previewConsolidationInternal(Long userId) {

        List<DiaryKnowledgeGraphEntity> existing = graphService.getTriplesForUser(userId);
        if (existing.isEmpty() || existing.size() < 3) {
            throw new RuntimeException("图谱节点过少，无需整理");
        }

        String prompt = buildPrompt(existing);
        if (contextMetadataRecorder != null) {
            contextMetadataRecorder.recordModelInvocation(userId, null, ContextPurpose.CHAT,
                    null, new TaskContext("GENERAL", "只审查和提出可追溯的图谱归并", List.of(), null),
                    "FLASH", "FLASH");
        }
        String json = chatClient.prompt()
                .system(promptComposer.compose(CONSOLIDATION_PROMPT, userId,
                        new TaskContext("GENERAL", "只审查和提出可追溯的图谱归并", List.of(), null),
                        ContextPurpose.CHAT, ""))
                .user(prompt)
                .call()
                .content();

        try {
            String cleanedJson = JsonUtils.cleanJson(json);
            ConsolidatedGraphResponse response = objectMapper.readValue(cleanedJson, ConsolidatedGraphResponse.class);
            return sanitize(userId, existing, response.triples());
        } catch (Exception e) {
            log.error("Failed to parse LLM response for graph consolidation", e);
            throw new RuntimeException("AI 返回格式错误，请稍后重试");
        }
    }

    public void runConsolidationTask(Long userId, String taskId) {
        try {
            List<ConsolidatedTriple> result = previewConsolidationInternal(userId);
            redisTemplate.opsForValue().set(RESULT_KEY_PREFIX + taskId,
                    objectMapper.writeValueAsString(result), Duration.ofHours(2));
            notificationService.notifyGlobalEvent(userId, "GRAPH_CONSOLIDATION_COMPLETED",
                    Map.of("message", "知识图谱整理已完成", "taskId", taskId, "resultCount", result.size()));
        } catch (Exception e) {
            log.warn("知识图谱整理任务失败，userId={}，taskId={}，error={}", userId, taskId, e.getMessage());
            throw e instanceof RuntimeException runtime ? runtime : new IllegalStateException(e);
        }
    }

    public List<ConsolidatedTriple> readTaskResult(String taskId) {
        String value = redisTemplate.opsForValue().get(RESULT_KEY_PREFIX + taskId);
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.readValue(value, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, ConsolidatedTriple.class));
        } catch (Exception e) {
            log.warn("知识图谱整理结果读取失败，taskId={}，errorType={}", taskId, e.getClass().getSimpleName());
            return null;
        }
    }

    public void applyConsolidation(Long userId, List<ConsolidatedTriple> newTriples) {
        List<DiaryKnowledgeGraphEntity> existing = graphService.getTriplesForUser(userId);
        LocalDateTime now = LocalDateTime.now();
        Map<String, DiaryKnowledgeGraphEntity> existingBySignature = existing.stream().collect(Collectors.toMap(
                this::tripleSignature, entity -> entity, (left, right) -> left, LinkedHashMap::new));
        List<DiaryKnowledgeGraphEntity> changed = new ArrayList<>();

        transactionOperations.execute(status -> {
            for (ConsolidatedTriple t : newTriples == null ? List.<ConsolidatedTriple>of() : newTriples) {
                List<DiaryKnowledgeGraphEntity> sources = existing.stream()
                        .filter(e -> t.sourceTripleIds() != null && t.sourceTripleIds().contains(e.getId()))
                        .toList();
                if (sources.isEmpty()) continue;
                Set<Integer> polarities = sources.stream().map(e -> e.getTailPolarity() == null ? 0 : e.getTailPolarity()).collect(Collectors.toSet());
                if (polarities.size() > 1) continue;
                DiaryKnowledgeGraphEntity entity = new DiaryKnowledgeGraphEntity();
                entity.setUserId(userId);
                entity.setDiaryId(-1L);
                entity.setHeadEntity(trimTo(t.headEntity(), 64));
                entity.setRelation(trimTo(t.relation(), 64));
                entity.setTailEntity(trimTo(t.tailEntity(), 64));
                entity.setTailPolarity(t.tailPolarity() != null ? t.tailPolarity() : 0);
                DiaryKnowledgeGraphEntity matched = existingBySignature.get(tripleSignature(entity));
                DiaryKnowledgeGraphEntity target = matched;
                if (target == null) {
                    target = entity;
                    target.setCreatedAt(now);
                    target.setStatus("active");
                    target.setSourceTripleIds(toJsonIds(sources.stream().map(DiaryKnowledgeGraphEntity::getId).toList()));
                    target.setSourceDiaryIds(toJsonIds(sources.stream().map(DiaryKnowledgeGraphEntity::getDiaryId).filter(id -> id != null && id > 0).distinct().toList()));
                    graphMapper.insert(target);
                } else {
                    target.setSourceTripleIds(toJsonIds(unionIds(target.getSourceTripleIds(), sources.stream().map(DiaryKnowledgeGraphEntity::getId).toList())));
                    target.setSourceDiaryIds(toJsonIds(unionIds(target.getSourceDiaryIds(), sources.stream().map(DiaryKnowledgeGraphEntity::getDiaryId).filter(id -> id != null && id > 0).toList())));
                    graphMapper.updateById(target);
                }
                existingBySignature.put(tripleSignature(target), target);
                changed.add(target);
                for (DiaryKnowledgeGraphEntity source : sources) {
                    if (source.getId().equals(target.getId())) continue;
                    source.setStatus("superseded");
                    source.setSupersededById(target.getId());
                    graphMapper.updateById(source);
                    ragMemoryService.deleteKnowledgeGraph(source.getId());
                }
            }
            return null;
        });

        for (DiaryKnowledgeGraphEntity entity : changed) {
            ragMemoryService.indexKnowledgeGraph(userId, entity.getDiaryId(), entity.getId(),
                    entity.getHeadEntity(), entity.getRelation(), entity.getTailEntity());
        }

        List<DiaryKnowledgeGraphEntity> latest = graphService.getTriplesForUser(userId);

        UserEntity user = userMapper.selectById(userId);
        if (user != null && !Boolean.FALSE.equals(user.getProfileNotifyEnabled())) {
            String summary = "### 关系图谱已更新\n\n本次共整理 **" + latest.size() + "** 条关系链路。\n\n点击查看图谱详情。";
            notificationService.notifyGraphUpdated(userId, summary);
        }
    }

    private String buildPrompt(List<DiaryKnowledgeGraphEntity> existing) {
        return "现有图谱三元组：\n" + existing.stream()
                .map(t -> "tripleId=" + t.getId() + " diaryId=" + t.getDiaryId() + " " + t.getHeadEntity() + " --(" + t.getRelation() + ")--> " + t.getTailEntity() + " [极性:"
                        + (t.getTailPolarity() == null ? 0 : t.getTailPolarity()) + "]")
                .collect(Collectors.joining("\n"));
    }

    private List<ConsolidatedTriple> sanitize(Long userId, List<DiaryKnowledgeGraphEntity> existing,
                                               List<ConsolidatedTriple> triples) {
        Set<Long> owned = existing.stream().map(DiaryKnowledgeGraphEntity::getId).collect(Collectors.toSet());
        List<ConsolidatedTriple> result = new ArrayList<>();
        for (ConsolidatedTriple t : triples == null ? List.<ConsolidatedTriple>of() : triples) {
            if (t == null || t.headEntity() == null || t.relation() == null || t.tailEntity() == null) continue;
            List<Long> ids = t.sourceTripleIds() == null ? List.of() : t.sourceTripleIds().stream().filter(owned::contains).distinct().toList();
            if (ids.isEmpty()) continue;
            List<Long> diaries = existing.stream().filter(e -> ids.contains(e.getId())).map(DiaryKnowledgeGraphEntity::getDiaryId)
                    .filter(id -> id != null && id > 0).distinct().toList();
            result.add(new ConsolidatedTriple(trimTo(t.headEntity(), 64), trimTo(t.relation(), 64), trimTo(t.tailEntity(), 64),
                    t.tailPolarity() == null ? 0 : t.tailPolarity(), ids, diaries,
                    t.operation() == null ? "DEDUP" : t.operation().toUpperCase(java.util.Locale.ROOT)));
        }
        return result;
    }

    private String toJsonIds(List<Long> ids) {
        try { return objectMapper.writeValueAsString(ids == null ? List.of() : ids); }
        catch (Exception e) { throw new IllegalStateException("图谱来源序列化失败", e); }
    }

    private List<Long> unionIds(String json, List<Long> additional) {
        Set<Long> ids = new LinkedHashSet<>();
        try { if (json != null && !json.isBlank()) for (JsonNode id : objectMapper.readTree(json)) ids.add(id.asLong()); }
        catch (Exception ignored) { }
        if (additional != null) ids.addAll(additional);
        return new ArrayList<>(ids);
    }

    private String trimTo(String val, int maxLen) {
        if (val == null)
            return "未知";
        String trimmed = val.trim();
        if (trimmed.length() > maxLen) {
            return trimmed.substring(0, maxLen);
        }
        return trimmed;
    }

    private String tripleSignature(DiaryKnowledgeGraphEntity entity) {
        return tripleSignature(entity.getHeadEntity(), entity.getRelation(), entity.getTailEntity(),
                entity.getTailPolarity());
    }

    private String tripleSignature(String head, String relation, String tail, Integer tailPolarity) {
        return (head == null ? "" : head.trim()) + "\u0001"
                + (relation == null ? "" : relation.trim()) + "\u0001"
                + (tail == null ? "" : tail.trim()) + "\u0001"
                + (tailPolarity == null ? 0 : tailPolarity);
    }
}
