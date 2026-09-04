package com.moodcopilot.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.UserMemoryCandidateEntity;
import com.moodcopilot.entity.UserMemoryEvidenceEntity;
import com.moodcopilot.entity.UserMemoryRejectionEntity;
import com.moodcopilot.entity.UserProfileMemoryEntity;
import com.moodcopilot.mapper.UserMemoryCandidateMapper;
import com.moodcopilot.mapper.UserMemoryEvidenceMapper;
import com.moodcopilot.mapper.UserMemoryRejectionMapper;
import com.moodcopilot.mapper.UserProfileMemoryMapper;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.entity.DiaryEntity;
import com.moodcopilot.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class MemoryOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(MemoryOrchestrator.class);
    private static final int REJECTION_DAYS = 180;
    private static final String ACTIVE = "active";
    private static final String PENDING = "PENDING";
    private static final String APPROVED = "APPROVED";
    private static final String MERGED = "MERGED";
    private static final String[] MEMORY_TYPES = {"preference", "relationship", "habit", "event", "short_term_state", "pattern"};
    private static final String[] SOURCE_TYPES = {"explicit", "diary_inferred", "chat_candidate", "system"};

    private final UserProfileMemoryMapper memoryMapper;
    private final UserMemoryCandidateMapper candidateMapper;
    private final UserMemoryEvidenceMapper evidenceMapper;
    private final UserMemoryRejectionMapper rejectionMapper;
    private final RagMemoryService ragMemoryService;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final DiaryMapper diaryMapper;

    @Autowired
    public MemoryOrchestrator(UserProfileMemoryMapper memoryMapper,
                              UserMemoryCandidateMapper candidateMapper,
                              UserMemoryEvidenceMapper evidenceMapper,
                              UserMemoryRejectionMapper rejectionMapper,
                              RagMemoryService ragMemoryService,
                              ObjectMapper objectMapper,
                              NotificationService notificationService,
                              DiaryMapper diaryMapper) {
        this.memoryMapper = memoryMapper;
        this.candidateMapper = candidateMapper;
        this.evidenceMapper = evidenceMapper;
        this.rejectionMapper = rejectionMapper;
        this.ragMemoryService = ragMemoryService;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.diaryMapper = diaryMapper;
    }

    /** 保留无通知依赖的构造入口，便于纯记忆规则单测隔离通知副作用。 */
    public MemoryOrchestrator(UserProfileMemoryMapper memoryMapper,
                              UserMemoryCandidateMapper candidateMapper,
                              UserMemoryEvidenceMapper evidenceMapper,
                              UserMemoryRejectionMapper rejectionMapper,
                              RagMemoryService ragMemoryService,
                              ObjectMapper objectMapper,
                              NotificationService notificationService) {
        this(memoryMapper, candidateMapper, evidenceMapper, rejectionMapper, ragMemoryService,
                objectMapper, notificationService, null);
    }

    public MemoryOrchestrator(UserProfileMemoryMapper memoryMapper,
                              UserMemoryCandidateMapper candidateMapper,
                              UserMemoryEvidenceMapper evidenceMapper,
                              UserMemoryRejectionMapper rejectionMapper,
                              RagMemoryService ragMemoryService,
                              ObjectMapper objectMapper) {
        this(memoryMapper, candidateMapper, evidenceMapper, rejectionMapper, ragMemoryService, objectMapper, null, null);
    }

    @Transactional
    public void processExtractedMemories(Long userId, List<MemoryExtractionService.MemoryAttribute> attributes,
                                         String sourceType, Long sourceDiaryId, Long sourceConversationId,
                                         String defaultEvidence, LocalDate evidenceDate) {
        if (userId == null || attributes == null || attributes.isEmpty()) return;
        String safeSource = normalizeSource(sourceType);
        LocalDate date = evidenceDate == null ? LocalDate.now() : evidenceDate;
        for (MemoryExtractionService.MemoryAttribute attr : attributes) {
            if (attr == null) continue;
            String key = clean(attr.attributeKey(), 64);
            String value = clean(attr.attributeValue(), 500);
            if (key.isBlank() || value.isBlank()) continue;
            String type = MemorySafetyPolicy.normalizeType(normalizeType(attr.memoryType()), attr.attributeKey(), attr.attributeValue());
            boolean allowCore = MemorySafetyPolicy.allowCore(type, attr.attributeKey(), attr.attributeValue());
            Boolean requestedIsCore = allowCore ? attr.isCore() : Boolean.FALSE;
            String assertion = attr.assertionType() == null ? "inferred" : attr.assertionType().toLowerCase(Locale.ROOT);
            if (!Set.of("explicit", "inferred", "negated").contains(assertion)) assertion = "inferred";
            String modelEvidence = clean(attr.evidence(), 2000);
            boolean evidenceGrounded = isEvidenceGrounded(safeSource, modelEvidence, defaultEvidence);
            String evidence = modelEvidence;
            evidence = groundEvidence(safeSource, evidence, defaultEvidence);
            String actualSource = "explicit".equals(safeSource)
                    || ("explicit".equals(assertion) && evidenceGrounded
                    && verifiedExplicitEvidence(safeSource, modelEvidence, defaultEvidence))
                    ? "explicit" : safeSource;

            if ("DELETE_MARKER".equals(value)) {
                rejectActive(userId, key, type, "MODEL_NEGATION");
                continue;
            }
            if ("negated".equals(assertion)) {
                rejectActive(userId, key, type, "MODEL_NEGATION");
                addRejection(userId, type, key, value, "MODEL_NEGATION");
                continue;
            }
            if ("explicit".equals(actualSource)) {
                UserProfileMemoryEntity memory = saveFormal(userId, key, value, type, actualSource,
                        sourceDiaryId, sourceConversationId, scoreConfidence(attr.confidence()), date,
                        "explicit evidence", requestedIsCore);
                addEvidence(userId, memory.getId(), null, actualSource, sourceDiaryId, sourceConversationId,
                        evidence, date, attr.confidence(), 1.0);
                notifyFormalized(userId, key, value, "用户明确确认");
                continue;
            }
            if (isRejected(userId, type, key, value)) continue;
            UserMemoryCandidateEntity candidate = findCandidate(userId, key, type, value);
            if (candidate == null) {
                candidate = new UserMemoryCandidateEntity();
                candidate.setUserId(userId);
                candidate.setAttributeKey(key);
                candidate.setNormalizedValue(normalize(value));
                candidate.setAttributeValue(value);
                candidate.setMemoryType(type);
                candidate.setSourceType(safeSource);
                candidate.setConfidence(scoreConfidence(attr.confidence()));
                candidate.setIsCore(requestedIsCore);
                candidate.setStatus(PENDING);
                candidate.setEvidenceSummary(evidence);
                candidate.setSourceDiaryId(sourceDiaryId);
                candidate.setSourceConversationId(sourceConversationId);
                candidate.setValidFrom(attr.validFrom() == null ? date : attr.validFrom());
                candidate.setValidUntil(attr.validUntil());
                try {
                    candidateMapper.insert(candidate);
                } catch (DuplicateKeyException duplicate) {
                    // The unique index is the final concurrency guard. Reload the winner instead of
                    // turning a duplicate delivery into a failed AI task.
                    candidate = findCandidate(userId, key, type, value);
                    if (candidate == null) throw duplicate;
                }
            } else {
                candidate.setConfidence(Math.max(candidate.getConfidence() == null ? 0 : candidate.getConfidence(),
                        scoreConfidence(attr.confidence())));
                candidate.setEvidenceSummary(appendSummary(candidate.getEvidenceSummary(), evidence));
                if (sourceDiaryId != null && candidate.getSourceDiaryId() == null) candidate.setSourceDiaryId(sourceDiaryId);
                if (sourceConversationId != null && candidate.getSourceConversationId() == null) candidate.setSourceConversationId(sourceConversationId);
                if (candidate.getValidFrom() == null || (attr.validFrom() != null && attr.validFrom().isBefore(candidate.getValidFrom()))) {
                    candidate.setValidFrom(attr.validFrom() == null ? date : attr.validFrom());
                }
                candidateMapper.updateById(candidate);
            }
            Long formalMemoryId = APPROVED.equals(candidate.getStatus())
                    ? resolveFormalMemoryForCandidate(userId, candidate) : null;
            addEvidence(userId, formalMemoryId, candidate.getId(), safeSource, sourceDiaryId, sourceConversationId,
                    evidence, date, attr.confidence(), evidenceQuality(attr));
            if (PENDING.equals(candidate.getStatus())) maybePromote(candidate);
        }
        // 日记后处理已经会创建 MEMORY_RAG_INDEX 子任务。将画像索引交给该任务，
        // 避免这里和子任务各自执行一次全量画像向量化；聊天和用户操作没有独立索引任务，仍即时更新。
        boolean hasDedicatedDiaryRagTask = "diary_inferred".equals(safeSource) && sourceDiaryId != null;
        if (!hasDedicatedDiaryRagTask) {
            reindex(userId);
        } else {
            log.debug("日记来源画像索引延后到 MEMORY_RAG_INDEX 任务，userId={}，sourceDiaryId={}",
                    userId, sourceDiaryId);
        }
    }

    @Transactional
    public void approveCandidate(long userId, long candidateId) {
        UserMemoryCandidateEntity candidate = ownedCandidate(userId, candidateId);
        if (!PENDING.equals(candidate.getStatus())) return;
        UserProfileMemoryEntity memory = saveFormal(userId, candidate.getAttributeKey(), candidate.getAttributeValue(),
                candidate.getMemoryType(), "explicit", candidate.getSourceDiaryId(), candidate.getSourceConversationId(),
                candidate.getConfidence(), candidate.getValidFrom(), "user approved candidate", candidate.getIsCore());
        evidenceMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<UserMemoryEvidenceEntity>()
                .eq(UserMemoryEvidenceEntity::getCandidateId, candidateId)
                .set(UserMemoryEvidenceEntity::getMemoryId, memory.getId()));
        candidate.setStatus("APPROVED");
        candidateMapper.updateById(candidate);
        notifyFormalized(userId, candidate.getAttributeKey(), candidate.getAttributeValue(), "你确认了候选记忆");
        reindex(userId);
    }

    @Transactional
    public void rejectCandidate(long userId, long candidateId) {
        UserMemoryCandidateEntity candidate = ownedCandidate(userId, candidateId);
        candidate.setStatus("REJECTED");
        candidateMapper.updateById(candidate);
        addRejection(userId, candidate.getMemoryType(), candidate.getAttributeKey(), candidate.getAttributeValue(), "USER_REJECTED");
    }

    @Transactional
    public void deleteFormal(long userId, long memoryId) {
        UserProfileMemoryEntity memory = ownedFormal(userId, memoryId);
        memory.setStatus("rejected");
        memory.setValidUntil(LocalDate.now());
        memory.setSupersededAt(LocalDateTime.now());
        memory.setSupersededReason("USER_DELETED");
        memoryMapper.updateById(memory);
        addRejection(userId, memory.getMemoryType(), memory.getAttributeKey(), memory.getAttributeValue(), "USER_DELETED");
        reindex(userId);
    }

    @Transactional
    public void updateFormal(long userId, long memoryId, String newValue, Boolean isCore) {
        UserProfileMemoryEntity old = ownedFormal(userId, memoryId);
        String value = clean(newValue == null ? old.getAttributeValue() : newValue, 500);
        if (value.isBlank()) throw new ResponseStatusException(BAD_REQUEST, "记忆内容不能为空");
        if (isCore == null) isCore = old.getIsCore();
        if (!MemorySafetyPolicy.allowCore(old.getMemoryType(), old.getAttributeKey(), value)) {
            isCore = false;
        }
        UserProfileMemoryEntity next = saveFormal(userId, old.getAttributeKey(), value, old.getMemoryType(),
                "explicit", old.getSourceDiaryId(), old.getSourceConversationId(), old.getConfidence(),
                old.getValidFrom(), "USER_EDITED", isCore);
        next.setIsCore(isCore);
        memoryMapper.updateById(next);
        addEvidence(userId, next.getId(), null, "USER_ACTION", null, null,
                "用户编辑长期记忆", LocalDate.now(), 1.0, 1.0);
        reindex(userId);
    }

    public List<UserMemoryCandidateEntity> listCandidates(long userId, String status) {
        return listCandidates(userId, status, 1, 20, "updatedAt");
    }

    public List<UserMemoryCandidateEntity> listCandidates(long userId, String status, int page, int size, String sort) {
        if (status == null || PENDING.equalsIgnoreCase(status)) repairPendingCandidates(userId);
        var wrapper = new LambdaQueryWrapper<UserMemoryCandidateEntity>().eq(UserMemoryCandidateEntity::getUserId, userId);
        if (status != null && !status.isBlank()) wrapper.eq(UserMemoryCandidateEntity::getStatus, status.toUpperCase(Locale.ROOT));
        if ("createdAt".equals(sort)) {
            wrapper.orderByAsc(UserMemoryCandidateEntity::getCreatedAt);
        } else {
            wrapper.orderByDesc(UserMemoryCandidateEntity::getUpdatedAt);
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        wrapper.last("LIMIT " + ((long) (safePage - 1) * safeSize) + "," + safeSize);
        return candidateMapper.selectList(wrapper);
    }

    /** 合并同义候选的证据，保留冲突候选作为独立分支。该操作不删除任何历史记录。 */
    @Transactional
    public int repairPendingCandidates() {
        List<UserMemoryCandidateEntity> candidates = candidateMapper.selectList(new LambdaQueryWrapper<UserMemoryCandidateEntity>()
                .in(UserMemoryCandidateEntity::getStatus, PENDING, APPROVED)
                .orderByAsc(UserMemoryCandidateEntity::getCreatedAt)
                .orderByAsc(UserMemoryCandidateEntity::getId)
                .last("FOR UPDATE"));
        return repairCandidateConflicts(candidates);
    }

    private int repairCandidateConflicts(List<UserMemoryCandidateEntity> candidates) {
        Map<String, List<UserMemoryCandidateEntity>> groups = new LinkedHashMap<>();
        for (UserMemoryCandidateEntity candidate : candidates) {
            String groupKey = candidate.getUserId() + "\u0000" + candidate.getMemoryType() + "\u0000" + candidate.getAttributeKey();
            groups.computeIfAbsent(groupKey, ignored -> new ArrayList<>()).add(candidate);
        }
        int merged = 0;
        for (List<UserMemoryCandidateEntity> group : groups.values()) {
            List<UserMemoryCandidateEntity> approved = group.stream()
                    .filter(candidate -> APPROVED.equals(candidate.getStatus()))
                    .toList();
            for (UserMemoryCandidateEntity pending : group) {
                if (!PENDING.equals(pending.getStatus())) continue;
                UserMemoryCandidateEntity target = approved.stream()
                        .filter(candidate -> candidateValuesCompatible(candidate.getAttributeValue(), pending.getAttributeValue()))
                        .findFirst().orElse(null);
                if (target != null) {
                    mergePendingIntoApproved(pending, target);
                    merged++;
                }
            }
            merged += mergeCompatibleCandidates(group.stream()
                    .filter(candidate -> PENDING.equals(candidate.getStatus()))
                    .toList());
        }
        return merged;
    }

    @Transactional
    public int repairPendingCandidates(long userId) {
        List<UserMemoryCandidateEntity> candidates = candidateMapper.selectList(new LambdaQueryWrapper<UserMemoryCandidateEntity>()
                .eq(UserMemoryCandidateEntity::getUserId, userId)
                .in(UserMemoryCandidateEntity::getStatus, PENDING, APPROVED)
                .orderByAsc(UserMemoryCandidateEntity::getCreatedAt)
                .orderByAsc(UserMemoryCandidateEntity::getId)
                .last("FOR UPDATE"));
        return repairCandidateConflicts(candidates);
    }

    public UserProfileMemoryEntity detail(long userId, long memoryId) {
        return ownedFormal(userId, memoryId);
    }

    @Transactional
    public void replaceWithUserAction(long userId, List<MemoryExtractionService.MemoryAttribute> attributes) {
        List<UserProfileMemoryEntity> existing = current(userId);
        for (UserProfileMemoryEntity old : existing) {
            old.setStatus("superseded");
            old.setValidUntil(LocalDate.now());
            old.setSupersededAt(LocalDateTime.now());
            old.setSupersededReason("USER_CONSOLIDATED");
            memoryMapper.updateById(old);
        }
        processExtractedMemories(userId, attributes, "explicit", null, null, "用户确认整理长期画像", LocalDate.now());
    }

    /**
     * 只应用有来源映射的去重结果。整理不是重新生成画像，冲突或无法证明来源的结果会被跳过。
     */
    @Transactional
    public void applyConsolidation(long userId, List<MemoryConsolidationService.ConsolidationItem> items) {
        if (items == null || items.isEmpty()) return;
        for (MemoryConsolidationService.ConsolidationItem item : items) {
            if (item == null || item.sourceMemoryIds() == null || item.sourceMemoryIds().isEmpty()) continue;
            List<UserProfileMemoryEntity> sources = memoryMapper.selectList(new LambdaQueryWrapper<UserProfileMemoryEntity>()
                    .eq(UserProfileMemoryEntity::getUserId, userId)
                    .eq(UserProfileMemoryEntity::getStatus, ACTIVE)
                    .in(UserProfileMemoryEntity::getId, item.sourceMemoryIds()));
            if (sources.isEmpty()) continue;

            if ("EXPIRE".equalsIgnoreCase(item.operation())) {
                for (UserProfileMemoryEntity source : sources) {
                    if ("short_term_state".equals(source.getMemoryType())) {
                        source.setStatus("expired");
                        source.setValidUntil(LocalDate.now());
                        source.setSupersededAt(LocalDateTime.now());
                        source.setSupersededReason("USER_CONSOLIDATED_EXPIRED");
                        memoryMapper.updateById(source);
                    }
                }
                continue;
            }

            String sourceKey = sources.get(0).getAttributeKey();
            String sourceValue = sources.get(0).getAttributeValue();
            boolean sameFact = sources.stream().allMatch(source -> sourceKey.equals(source.getAttributeKey())
                    && normalize(sourceValue).equals(normalize(source.getAttributeValue()))
                    && sourceKey.equals(item.attributeKey())
                    && normalize(sourceValue).equals(normalize(item.attributeValue())));
            if (!sameFact) {
                // 不能让模型把同一属性的冲突值拼成一条新事实。
                continue;
            }

            UserProfileMemoryEntity target = sources.get(0);
            for (UserProfileMemoryEntity source : sources.subList(1, sources.size())) {
                evidenceMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<UserMemoryEvidenceEntity>()
                        .eq(UserMemoryEvidenceEntity::getUserId, userId)
                        .eq(UserMemoryEvidenceEntity::getMemoryId, source.getId())
                        .set(UserMemoryEvidenceEntity::getMemoryId, target.getId()));
                source.setStatus("superseded");
                source.setValidUntil(target.getValidFrom() == null ? LocalDate.now() : target.getValidFrom());
                source.setSupersededAt(LocalDateTime.now());
                source.setSupersededReason("USER_CONSOLIDATED_DEDUP");
                memoryMapper.updateById(source);
            }
            if (item.isCore() != null && MemorySafetyPolicy.allowCore(target.getMemoryType(), target.getAttributeKey(), target.getAttributeValue())) {
                target.setIsCore(item.isCore());
                target.setUpdatedAt(LocalDateTime.now());
                target.setUpdateTime(LocalDateTime.now());
                memoryMapper.updateById(target);
            }
            addEvidence(userId, target.getId(), null, "USER_ACTION", null, null,
                    "用户确认整理：" + item.operation(), LocalDate.now(), 1.0, 1.0);
        }
        reindex(userId);
    }

    public List<UserProfileMemoryEntity> current(long userId) {
        return memoryMapper.selectList(new LambdaQueryWrapper<UserProfileMemoryEntity>()
                .eq(UserProfileMemoryEntity::getUserId, userId)
                .eq(UserProfileMemoryEntity::getStatus, ACTIVE)
                .and(w -> w.isNull(UserProfileMemoryEntity::getValidUntil).or().ge(UserProfileMemoryEntity::getValidUntil, LocalDate.now()))
                .orderByAsc(UserProfileMemoryEntity::getAttributeKey)).stream()
                .filter(memory -> !isShortTermExpired(memory))
                .map(memory -> {
                    if (!MemorySafetyPolicy.allowCore(memory.getMemoryType(), memory.getAttributeKey(), memory.getAttributeValue())) {
                        memory.setIsCore(false);
                    }
                    return memory;
                })
                .toList();
    }

    public List<UserProfileMemoryEntity> history(long userId, long memoryId) {
        UserProfileMemoryEntity selected = memoryMapper.selectOne(new LambdaQueryWrapper<UserProfileMemoryEntity>()
                .eq(UserProfileMemoryEntity::getId, memoryId).eq(UserProfileMemoryEntity::getUserId, userId)
                .last("LIMIT 1"));
        if (selected == null) throw new ResponseStatusException(BAD_REQUEST, "记忆记录不存在或无权操作");
        return memoryMapper.selectList(new LambdaQueryWrapper<UserProfileMemoryEntity>()
                .eq(UserProfileMemoryEntity::getUserId, userId)
                .eq(UserProfileMemoryEntity::getAttributeKey, selected.getAttributeKey())
                .orderByDesc(UserProfileMemoryEntity::getUpdatedAt)
                .orderByDesc(UserProfileMemoryEntity::getId));
    }

    public List<UserMemoryEvidenceEntity> evidence(long userId, long memoryId) {
        List<Long> ids = history(userId, memoryId).stream().map(UserProfileMemoryEntity::getId).toList();
        if (ids.isEmpty()) return List.of();
        return evidenceMapper.selectList(new LambdaQueryWrapper<UserMemoryEvidenceEntity>()
                .eq(UserMemoryEvidenceEntity::getUserId, userId)
                .in(UserMemoryEvidenceEntity::getMemoryId, ids)
                .orderByDesc(UserMemoryEvidenceEntity::getEvidenceDate)
                .orderByDesc(UserMemoryEvidenceEntity::getCreatedAt));
    }

    public List<UserMemoryEvidenceEntity> candidateEvidence(long userId, long candidateId) {
        ownedCandidate(userId, candidateId);
        return evidenceMapper.selectList(new LambdaQueryWrapper<UserMemoryEvidenceEntity>()
                .eq(UserMemoryEvidenceEntity::getUserId, userId)
                .eq(UserMemoryEvidenceEntity::getCandidateId, candidateId)
                .orderByDesc(UserMemoryEvidenceEntity::getEvidenceDate)
                .orderByDesc(UserMemoryEvidenceEntity::getCreatedAt));
    }

    public record SourceSummary(long evidenceCount, List<Long> diaryIds, List<Long> conversationIds,
                                Map<Long, LocalDate> diaryEvidenceDates) {
        public SourceSummary(long evidenceCount, List<Long> diaryIds, List<Long> conversationIds) {
            this(evidenceCount, diaryIds, conversationIds, Map.of());
        }
    }

    public record DiarySourcePreview(Long id, LocalDateTime createdAt, String excerpt) {}

    /** 批量读取当前用户可访问的日记来源，避免记忆列表逐条查询。 */
    public Map<Long, DiarySourcePreview> diarySourcePreviews(long userId, List<Long> diaryIds) {
        if (diaryMapper == null || diaryIds == null || diaryIds.isEmpty()) return Map.of();
        List<Long> distinctIds = diaryIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) return Map.of();
        Map<Long, DiarySourcePreview> result = new LinkedHashMap<>();
        for (DiaryEntity diary : diaryMapper.selectBatchIds(distinctIds)) {
            if (!Long.valueOf(userId).equals(diary.getAuthorUserId())) continue;
            result.put(diary.getId(), new DiarySourcePreview(diary.getId(), diary.getCreatedAt(), diaryExcerpt(diary.getContent())));
        }
        return result;
    }

    /** 用证据日期补齐旧日记缺少创建时间的来源预览。 */
    public DiarySourcePreview withEvidenceDate(DiarySourcePreview preview, LocalDate evidenceDate) {
        if (preview == null || preview.createdAt() != null || evidenceDate == null) return preview;
        return new DiarySourcePreview(preview.id(), LocalDateTime.of(evidenceDate, LocalTime.MIDNIGHT), preview.excerpt());
    }

    private String diaryExcerpt(String content) {
        if (content == null || content.isBlank()) return "无文字内容";
        String plain = content.replaceAll("<[^>]*>", " ")
                .replaceAll("!\\[[^]]*]\\([^)]*\\)", " ")
                .replaceAll("[#*_`~>\\[\\]()]", " ")
                .replaceAll("\\s+", " ").trim();
        if (plain.isBlank()) return "无文字内容";
        return plain.length() > 24 ? plain.substring(0, 24) + "..." : plain;
    }

    public Map<Long, SourceSummary> sourceSummariesForMemories(long userId, List<Long> memoryIds) {
        if (memoryIds == null || memoryIds.isEmpty()) return Map.of();
        List<UserProfileMemoryEntity> currentMemories = memoryMapper.selectBatchIds(memoryIds).stream()
                .filter(memory -> Long.valueOf(userId).equals(memory.getUserId()))
                .toList();
        if (currentMemories.isEmpty()) return Map.of();
        List<String> keys = currentMemories.stream().map(UserProfileMemoryEntity::getAttributeKey).distinct().toList();
        List<UserProfileMemoryEntity> versions = memoryMapper.selectList(new LambdaQueryWrapper<UserProfileMemoryEntity>()
                .eq(UserProfileMemoryEntity::getUserId, userId)
                .in(UserProfileMemoryEntity::getAttributeKey, keys));
        Map<Long, SourceSummary> allVersionSources = sourceSummaries(userId,
                versions.stream().map(UserProfileMemoryEntity::getId).toList(), false);
        Map<Long, SourceSummary> result = new LinkedHashMap<>();
        for (UserProfileMemoryEntity current : currentMemories) {
            List<UserProfileMemoryEntity> sameKey = versions.stream()
                    .filter(version -> current.getAttributeKey().equals(version.getAttributeKey()))
                    .toList();
            result.put(current.getId(), mergeSourceSummaries(sameKey.stream().map(allVersionSources::get).toList()));
        }
        return result;
    }

    public Map<Long, SourceSummary> sourceSummariesForCandidates(long userId, List<Long> candidateIds) {
        return sourceSummaries(userId, candidateIds, true);
    }

    private Map<Long, SourceSummary> sourceSummaries(long userId, List<Long> aggregateIds, boolean candidate) {
        if (aggregateIds == null || aggregateIds.isEmpty()) return Map.of();
        Map<Long, Long> counts = new LinkedHashMap<>();
        Map<Long, LinkedHashSet<Long>> diaryIds = new LinkedHashMap<>();
        Map<Long, LinkedHashSet<Long>> conversationIds = new LinkedHashMap<>();
        Map<Long, LocalDate> diaryEvidenceDates = new LinkedHashMap<>();
        var wrapper = new LambdaQueryWrapper<UserMemoryEvidenceEntity>()
                .eq(UserMemoryEvidenceEntity::getUserId, userId);
        if (candidate) {
            wrapper.in(UserMemoryEvidenceEntity::getCandidateId, aggregateIds);
        } else {
            wrapper.in(UserMemoryEvidenceEntity::getMemoryId, aggregateIds);
        }
        for (UserMemoryEvidenceEntity evidence : evidenceMapper.selectList(wrapper)) {
            Long aggregateId = candidate ? evidence.getCandidateId() : evidence.getMemoryId();
            if (aggregateId == null) continue;
            counts.merge(aggregateId, 1L, Long::sum);
            if (evidence.getSourceDiaryId() != null) diaryIds.computeIfAbsent(aggregateId, ignored -> new LinkedHashSet<>()).add(evidence.getSourceDiaryId());
            if (evidence.getSourceDiaryId() != null && evidence.getEvidenceDate() != null) {
                diaryEvidenceDates.putIfAbsent(evidence.getSourceDiaryId(), evidence.getEvidenceDate());
            }
            if (evidence.getSourceConversationId() != null) conversationIds.computeIfAbsent(aggregateId, ignored -> new LinkedHashSet<>()).add(evidence.getSourceConversationId());
        }

        // Older records may have kept the source on the aggregate row before the
        // evidence table was introduced. Include that provenance as a fallback so
        // the UI can still resolve the diary and show its date and excerpt.
        if (candidate) {
            for (UserMemoryCandidateEntity row : candidateMapper.selectBatchIds(aggregateIds).stream()
                    .filter(candidateRow -> Long.valueOf(userId).equals(candidateRow.getUserId()))
                    .toList()) {
                if (row.getSourceDiaryId() != null) diaryIds.computeIfAbsent(row.getId(), ignored -> new LinkedHashSet<>()).add(row.getSourceDiaryId());
                if (row.getSourceConversationId() != null) conversationIds.computeIfAbsent(row.getId(), ignored -> new LinkedHashSet<>()).add(row.getSourceConversationId());
            }
        } else {
            for (UserProfileMemoryEntity row : memoryMapper.selectBatchIds(aggregateIds).stream()
                    .filter(memory -> Long.valueOf(userId).equals(memory.getUserId()))
                    .toList()) {
                if (row.getSourceDiaryId() != null) diaryIds.computeIfAbsent(row.getId(), ignored -> new LinkedHashSet<>()).add(row.getSourceDiaryId());
                if (row.getSourceConversationId() != null) conversationIds.computeIfAbsent(row.getId(), ignored -> new LinkedHashSet<>()).add(row.getSourceConversationId());
            }
        }
        Map<Long, SourceSummary> result = new LinkedHashMap<>();
        for (Long id : aggregateIds) {
            result.put(id, new SourceSummary(counts.getOrDefault(id, 0L),
                    List.copyOf(diaryIds.getOrDefault(id, new LinkedHashSet<>())),
                    List.copyOf(conversationIds.getOrDefault(id, new LinkedHashSet<>())),
                    diaryEvidenceDates.entrySet().stream()
                            .filter(entry -> diaryIds.getOrDefault(id, new LinkedHashSet<>()).contains(entry.getKey()))
                            .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue))));
        }
        return result;
    }

    private SourceSummary mergeSourceSummaries(List<SourceSummary> summaries) {
        long count = 0;
        LinkedHashSet<Long> diaries = new LinkedHashSet<>();
        LinkedHashSet<Long> conversations = new LinkedHashSet<>();
        Map<Long, LocalDate> diaryDates = new LinkedHashMap<>();
        for (SourceSummary summary : summaries) {
            if (summary == null) continue;
            count += summary.evidenceCount();
            diaries.addAll(summary.diaryIds());
            conversations.addAll(summary.conversationIds());
            diaryDates.putAll(summary.diaryEvidenceDates());
        }
        return new SourceSummary(count, List.copyOf(diaries), List.copyOf(conversations), Map.copyOf(diaryDates));
    }

    public Map<Long, List<Long>> diarySourcesForMemories(long userId, List<Long> memoryIds) {
        if (memoryIds == null || memoryIds.isEmpty()) return Map.of();
        return evidenceMapper.selectList(new LambdaQueryWrapper<UserMemoryEvidenceEntity>()
                        .eq(UserMemoryEvidenceEntity::getUserId, userId)
                        .in(UserMemoryEvidenceEntity::getMemoryId, memoryIds))
                .stream()
                .filter(e -> e.getMemoryId() != null && e.getSourceDiaryId() != null)
                .collect(java.util.stream.Collectors.groupingBy(UserMemoryEvidenceEntity::getMemoryId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.mapping(UserMemoryEvidenceEntity::getSourceDiaryId,
                                java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toCollection(LinkedHashSet::new), List::copyOf))));
    }

    public Map<Long, List<Long>> diarySourcesForCandidates(long userId, List<Long> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) return Map.of();
        return evidenceMapper.selectList(new LambdaQueryWrapper<UserMemoryEvidenceEntity>()
                        .eq(UserMemoryEvidenceEntity::getUserId, userId)
                        .in(UserMemoryEvidenceEntity::getCandidateId, candidateIds))
                .stream()
                .filter(e -> e.getCandidateId() != null && e.getSourceDiaryId() != null)
                .collect(java.util.stream.Collectors.groupingBy(UserMemoryEvidenceEntity::getCandidateId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.mapping(UserMemoryEvidenceEntity::getSourceDiaryId,
                                java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toCollection(LinkedHashSet::new), List::copyOf))));
    }

    private void maybePromote(UserMemoryCandidateEntity candidate) {
        if (candidate == null || !PENDING.equals(candidate.getStatus())) return;

        // A historical APPROVED candidate may have the same normalized value. In that case
        // move the pending branch into the existing formal memory instead of changing its
        // status to APPROVED and colliding with uk_memory_candidate_pending.
        UserMemoryCandidateEntity approved = findApprovedEquivalentCandidate(candidate);
        if (approved != null) {
            mergePendingIntoApproved(candidate, approved);
            return;
        }

        List<UserMemoryEvidenceEntity> evidence = evidenceMapper.selectList(new LambdaQueryWrapper<UserMemoryEvidenceEntity>()
                .eq(UserMemoryEvidenceEntity::getCandidateId, candidate.getId()));
        long dates = evidence.stream().map(UserMemoryEvidenceEntity::getEvidenceDate).filter(java.util.Objects::nonNull).distinct().count();
        int required = switch (candidate.getMemoryType()) {
            case "habit", "pattern" -> 3;
            case "preference", "relationship" -> 2;
            case "event" -> 2;
            default -> Integer.MAX_VALUE;
        };
        if (dates < required) return;
        double dateCoverage = Math.min(1.0, dates / (double) required);
        double sourceDiversity = Math.min(1.0, evidence.stream().map(UserMemoryEvidenceEntity::getSourceType).distinct().count() / 2.0);
        double quality = evidence.stream().mapToDouble(e -> e.getEvidenceQuality() == null ? .5 : e.getEvidenceQuality()).average().orElse(.5);
        double model = evidence.stream().map(UserMemoryEvidenceEntity::getModelConfidence).filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue).average().orElse(.5);
        double contradictionPenalty = evidence.stream().anyMatch(e -> containsNegation(e.getEvidenceText())) ? .30 : 0;
        double rawScore = .40 * model + .25 * quality + .20 * dateCoverage + .15 * sourceDiversity
                - contradictionPenalty;
        long ageDays = evidence.stream().map(UserMemoryEvidenceEntity::getEvidenceDate).filter(java.util.Objects::nonNull)
                .mapToLong(d -> ChronoUnit.DAYS.between(d, LocalDate.now())).max().orElse(0);
        double effective = rawScore * Math.pow(2, -Math.max(0, ageDays) / halfLife(candidate.getMemoryType()));
        double threshold = switch (candidate.getMemoryType()) {
            case "preference", "relationship" -> .85;
            case "habit" -> .88;
            case "pattern" -> .90;
            case "event" -> .80;
            default -> 2.0;
        };
        if (effective < threshold) return;

        // Re-check under the transaction lock immediately before changing the candidate
        // status. A duplicate delivery may have formalized an equivalent candidate since
        // the first read.
        approved = findApprovedEquivalentCandidate(candidate);
        if (approved != null) {
            mergePendingIntoApproved(candidate, approved);
            return;
        }

        UserProfileMemoryEntity memory = saveFormal(candidate.getUserId(), candidate.getAttributeKey(), candidate.getAttributeValue(),
                candidate.getMemoryType(), candidate.getSourceType(), candidate.getSourceDiaryId(), candidate.getSourceConversationId(),
                effective, candidate.getValidFrom(), "AUTO_UPGRADED", candidate.getIsCore());
        evidenceMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<UserMemoryEvidenceEntity>()
                .eq(UserMemoryEvidenceEntity::getCandidateId, candidate.getId()).set(UserMemoryEvidenceEntity::getMemoryId, memory.getId()));
        candidate.setConfidence(effective);
        candidate.setStatus("APPROVED");
        try {
            int updated = candidateMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<UserMemoryCandidateEntity>()
                    .eq(UserMemoryCandidateEntity::getId, candidate.getId())
                    .eq(UserMemoryCandidateEntity::getUserId, candidate.getUserId())
                    .eq(UserMemoryCandidateEntity::getStatus, PENDING)
                    .set(UserMemoryCandidateEntity::getConfidence, effective)
                    .set(UserMemoryCandidateEntity::getStatus, APPROVED));
            if (updated == 0) return;
        } catch (DuplicateKeyException duplicate) {
            // Another worker won the same promotion. The transaction remains idempotent:
            // reload and merge into the existing approved branch on the next delivery.
            log.info("忽略重复候选升级冲突，candidateId={}，userId={}", candidate.getId(), candidate.getUserId());
            return;
        }
        notifyFormalized(candidate.getUserId(), candidate.getAttributeKey(), candidate.getAttributeValue(), "多次证据已满足升级条件");
    }

    private UserMemoryCandidateEntity findApprovedEquivalentCandidate(UserMemoryCandidateEntity candidate) {
        if (candidate == null || candidate.getId() == null) return null;
        List<UserMemoryCandidateEntity> approved = candidateMapper.selectList(new LambdaQueryWrapper<UserMemoryCandidateEntity>()
                .eq(UserMemoryCandidateEntity::getUserId, candidate.getUserId())
                .eq(UserMemoryCandidateEntity::getMemoryType, candidate.getMemoryType())
                .eq(UserMemoryCandidateEntity::getAttributeKey, candidate.getAttributeKey())
                .eq(UserMemoryCandidateEntity::getStatus, APPROVED)
                .orderByAsc(UserMemoryCandidateEntity::getCreatedAt)
                .orderByAsc(UserMemoryCandidateEntity::getId)
                .last("FOR UPDATE"));
        return approved.stream()
                .filter(other -> candidateValuesCompatible(other.getAttributeValue(), candidate.getAttributeValue()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Rebind a pending branch to an existing approved branch while retaining every unique
     * evidence/source row. Exact duplicate evidence is one fact and is therefore collapsed
     * to the canonical row protected by uk_memory_evidence_source.
     */
    private void mergePendingIntoApproved(UserMemoryCandidateEntity source, UserMemoryCandidateEntity target) {
        if (source == null || target == null || source.getId() == null || target.getId() == null
                || source.getId().equals(target.getId()) || !PENDING.equals(source.getStatus())
                || !APPROVED.equals(target.getStatus())) return;

        Long memoryId = resolveFormalMemoryForCandidate(source.getUserId(), target);
        moveCandidateEvidence(source, target, memoryId);
        target.setConfidence(Math.max(target.getConfidence() == null ? 0 : target.getConfidence(),
                source.getConfidence() == null ? 0 : source.getConfidence()));
        target.setEvidenceSummary(appendSummary(target.getEvidenceSummary(), source.getEvidenceSummary()));
        if (target.getSourceDiaryId() == null) target.setSourceDiaryId(source.getSourceDiaryId());
        if (target.getSourceConversationId() == null) target.setSourceConversationId(source.getSourceConversationId());
        candidateMapper.updateById(target);

        source.setStatus(MERGED);
        source.setMergedIntoId(target.getId());
        source.setMergeReason("与已确认候选等价，证据已转移");
        candidateMapper.updateById(source);
        if (memoryId != null) touchFormalMemory(memoryId, target.getConfidence());
    }

    private UserMemoryEvidenceEntity findEquivalentEvidence(UserMemoryEvidenceEntity evidence, Long targetCandidateId,
                                                              Long memoryId) {
        LambdaQueryWrapper<UserMemoryEvidenceEntity> wrapper = new LambdaQueryWrapper<UserMemoryEvidenceEntity>()
                .eq(UserMemoryEvidenceEntity::getUserId, evidence.getUserId())
                .eq(UserMemoryEvidenceEntity::getSourceType, evidence.getSourceType())
                .eq(UserMemoryEvidenceEntity::getEvidenceText, evidence.getEvidenceText())
                .ne(UserMemoryEvidenceEntity::getId, evidence.getId())
                .last("LIMIT 1");
        if (evidence.getSourceDiaryId() == null) wrapper.isNull(UserMemoryEvidenceEntity::getSourceDiaryId);
        else wrapper.eq(UserMemoryEvidenceEntity::getSourceDiaryId, evidence.getSourceDiaryId());
        if (evidence.getSourceConversationId() == null) wrapper.isNull(UserMemoryEvidenceEntity::getSourceConversationId);
        else wrapper.eq(UserMemoryEvidenceEntity::getSourceConversationId, evidence.getSourceConversationId());
        UserMemoryEvidenceEntity existing = evidenceMapper.selectOne(wrapper);
        if (existing == null) return null;
        existing.setCandidateId(targetCandidateId);
        if (existing.getMemoryId() == null && memoryId != null) existing.setMemoryId(memoryId);
        return existing;
    }

    private Long resolveFormalMemoryForCandidate(long userId, UserMemoryCandidateEntity candidate) {
        List<UserMemoryEvidenceEntity> linked = evidenceMapper.selectList(new LambdaQueryWrapper<UserMemoryEvidenceEntity>()
                .eq(UserMemoryEvidenceEntity::getUserId, userId)
                .eq(UserMemoryEvidenceEntity::getCandidateId, candidate.getId())
                .isNotNull(UserMemoryEvidenceEntity::getMemoryId)
                .orderByDesc(UserMemoryEvidenceEntity::getCreatedAt)
                .orderByDesc(UserMemoryEvidenceEntity::getId));
        for (UserMemoryEvidenceEntity evidence : linked) {
            UserProfileMemoryEntity memory = memoryMapper.selectOne(new LambdaQueryWrapper<UserProfileMemoryEntity>()
                    .eq(UserProfileMemoryEntity::getId, evidence.getMemoryId())
                    .eq(UserProfileMemoryEntity::getUserId, userId)
                    .eq(UserProfileMemoryEntity::getStatus, ACTIVE)
                    .last("LIMIT 1"));
            if (memory != null && candidateValuesCompatible(memory.getAttributeValue(), candidate.getAttributeValue())) {
                return memory.getId();
            }
        }
        List<UserProfileMemoryEntity> activeMemories = memoryMapper.selectList(new LambdaQueryWrapper<UserProfileMemoryEntity>()
                .eq(UserProfileMemoryEntity::getUserId, userId)
                .eq(UserProfileMemoryEntity::getAttributeKey, candidate.getAttributeKey())
                .eq(UserProfileMemoryEntity::getStatus, ACTIVE)
                .orderByDesc(UserProfileMemoryEntity::getUpdatedAt)
                .orderByDesc(UserProfileMemoryEntity::getId));
        return activeMemories.stream()
                .filter(memory -> candidateValuesCompatible(memory.getAttributeValue(), candidate.getAttributeValue()))
                .map(UserProfileMemoryEntity::getId)
                .findFirst()
                .orElse(null);
    }

    private void touchFormalMemory(Long memoryId, Double confidence) {
        if (memoryId == null) return;
        UserProfileMemoryEntity memory = memoryMapper.selectById(memoryId);
        if (memory == null || !ACTIVE.equals(memory.getStatus())) return;
        memory.setConfidence(Math.max(memory.getConfidence() == null ? 0 : memory.getConfidence(),
                confidence == null ? 0 : confidence));
        memory.setLastEvidenceAt(LocalDateTime.now());
        memory.setUpdatedAt(LocalDateTime.now());
        memory.setUpdateTime(LocalDateTime.now());
        memoryMapper.updateById(memory);
    }

    private UserProfileMemoryEntity saveFormal(Long userId, String key, String value, String type, String source,
                                               Long diaryId, Long conversationId, double confidence, LocalDate validFrom,
                                               String reason, Boolean requestedIsCore) {
        LocalDateTime now = LocalDateTime.now();
        UserProfileMemoryEntity old = memoryMapper.selectOne(new LambdaQueryWrapper<UserProfileMemoryEntity>()
                .eq(UserProfileMemoryEntity::getUserId, userId).eq(UserProfileMemoryEntity::getAttributeKey, key)
                .eq(UserProfileMemoryEntity::getStatus, ACTIVE).last("LIMIT 1"));
        if (old != null && old.getValidUntil() != null && old.getValidUntil().isBefore(LocalDate.now())) {
            old.setStatus("expired");
            old.setSupersededAt(now);
            old.setSupersededReason("VALID_UNTIL_REACHED");
            memoryMapper.updateById(old);
            old = null;
        }
        if (old != null && value.equals(old.getAttributeValue())) {
            if (!MemorySafetyPolicy.allowCore(old.getMemoryType(), old.getAttributeKey(), value)) {
                old.setIsCore(false);
            }
            // 同值记忆复用当前版本，但不能丢失这次确认带来的真实来源。
            if (diaryId != null) old.setSourceDiaryId(diaryId);
            if (conversationId != null) old.setSourceConversationId(conversationId);
            if (source != null && !source.isBlank()) old.setSourceType(source);
            old.setConfidence(Math.max(old.getConfidence() == null ? 0 : old.getConfidence(), confidence));
            old.setLastEvidenceAt(now);
            old.setUpdatedAt(now);
            old.setUpdateTime(now);
            memoryMapper.updateById(old);
            return old;
        }
        if (old != null) {
            old.setStatus("superseded");
            old.setValidUntil(validFrom == null ? LocalDate.now() : validFrom);
            old.setSupersededAt(now);
            old.setSupersededReason(reason);
            memoryMapper.updateById(old);
        }
        UserProfileMemoryEntity next = new UserProfileMemoryEntity();
        next.setUserId(userId);
        next.setAttributeKey(key);
        next.setAttributeValue(value);
        next.setUpdateTime(now);
        next.setUpdatedAt(now);
        next.setMemoryType(type);
        next.setSourceType(source);
        next.setSourceDiaryId(diaryId);
        next.setSourceConversationId(conversationId);
        next.setConfidence(confidence);
        next.setValidFrom(validFrom == null ? LocalDate.now() : validFrom);
        next.setLastEvidenceAt(now);
        next.setStatus(ACTIVE);
        next.setPreviousMemoryId(old == null ? null : old.getId());
        next.setIsCore(MemorySafetyPolicy.allowCore(type, key, value)
                && (requestedIsCore != null ? requestedIsCore : ("preference".equals(type) || "relationship".equals(type))));
        memoryMapper.insert(next);
        return next;
    }

    private void addEvidence(Long userId, Long memoryId, Long candidateId, String source, Long diaryId, Long conversationId,
                              String text, LocalDate date, Double modelConfidence, double quality) {
        if (text == null || text.isBlank()) return;
        boolean exists = evidenceMapper.selectCount(new LambdaQueryWrapper<UserMemoryEvidenceEntity>()
                .eq(UserMemoryEvidenceEntity::getUserId, userId).eq(UserMemoryEvidenceEntity::getSourceType, source)
                .eq(diaryId != null, UserMemoryEvidenceEntity::getSourceDiaryId, diaryId)
                .eq(conversationId != null, UserMemoryEvidenceEntity::getSourceConversationId, conversationId)
                .eq(UserMemoryEvidenceEntity::getEvidenceText, text)) > 0;
        if (exists) return;
        UserMemoryEvidenceEntity evidence = new UserMemoryEvidenceEntity();
        evidence.setUserId(userId); evidence.setMemoryId(memoryId); evidence.setCandidateId(candidateId);
        evidence.setSourceType(source); evidence.setSourceDiaryId(diaryId); evidence.setSourceConversationId(conversationId);
        evidence.setEvidenceText(text); evidence.setEvidenceDate(date); evidence.setModelConfidence(scoreConfidence(modelConfidence));
        evidence.setEvidenceQuality(Math.max(0, Math.min(1, quality))); evidenceMapper.insert(evidence);
    }

    private boolean isRejected(long userId, String type, String key, String value) {
        return rejectionMapper.selectCount(new LambdaQueryWrapper<UserMemoryRejectionEntity>()
                .eq(UserMemoryRejectionEntity::getUserId, userId).eq(UserMemoryRejectionEntity::getNormalizedKey, normalize(key))
                .eq(UserMemoryRejectionEntity::getMemoryType, type)
                .eq(UserMemoryRejectionEntity::getNormalizedValue, normalize(value))
                .gt(UserMemoryRejectionEntity::getExpiresAt, LocalDateTime.now())) > 0;
    }

    private void addRejection(long userId, String type, String key, String value, String reason) {
        UserMemoryRejectionEntity rejection = new UserMemoryRejectionEntity();
        rejection.setUserId(userId); rejection.setMemoryType(type); rejection.setNormalizedKey(normalize(key));
        rejection.setNormalizedValue(normalize(value)); rejection.setRejectionType(reason);
        rejection.setCreatedAt(LocalDateTime.now()); rejection.setExpiresAt(LocalDateTime.now().plusDays(REJECTION_DAYS));
        rejectionMapper.insert(rejection);
    }

    private void rejectActive(long userId, String key, String type, String reason) {
        UserProfileMemoryEntity old = memoryMapper.selectOne(new LambdaQueryWrapper<UserProfileMemoryEntity>()
                .eq(UserProfileMemoryEntity::getUserId, userId).eq(UserProfileMemoryEntity::getAttributeKey, key)
                .eq(UserProfileMemoryEntity::getStatus, ACTIVE).last("LIMIT 1"));
        if (old != null) {
            old.setStatus("rejected"); old.setValidUntil(LocalDate.now()); old.setSupersededAt(LocalDateTime.now());
            old.setSupersededReason(reason); memoryMapper.updateById(old);
            addRejection(userId, type, key, old.getAttributeValue(), reason);
        }
    }

    private UserMemoryCandidateEntity findCandidate(long userId, String key, String type, String value) {
        List<UserMemoryCandidateEntity> candidates = candidateMapper.selectList(new LambdaQueryWrapper<UserMemoryCandidateEntity>()
                .eq(UserMemoryCandidateEntity::getUserId, userId)
                .eq(UserMemoryCandidateEntity::getAttributeKey, key)
                .eq(UserMemoryCandidateEntity::getMemoryType, type)
                .in(UserMemoryCandidateEntity::getStatus, PENDING, APPROVED)
                .orderByAsc(UserMemoryCandidateEntity::getCreatedAt)
                .orderByAsc(UserMemoryCandidateEntity::getId)
                .last("FOR UPDATE"));
        UserMemoryCandidateEntity approved = candidates.stream()
                .filter(candidate -> APPROVED.equals(candidate.getStatus())
                        && normalize(value).equals(candidate.getNormalizedValue()))
                .findFirst().orElse(null);
        if (approved != null) {
            candidates.stream()
                    .filter(candidate -> PENDING.equals(candidate.getStatus())
                            && candidateValuesCompatible(candidate.getAttributeValue(), value))
                    .forEach(pending -> mergePendingIntoApproved(pending, approved));
            return approved;
        }
        List<UserMemoryCandidateEntity> pending = candidates.stream()
                .filter(candidate -> PENDING.equals(candidate.getStatus()))
                .toList();
        mergeCompatibleCandidates(pending);
        return pending.stream()
                .filter(candidate -> PENDING.equals(candidate.getStatus())
                        && candidateValuesCompatible(candidate.getAttributeValue(), value))
                .findFirst()
                .orElse(null);
    }

    private int mergeCompatibleCandidates(List<UserMemoryCandidateEntity> candidates) {
        if (candidates == null || candidates.size() < 2) return 0;
        int merged = 0;
        for (int i = 0; i < candidates.size(); i++) {
            UserMemoryCandidateEntity target = candidates.get(i);
            if (!PENDING.equals(target.getStatus())) continue;
            for (int j = i + 1; j < candidates.size(); j++) {
                UserMemoryCandidateEntity source = candidates.get(j);
                if (!PENDING.equals(source.getStatus()) || !candidateValuesCompatible(target.getAttributeValue(), source.getAttributeValue())) continue;
                mergeCandidateInto(source, target);
                merged++;
            }
        }
        return merged;
    }

    private void mergeCandidateInto(UserMemoryCandidateEntity source, UserMemoryCandidateEntity target) {
        moveCandidateEvidence(source, target, null);
        target.setConfidence(Math.max(target.getConfidence() == null ? 0 : target.getConfidence(), source.getConfidence() == null ? 0 : source.getConfidence()));
        target.setEvidenceSummary(appendSummary(target.getEvidenceSummary(), source.getEvidenceSummary()));
        if (target.getAttributeValue() == null || source.getAttributeValue() != null && source.getAttributeValue().length() > target.getAttributeValue().length()) {
            target.setAttributeValue(source.getAttributeValue());
        }
        if (target.getSourceDiaryId() == null) target.setSourceDiaryId(source.getSourceDiaryId());
        if (target.getSourceConversationId() == null) target.setSourceConversationId(source.getSourceConversationId());
        if (target.getValidFrom() == null || source.getValidFrom() != null && source.getValidFrom().isBefore(target.getValidFrom())) target.setValidFrom(source.getValidFrom());
        target.setUpdatedAt(LocalDateTime.now());
        candidateMapper.updateById(target);

        source.setStatus("MERGED");
        source.setMergedIntoId(target.getId());
        source.setMergeReason("同义候选合并，证据已转移");
        candidateMapper.updateById(source);
    }

    private void moveCandidateEvidence(UserMemoryCandidateEntity source, UserMemoryCandidateEntity target, Long memoryId) {
        List<UserMemoryEvidenceEntity> sourceEvidence = evidenceMapper.selectList(new LambdaQueryWrapper<UserMemoryEvidenceEntity>()
                .eq(UserMemoryEvidenceEntity::getUserId, source.getUserId())
                .eq(UserMemoryEvidenceEntity::getCandidateId, source.getId())
                .orderByAsc(UserMemoryEvidenceEntity::getId));
        for (UserMemoryEvidenceEntity evidence : sourceEvidence) {
            UserMemoryEvidenceEntity duplicate = findEquivalentEvidence(evidence, target.getId(), memoryId);
            if (duplicate != null && !duplicate.getId().equals(evidence.getId())) {
                evidenceMapper.updateById(duplicate);
                evidenceMapper.deleteById(evidence.getId());
                continue;
            }
            evidence.setCandidateId(target.getId());
            if (memoryId != null) evidence.setMemoryId(memoryId);
            evidenceMapper.updateById(evidence);
        }
    }

    private boolean candidateValuesCompatible(String left, String right) {
        return MemoryCandidateMergePolicy.compatible(left, right);
    }

    private String appendSummary(String existing, String addition) {
        String left = clean(existing, 1000);
        String right = clean(addition, 1000);
        if (right.isBlank() || left.contains(right)) return left;
        if (left.isBlank()) return right;
        String result = left + "；" + right;
        return result.length() > 1000 ? result.substring(0, 1000) : result;
    }

    private UserMemoryCandidateEntity ownedCandidate(long userId, long id) {
        UserMemoryCandidateEntity entity = candidateMapper.selectById(id);
        if (entity == null || !Long.valueOf(userId).equals(entity.getUserId())) throw new ResponseStatusException(BAD_REQUEST, "候选记忆不存在");
        return entity;
    }

    private UserProfileMemoryEntity ownedFormal(long userId, long id) {
        UserProfileMemoryEntity entity = memoryMapper.selectById(id);
        if (entity == null || !Long.valueOf(userId).equals(entity.getUserId()) || !ACTIVE.equals(entity.getStatus()))
            throw new ResponseStatusException(BAD_REQUEST, "记忆记录不存在或无权操作");
        return entity;
    }

    private void reindex(long userId) { ragMemoryService.indexUserProfileAsync(userId, current(userId)); }

    private boolean verifiedExplicitEvidence(String source, String evidence, String sourceText) {
        if (!"diary_inferred".equals(source) && !"chat_candidate".equals(source)) return "explicit".equals(source);
        if (evidence == null || evidence.isBlank() || sourceText == null || sourceText.isBlank()) return false;
        String normalizedEvidence = normalize(evidence);
        String normalizedSource = normalize(sourceText);
        return (normalizedSource.contains(normalizedEvidence) || normalizedEvidence.contains(normalizedSource))
                && containsExplicitUserMarker(normalizedSource);
    }

    /**
     * 模型只能提供证据摘录，不能把自己的摘要或推理当作用户原话落库。
     * 无法在用户输入中定位的摘录退回到完整用户输入，保证来源仍可审计。
     */
    private String groundEvidence(String source, String evidence, String sourceText) {
        String grounded = clean(sourceText, 2000);
        if (evidence.isBlank()) return grounded;
        if (!("diary_inferred".equals(source) || "chat_candidate".equals(source))) return evidence;
        if (grounded.isBlank()) return "";
        String normalizedEvidence = normalize(evidence);
        String normalizedSource = normalize(sourceText);
        return normalizedSource.contains(normalizedEvidence) || normalizedEvidence.contains(normalizedSource)
                ? evidence : grounded;
    }

    private boolean isEvidenceGrounded(String source, String evidence, String sourceText) {
        if (evidence == null || evidence.isBlank() || sourceText == null || sourceText.isBlank()) return false;
        if (!("diary_inferred".equals(source) || "chat_candidate".equals(source))) return true;
        String normalizedEvidence = normalize(evidence);
        String normalizedSource = normalize(sourceText);
        return !normalizedEvidence.isBlank()
                && (normalizedSource.contains(normalizedEvidence) || normalizedEvidence.contains(normalizedSource));
    }

    private boolean containsExplicitUserMarker(String source) {
        return List.of("我喜欢", "我不喜欢", "我偏好", "我习惯", "我是", "我会", "我想", "我希望",
                "我一直", "我通常", "对我来说", "我的目标", "我不再").stream().anyMatch(source::contains);
    }

    private void notifyFormalized(long userId, String key, String value, String reason) {
        if (notificationService == null) return;
        String summary = "记忆中心已更新：**" + clean(key, 64) + "**\n" + clean(value, 180) + "\n\n" + reason;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            notificationService.notifyMemoryUpdated(userId, summary);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notificationService.notifyMemoryUpdated(userId, summary);
            }
        });
    }
    private String normalizeSource(String value) {
        for (String allowed : SOURCE_TYPES) if (allowed.equals(value)) return value;
        throw new IllegalArgumentException("不支持的记忆来源类型");
    }
    private String normalizeType(String value) {
        for (String allowed : MEMORY_TYPES) if (allowed.equals(value)) return value;
        throw new IllegalArgumentException("不支持的记忆类型");
    }
    private String normalize(String value) { return value == null ? "" : value.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT); }
    private String clean(String value, int max) { if (value == null) return ""; String s = value.replaceAll("[\\r\\n\\t\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim(); return s.length() > max ? s.substring(0, max) : s; }
    private double scoreConfidence(Double value) { return value == null ? .5 : Math.max(0, Math.min(1, value)); }
    private double evidenceQuality(MemoryExtractionService.MemoryAttribute attr) { return attr.evidence() == null || attr.evidence().isBlank() ? .5 : 1.0; }
    private boolean containsNegation(String text) {
        if (text == null) return false;
        return text.contains("不再") || text.contains("不喜欢") || text.contains("不是") || text.contains("否认");
    }
    private double halfLife(String type) { return switch (type) { case "preference", "relationship" -> 180; case "habit" -> 90; case "pattern" -> 60; case "event" -> 30; default -> 7; }; }
    private boolean isShortTermExpired(UserProfileMemoryEntity memory) {
        return "short_term_state".equals(memory.getMemoryType())
                && memory.getLastEvidenceAt() != null
                && memory.getLastEvidenceAt().plusDays(7).isBefore(LocalDateTime.now());
    }
}
