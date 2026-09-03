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
import com.moodcopilot.notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class MemoryOrchestrator {
    private static final int REJECTION_DAYS = 180;
    private static final String ACTIVE = "active";
    private static final String PENDING = "PENDING";
    private static final String[] MEMORY_TYPES = {"preference", "relationship", "habit", "event", "short_term_state", "pattern"};
    private static final String[] SOURCE_TYPES = {"explicit", "diary_inferred", "chat_candidate", "system"};

    private final UserProfileMemoryMapper memoryMapper;
    private final UserMemoryCandidateMapper candidateMapper;
    private final UserMemoryEvidenceMapper evidenceMapper;
    private final UserMemoryRejectionMapper rejectionMapper;
    private final RagMemoryService ragMemoryService;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    @Autowired
    public MemoryOrchestrator(UserProfileMemoryMapper memoryMapper,
                              UserMemoryCandidateMapper candidateMapper,
                              UserMemoryEvidenceMapper evidenceMapper,
                              UserMemoryRejectionMapper rejectionMapper,
                              RagMemoryService ragMemoryService,
                              ObjectMapper objectMapper,
                              NotificationService notificationService) {
        this.memoryMapper = memoryMapper;
        this.candidateMapper = candidateMapper;
        this.evidenceMapper = evidenceMapper;
        this.rejectionMapper = rejectionMapper;
        this.ragMemoryService = ragMemoryService;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
    }

    /** 保留无通知依赖的构造入口，便于纯记忆规则单测隔离通知副作用。 */
    public MemoryOrchestrator(UserProfileMemoryMapper memoryMapper,
                              UserMemoryCandidateMapper candidateMapper,
                              UserMemoryEvidenceMapper evidenceMapper,
                              UserMemoryRejectionMapper rejectionMapper,
                              RagMemoryService ragMemoryService,
                              ObjectMapper objectMapper) {
        this(memoryMapper, candidateMapper, evidenceMapper, rejectionMapper, ragMemoryService, objectMapper, null);
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
            String type = normalizeType(attr.memoryType());
            String assertion = attr.assertionType() == null ? "inferred" : attr.assertionType().toLowerCase(Locale.ROOT);
            if (!Set.of("explicit", "inferred", "negated").contains(assertion)) assertion = "inferred";
            String evidence = clean(attr.evidence(), 2000);
            if (evidence.isBlank()) evidence = clean(defaultEvidence, 2000);
            String actualSource = "explicit".equals(safeSource)
                    || ("explicit".equals(assertion) && verifiedExplicitEvidence(safeSource, evidence, defaultEvidence))
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
                        "explicit evidence", attr.isCore());
                addEvidence(userId, memory.getId(), null, actualSource, sourceDiaryId, sourceConversationId,
                        evidence, date, attr.confidence(), 1.0);
                notifyFormalized(userId, key, value, "用户明确确认");
                continue;
            }
            if (isRejected(userId, type, key, value)) continue;
            UserMemoryCandidateEntity candidate = findCandidate(userId, key, value);
            if (candidate == null) {
                candidate = new UserMemoryCandidateEntity();
                candidate.setUserId(userId);
                candidate.setAttributeKey(key);
                candidate.setNormalizedValue(normalize(value));
                candidate.setAttributeValue(value);
                candidate.setMemoryType(type);
                candidate.setSourceType(safeSource);
                candidate.setConfidence(scoreConfidence(attr.confidence()));
                candidate.setIsCore(attr.isCore());
                candidate.setStatus(PENDING);
                candidate.setEvidenceSummary(evidence);
                candidate.setSourceDiaryId(sourceDiaryId);
                candidate.setSourceConversationId(sourceConversationId);
                candidate.setValidFrom(attr.validFrom() == null ? date : attr.validFrom());
                candidate.setValidUntil(attr.validUntil());
                candidateMapper.insert(candidate);
            } else {
                candidate.setConfidence(Math.max(candidate.getConfidence() == null ? 0 : candidate.getConfidence(),
                        scoreConfidence(attr.confidence())));
                if (candidate.getEvidenceSummary() == null || candidate.getEvidenceSummary().isBlank()) {
                    candidate.setEvidenceSummary(evidence);
                }
                candidateMapper.updateById(candidate);
            }
            addEvidence(userId, null, candidate.getId(), safeSource, sourceDiaryId, sourceConversationId,
                    evidence, date, attr.confidence(), evidenceQuality(attr));
            maybePromote(candidate);
        }
        reindex(userId);
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

    public List<UserProfileMemoryEntity> current(long userId) {
        return memoryMapper.selectList(new LambdaQueryWrapper<UserProfileMemoryEntity>()
                .eq(UserProfileMemoryEntity::getUserId, userId)
                .eq(UserProfileMemoryEntity::getStatus, ACTIVE)
                .and(w -> w.isNull(UserProfileMemoryEntity::getValidUntil).or().ge(UserProfileMemoryEntity::getValidUntil, LocalDate.now()))
                .orderByAsc(UserProfileMemoryEntity::getAttributeKey)).stream()
                .filter(memory -> !isShortTermExpired(memory))
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

    private void maybePromote(UserMemoryCandidateEntity candidate) {
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
        UserProfileMemoryEntity memory = saveFormal(candidate.getUserId(), candidate.getAttributeKey(), candidate.getAttributeValue(),
                candidate.getMemoryType(), candidate.getSourceType(), candidate.getSourceDiaryId(), candidate.getSourceConversationId(),
                effective, candidate.getValidFrom(), "AUTO_UPGRADED", candidate.getIsCore());
        evidenceMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<UserMemoryEvidenceEntity>()
                .eq(UserMemoryEvidenceEntity::getCandidateId, candidate.getId()).set(UserMemoryEvidenceEntity::getMemoryId, memory.getId()));
        candidate.setConfidence(effective);
        candidate.setStatus("APPROVED");
        candidateMapper.updateById(candidate);
        notifyFormalized(candidate.getUserId(), candidate.getAttributeKey(), candidate.getAttributeValue(), "多次证据已满足升级条件");
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
        next.setMemoryType(type);
        next.setSourceType(source);
        next.setSourceDiaryId(diaryId);
        next.setSourceConversationId(conversationId);
        next.setConfidence(confidence);
        next.setValidFrom(validFrom == null ? LocalDate.now() : validFrom);
        next.setLastEvidenceAt(now);
        next.setStatus(ACTIVE);
        next.setPreviousMemoryId(old == null ? null : old.getId());
        next.setIsCore(!"short_term_state".equals(type)
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

    private UserMemoryCandidateEntity findCandidate(long userId, String key, String value) {
        return candidateMapper.selectOne(new LambdaQueryWrapper<UserMemoryCandidateEntity>()
                .eq(UserMemoryCandidateEntity::getUserId, userId).eq(UserMemoryCandidateEntity::getAttributeKey, key)
                .eq(UserMemoryCandidateEntity::getNormalizedValue, normalize(value)).eq(UserMemoryCandidateEntity::getStatus, PENDING)
                .last("LIMIT 1"));
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

    private void reindex(long userId) { ragMemoryService.indexUserProfile(userId, current(userId)); }

    private boolean verifiedExplicitEvidence(String source, String evidence, String sourceText) {
        if (!"diary_inferred".equals(source) && !"chat_candidate".equals(source)) return "explicit".equals(source);
        if (evidence == null || evidence.isBlank() || sourceText == null || sourceText.isBlank()) return false;
        String normalizedEvidence = normalize(evidence);
        String normalizedSource = normalize(sourceText);
        return (normalizedSource.contains(normalizedEvidence) || normalizedEvidence.contains(normalizedSource))
                && containsExplicitUserMarker(normalizedSource);
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
