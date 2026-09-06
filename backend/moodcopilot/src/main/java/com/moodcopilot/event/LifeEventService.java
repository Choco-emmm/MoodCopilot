package com.moodcopilot.event;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.ai.JsonUtils;
import com.moodcopilot.ai.ContextPurpose;
import com.moodcopilot.ai.PromptComposer;
import com.moodcopilot.ai.SystemPolicy;
import com.moodcopilot.ai.TaskContext;
import com.moodcopilot.config.AiPromptProperties;
import com.moodcopilot.entity.DiaryAnalysisEntity;
import com.moodcopilot.entity.DiaryEntity;
import com.moodcopilot.entity.UserLifeEventEntity;
import com.moodcopilot.mapper.DiaryAnalysisMapper;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.mapper.UserLifeEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class LifeEventService {
    private static final Logger log = LoggerFactory.getLogger(LifeEventService.class);
    private static final String LOCK_PREFIX = "life-event:extract-lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final ZoneId DEFAULT_EVENT_TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int DEFAULT_FOLLOW_UP_DELAY_DAYS = 2;
    private static final int MAX_FOLLOW_UP_DELAY_DAYS = 30;
    private static final int MAX_AUTOMATIC_FOLLOW_UPS = 2;

    private final UserLifeEventMapper userLifeEventMapper;
    private final DiaryMapper diaryMapper;
    private final DiaryAnalysisMapper diaryAnalysisMapper;
    private final ChatClient analysisChatClient;
    private final ObjectMapper objectMapper;
    private final AiPromptProperties aiPrompts;
    private final StringRedisTemplate redisTemplate;
    private final ZoneId eventTimeZone;
    private final LifeChapterService lifeChapterService;
    private final PromptComposer promptComposer;

    @Autowired(required = false)
    private com.moodcopilot.ai.ContextMetadataRecorder contextMetadataRecorder;

    @Autowired
    public LifeEventService(UserLifeEventMapper userLifeEventMapper, DiaryMapper diaryMapper,
                            DiaryAnalysisMapper diaryAnalysisMapper,
                            @Qualifier("analysisChatClient") ChatClient analysisChatClient,
                            ObjectMapper objectMapper, AiPromptProperties aiPrompts,
                            StringRedisTemplate redisTemplate,
                            @org.springframework.beans.factory.annotation.Value("${moodcopilot.time-zone:Asia/Shanghai}") String timeZoneId,
                            LifeChapterService lifeChapterService,
                            PromptComposer promptComposer) {
        this(userLifeEventMapper, diaryMapper, diaryAnalysisMapper, analysisChatClient, objectMapper,
                aiPrompts, redisTemplate, parseTimeZone(timeZoneId), lifeChapterService, promptComposer);
    }

    private LifeEventService(UserLifeEventMapper userLifeEventMapper, DiaryMapper diaryMapper,
                             DiaryAnalysisMapper diaryAnalysisMapper, ChatClient analysisChatClient,
                             ObjectMapper objectMapper, AiPromptProperties aiPrompts,
                             StringRedisTemplate redisTemplate, ZoneId eventTimeZone, LifeChapterService lifeChapterService,
                             PromptComposer promptComposer) {
        this.userLifeEventMapper = userLifeEventMapper;
        this.diaryMapper = diaryMapper;
        this.diaryAnalysisMapper = diaryAnalysisMapper;
        this.analysisChatClient = analysisChatClient;
        this.objectMapper = objectMapper;
        this.aiPrompts = aiPrompts;
        this.redisTemplate = redisTemplate;
        this.eventTimeZone = eventTimeZone;
        this.lifeChapterService = lifeChapterService;
        this.promptComposer = promptComposer;
    }

    /** 兼容旧测试；生产 Bean 使用包含摘要 Mapper 和 Redis 的构造器。 */
    public LifeEventService(UserLifeEventMapper userLifeEventMapper, DiaryMapper diaryMapper,
                            ChatClient analysisChatClient, ObjectMapper objectMapper,
                            AiPromptProperties aiPrompts) {
        this(userLifeEventMapper, diaryMapper, null, analysisChatClient, objectMapper, aiPrompts,
                null, DEFAULT_EVENT_TIME_ZONE, null, null);
    }

    /** 兼容直接构造 Service 的测试；生产环境通过配置注入事件时区。 */
    public LifeEventService(UserLifeEventMapper userLifeEventMapper, DiaryMapper diaryMapper,
                            DiaryAnalysisMapper diaryAnalysisMapper, ChatClient analysisChatClient,
                            ObjectMapper objectMapper, AiPromptProperties aiPrompts,
                            StringRedisTemplate redisTemplate) {
        this(userLifeEventMapper, diaryMapper, diaryAnalysisMapper, analysisChatClient, objectMapper,
                aiPrompts, redisTemplate, DEFAULT_EVENT_TIME_ZONE, null, null);
    }

    public record FollowUpSuggestion(String timing, Integer delayDays, String reason) {}
    public record ExtractedLifeEvent(String title, String description, String temporalPhase,
                                     String targetDate, String endDate, String startTime, String endTime,
                                     BigDecimal importance, FollowUpSuggestion followUp) {
        public ExtractedLifeEvent(String title, String description, String targetDate,
                                  String endDate, String startTime, String endTime) {
            this(title, description, null, targetDate, endDate, startTime, endTime, null, null);
        }
    }
    public record LifeEventUpsertRequest(String title, String description, String targetDate,
                                         String endDate, String startTime, String endTime,
                                         List<Long> diaryIds) {}
    public record LifeEventView(Long id, String title, String description, String targetDate,
                                String endDate, String startTime, String endTime, String status,
                                List<Long> diaryIds, int diaryCount, Long lastDiaryId,
                                String followUpNote, String createdAt, String updatedAt,
                                String temporalPhase, String nextFollowUpAt, String lastFollowUpAt,
                                int followUpCount, String followUpReason, boolean followUpCompleted,
                                BigDecimal importance) {}
    public record LifeDiaryOption(Long id, String date, String excerpt, String summary) {}
    public record LifeDiaryPage(List<LifeDiaryOption> items, long total, int page, int size, boolean hasMore) {}

    @Transactional
    public void extractAndTrackLifeEvents(Long userId, Long diaryId, String content, LocalDateTime diaryCreatedAt) {
        if (content == null || content.isBlank() || content.length() < 10) return;
        try {
            LocalDate baseDate = diaryCreatedAt != null ? diaryCreatedAt.toLocalDate() : LocalDate.now(eventTimeZone);
            String prompt = "[日记记录日期]" + baseDate.format(DATE_FORMAT) + "\n\n[日记内容]\n" + content;
            if (contextMetadataRecorder != null) {
                contextMetadataRecorder.recordModelInvocation(userId, null, ContextPurpose.EVENT_REVIEW,
                        null, new TaskContext("GENERAL", "只提取来源明确的重要事件，不修改事件状态", List.of(), null),
                        "FLASH", "FLASH");
            }
            String response = analysisChatClient.prompt().system(eventExtractionSystemPrompt(userId))
                    .user(prompt).call().content();
            List<ExtractedLifeEvent> events = objectMapper.readValue(JsonUtils.cleanJson(response),
                    new TypeReference<List<ExtractedLifeEvent>>() {});
            if (events == null || events.isEmpty()) {
                log.info("事件提取完成，识别数量=0，userId={}，diaryId={}", userId, diaryId);
                return;
            }
            log.info("事件提取完成，识别数量={}，userId={}，diaryId={}", events.size(), userId, diaryId);
            withUserLock(userId, () -> {
                List<UserLifeEventEntity> existing = listMergeableEvents(userId);
                List<UserLifeEventEntity> deletedEvents = listDeletedEvents(userId);
                deduplicateExistingEvents(existing);
                for (ExtractedLifeEvent extracted : events) {
                    String title = clean(extracted.title(), 128);
                    if (title.isBlank()) {
                        log.info("事件未创建，userId={}, diaryId={}, 原因=事件名称为空", userId, diaryId);
                        continue;
                    }
                    String phase;
                    BigDecimal importance;
                    FollowUpSuggestion followUp;
                    try {
                        phase = validateTemporalPhase(extracted.temporalPhase());
                        if (phase == null && extracted.temporalPhase() != null && !extracted.temporalPhase().isBlank()) {
                            throw new IllegalArgumentException("阶段值只能是 UPCOMING、ONGOING 或 PAST");
                        }
                        importance = validateImportance(extracted.importance());
                        followUp = validateFollowUp(extracted.followUp());
                    } catch (IllegalArgumentException ex) {
                        log.info("事件未创建，userId={}, diaryId={}, 原因={}", userId, diaryId, ex.getMessage());
                        continue;
                    }
                    ParsedSchedule schedule;
                    try {
                        schedule = parseSchedule(extracted.targetDate(), extracted.endDate(), extracted.startTime(), extracted.endTime());
                    } catch (IllegalArgumentException ex) {
                        log.info("事件未创建，userId={}, diaryId={}, 原因={}", userId, diaryId, ex.getMessage());
                        continue;
                    }
                    UserLifeEventEntity matched = existing.stream()
                            .filter(candidate -> isSameEvent(candidate, title, schedule.startDate(), schedule.endDate(), content))
                            .findFirst().orElse(null);
                    if (matched == null) {
                        UserLifeEventEntity deletedMatch = deletedEvents.stream()
                                .filter(candidate -> isSameEvent(candidate, title, schedule.startDate(), schedule.endDate(), content))
                                .findFirst().orElse(null);
                        if (deletedMatch != null) {
                            log.info("事件未创建，userId={}，diaryId={}，原因=匹配到用户已删除事件，eventId={}", userId, diaryId, deletedMatch.getId());
                            continue;
                        }
                        UserLifeEventEntity created = new UserLifeEventEntity();
                        created.setUserId(userId);
                        created.setTitle(title);
                        created.setDescription(clean(extracted.description(), 1000));
                        applySchedule(created, schedule);
                        created.setStatus("PENDING");
                        applyTemporalAndFollowUp(created, phase, followUp, importance, schedule, false);
                        created.setDiaryIdsJson(writeIds(List.of(diaryId)));
                        created.setLastDiaryId(diaryId);
                        created.setCreatedAt(LocalDateTime.now());
                        created.setUpdatedAt(LocalDateTime.now());
                        userLifeEventMapper.insert(created);
                        log.info("事件提取完成，识别数量=1，事件已创建，eventId={}，diaryId={}，phase={}", created.getId(), diaryId, created.getTemporalPhase());
                        notifyTimelineChanged(userId, created.getId());
                        existing.add(created);
                    } else {
                        mergeEvent(matched, schedule, extracted.description(), diaryId, phase, followUp, importance);
                        userLifeEventMapper.updateById(matched);
                        log.info("事件已合并，eventId={}，diaryId={}，phase={}，status={}", matched.getId(), diaryId, matched.getTemporalPhase(), matched.getStatus());
                        notifyTimelineChanged(userId, matched.getId());
                    }
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("提取重要事件失败 userId={}, diaryId={}: {}", userId, diaryId, e.getMessage());
            throw new IllegalStateException("重要事件提取失败", e);
        }
    }

    private String eventExtractionSystemPrompt(Long userId) {
        String base = aiPrompts.getLifeEventExtractionSystemPrompt();
        if (promptComposer == null) {
            return SystemPolicy.text() + "\n\n" + base;
        }
        return promptComposer.compose(base, userId,
                new TaskContext("GENERAL", "只提取来源明确的重要事件，不修改事件状态", List.of(), null),
                ContextPurpose.EVENT_REVIEW, "");
    }

    @Transactional
    public LifeEventView createEvent(Long userId, LifeEventUpsertRequest request) {
        ParsedSchedule schedule = parseRequest(request);
        List<Long> diaryIds = validateDiaryIds(userId, request.diaryIds());
        UserLifeEventEntity entity = new UserLifeEventEntity();
        entity.setUserId(userId);
        entity.setTitle(clean(request.title(), 128));
        entity.setDescription(clean(request.description(), 1000));
        applySchedule(entity, schedule);
        entity.setStatus("PENDING");
        applyTemporalAndFollowUp(entity, null, null, null, schedule, false);
        entity.setDiaryIdsJson(writeIds(diaryIds));
        entity.setLastDiaryId(diaryIds.isEmpty() ? null : diaryIds.get(diaryIds.size() - 1));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        userLifeEventMapper.insert(entity);
        notifyTimelineChanged(userId, entity.getId());
        return toView(entity);
    }

    @Transactional
    public LifeEventView updateEvent(Long userId, Long eventId, LifeEventUpsertRequest request) {
        UserLifeEventEntity entity = requireOwned(userId, eventId);
        ParsedSchedule schedule = parseRequest(request);
        String title = clean(request.title(), 128);
        if (!normalize(entity.getTitle()).equals(normalize(title))) {
            LinkedHashSet<String> aliases = new LinkedHashSet<>(parseStrings(entity.getTitleAliasesJson()));
            if (entity.getTitle() != null && !entity.getTitle().isBlank()) aliases.add(entity.getTitle().trim());
            entity.setTitleAliasesJson(writeStrings(aliases));
        }
        entity.setTitle(title);
        entity.setDescription(clean(request.description(), 1000));
        applySchedule(entity, schedule);
        applyTemporalAndFollowUp(entity, null, null, entity.getImportance(), schedule, false);
        if (request.diaryIds() != null) {
            List<Long> diaryIds = validateDiaryIds(userId, request.diaryIds());
            entity.setDiaryIdsJson(writeIds(diaryIds));
            entity.setLastDiaryId(diaryIds.isEmpty() ? null : diaryIds.get(diaryIds.size() - 1));
        }
        entity.setUpdatedAt(LocalDateTime.now());
        userLifeEventMapper.updateById(entity);
        notifyTimelineChanged(userId, entity.getId());
        return toView(entity);
    }

    @Transactional
    public LifeEventView updateEventDiaries(Long userId, Long eventId, List<Long> requestedIds) {
        UserLifeEventEntity entity = requireOwned(userId, eventId);
        List<Long> ids = validateDiaryIds(userId, requestedIds);
        entity.setDiaryIdsJson(writeIds(ids));
        entity.setLastDiaryId(ids.isEmpty() ? null : ids.get(ids.size() - 1));
        entity.setUpdatedAt(LocalDateTime.now());
        userLifeEventMapper.updateById(entity);
        notifyTimelineChanged(userId, entity.getId());
        return toView(entity);
    }

    public LifeEventView getEvent(Long userId, Long eventId) { return toView(requireOwned(userId, eventId)); }

    public String currentTemporalPhase(UserLifeEventEntity entity) {
        return entity == null ? "PAST" : phaseFor(entity.getTargetDate(), entity.getEndDate(), entity.getStartTime(), entity.getEndTime());
    }

    public List<LifeEventView> listUserEvents(Long userId) {
        return userLifeEventMapper.selectList(new LambdaQueryWrapper<UserLifeEventEntity>()
                .eq(UserLifeEventEntity::getUserId, userId)
                .isNull(UserLifeEventEntity::getDeletedAt)
                .orderByDesc(UserLifeEventEntity::getTargetDate)
                .orderByDesc(UserLifeEventEntity::getUpdatedAt)).stream().map(this::toView).toList();
    }

    /**
     * Repairs values initialized by older migrations without using the database
     * server timezone. This is intentionally idempotent and never reopens an
     * event that the user has already marked as followed up.
     */
    @Transactional
    public int repairLegacyEventSchedules() {
        if (userLifeEventMapper == null) return 0;
        LocalDateTime now = nowInEventZone();
        int repaired = 0;
        List<UserLifeEventEntity> events = userLifeEventMapper.selectList(new LambdaQueryWrapper<UserLifeEventEntity>()
                .isNull(UserLifeEventEntity::getDeletedAt));
        for (UserLifeEventEntity entity : events) {
            boolean changed = false;
            String phase = phaseFor(entity.getTargetDate(), entity.getEndDate(), entity.getStartTime(), entity.getEndTime());
            if (!Objects.equals(entity.getTemporalPhase(), phase)) {
                entity.setTemporalPhase(phase);
                changed = true;
            }
            if ("FOLLOWED_UP".equalsIgnoreCase(entity.getStatus())) {
                if (!Boolean.TRUE.equals(entity.getFollowUpCompleted())) {
                    entity.setFollowUpCompleted(true);
                    changed = true;
                }
                if (entity.getNextFollowUpAt() != null) {
                    entity.setNextFollowUpAt(null);
                    changed = true;
                }
            } else if ("PENDING".equalsIgnoreCase(entity.getStatus())) {
                if (entity.getFollowUpCompleted() == null) {
                    entity.setFollowUpCompleted(false);
                    changed = true;
                }
                if (!Boolean.TRUE.equals(entity.getFollowUpCompleted()) && entity.getNextFollowUpAt() == null) {
                    entity.setNextFollowUpAt(initialFollowUpAt(entity, phase, null, DEFAULT_FOLLOW_UP_DELAY_DAYS, now));
                    changed = true;
                }
            }
            if (changed) {
                entity.setUpdatedAt(LocalDateTime.now());
                userLifeEventMapper.updateById(entity);
                repaired++;
            }
        }
        if (repaired > 0) {
            log.info("历史重要事件回访计划修复完成，修复数量={}，业务时区={}", repaired, eventTimeZone);
        }
        return repaired;
    }

    public LifeDiaryPage listUserDiaryOptions(Long userId, String keyword, LocalDate startDate,
                                               LocalDate endDate, int page, int size) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new ResponseStatusException(BAD_REQUEST, "日记筛选的结束日期不能早于开始日期");
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(1, size));
        LambdaQueryWrapper<DiaryEntity> query = new LambdaQueryWrapper<DiaryEntity>()
                .eq(DiaryEntity::getAuthorUserId, userId).eq(DiaryEntity::getIsDeleted, false);
        if (keyword != null && !keyword.isBlank()) {
            String escaped = keyword.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            query.and(w -> w.like(DiaryEntity::getContent, escaped)
                    .or().apply("JSON_UNQUOTE(JSON_EXTRACT(music_meta, '$.title')) LIKE {0}", "%" + escaped + "%")
                    .or().apply("JSON_UNQUOTE(JSON_EXTRACT(music_meta, '$.artist')) LIKE {0}", "%" + escaped + "%"));
        }
        if (startDate != null) query.ge(DiaryEntity::getCreatedAt, startDate.atStartOfDay());
        if (endDate != null) query.lt(DiaryEntity::getCreatedAt, endDate.plusDays(1).atStartOfDay());
        query.orderByDesc(DiaryEntity::getCreatedAt)
                .last("LIMIT " + ((long) (safePage - 1) * safeSize) + "," + safeSize);
        long total = diaryMapper.selectCount(new LambdaQueryWrapper<DiaryEntity>()
                .eq(DiaryEntity::getAuthorUserId, userId).eq(DiaryEntity::getIsDeleted, false)
                .and(keyword != null && !keyword.isBlank(), w -> {
                    String escaped = keyword.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                    w.like(DiaryEntity::getContent, escaped)
                            .or().apply("JSON_UNQUOTE(JSON_EXTRACT(music_meta, '$.title')) LIKE {0}", "%" + escaped + "%")
                            .or().apply("JSON_UNQUOTE(JSON_EXTRACT(music_meta, '$.artist')) LIKE {0}", "%" + escaped + "%");
                })
                .ge(startDate != null, DiaryEntity::getCreatedAt, startDate == null ? LocalDateTime.MIN : startDate.atStartOfDay())
                .lt(endDate != null, DiaryEntity::getCreatedAt, endDate == null ? LocalDateTime.MAX : endDate.plusDays(1).atStartOfDay()));
        List<DiaryEntity> diaries = diaryMapper.selectList(query);
        Map<Long, DiaryAnalysisEntity> analyses = Collections.emptyMap();
        if (diaryAnalysisMapper != null && !diaries.isEmpty()) {
            analyses = diaryAnalysisMapper.selectList(new LambdaQueryWrapper<DiaryAnalysisEntity>()
                    .in(DiaryAnalysisEntity::getDiaryId, diaries.stream().map(DiaryEntity::getId).toList()))
                    .stream().collect(java.util.stream.Collectors.toMap(DiaryAnalysisEntity::getDiaryId, a -> a, (a, b) -> a));
        }
        Map<Long, DiaryAnalysisEntity> finalAnalyses = analyses;
        List<LifeDiaryOption> items = diaries.stream().map(diary -> {
            String excerpt = plain(diary.getContent());
            if (excerpt.length() > 90) excerpt = excerpt.substring(0, 90) + "...";
            DiaryAnalysisEntity analysis = finalAnalyses.get(diary.getId());
            return new LifeDiaryOption(diary.getId(), diary.getCreatedAt() == null ? "" : diary.getCreatedAt().toLocalDate().toString(),
                    excerpt, analysis == null ? "" : clean(analysis.getSummary(), 160));
        }).toList();
        return new LifeDiaryPage(items, total, safePage, safeSize, (long) safePage * safeSize < total);
    }

    @Transactional
    public LifeEventView updateEventStatus(Long userId, Long eventId, String status, String note) {
        UserLifeEventEntity entity = requireOwned(userId, eventId);
        if (status != null && !status.isBlank()) {
            String normalized = status.toUpperCase(Locale.ROOT).trim();
            if (!Set.of("PENDING", "FOLLOWED_UP").contains(normalized))
                throw new ResponseStatusException(BAD_REQUEST, "事件状态只能是 PENDING 或 FOLLOWED_UP");
            entity.setStatus(normalized);
            if ("FOLLOWED_UP".equals(normalized)) {
                entity.setFollowUpCompleted(true);
                entity.setNextFollowUpAt(null);
            } else {
                entity.setFollowUpCompleted(false);
                if (entity.getNextFollowUpAt() == null) entity.setNextFollowUpAt(nowInEventZone());
            }
        }
        if (note != null) entity.setFollowUpNote(clean(note, 2000));
        entity.setUpdatedAt(LocalDateTime.now());
        userLifeEventMapper.updateById(entity);
        notifyTimelineChanged(userId, entity.getId());
        return toView(entity);
    }

    @Transactional
    public void softDeleteEvent(Long userId, Long eventId) {
        UserLifeEventEntity entity = findOwnedEventIncludingDeleted(userId, eventId);
        if (entity == null) throw new ResponseStatusException(NOT_FOUND, "事件不存在");
        if (entity.getDeletedAt() != null) return;
        entity.setDeletedAt(nowInEventZone());
        entity.setNextFollowUpAt(null);
        entity.setFollowUpCompleted(true);
        entity.setUpdatedAt(LocalDateTime.now());
        userLifeEventMapper.updateById(entity);
        if (lifeChapterService != null) lifeChapterService.onEventDeleted(userId, eventId);
        log.info("重要事件已软删除，userId={}，eventId={}", userId, eventId);
    }

    public Optional<UserLifeEventEntity> getPendingEventForFollowUp(Long userId) {
        return userLifeEventMapper.selectList(new LambdaQueryWrapper<UserLifeEventEntity>()
                .eq(UserLifeEventEntity::getUserId, userId)
                .eq(UserLifeEventEntity::getStatus, "PENDING")
                .isNull(UserLifeEventEntity::getDeletedAt))
                .stream().filter(event -> !Boolean.TRUE.equals(event.getFollowUpCompleted()))
                .filter(this::isFollowUpDue)
                .min(Comparator.comparing(this::followUpAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(event -> event.getImportance() == null ? BigDecimal.ZERO : event.getImportance(), Comparator.reverseOrder())
                        .thenComparing(UserLifeEventEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
    }

    public boolean claimFollowUp(Long userId, Long eventId, LocalDateTime scheduledAt) {
        if (redisTemplate == null) return true;
        String time = scheduledAt == null ? "unknown" : scheduledAt.toString();
        String key = "life-event-follow-up:" + eventId + ":" + time;
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofDays(2)));
        } catch (Exception e) {
            log.warn("事件回访幂等检查失败，暂不发送，eventId={}", eventId, e);
            return false;
        }
    }

    public void releaseFollowUpClaim(Long eventId, LocalDateTime scheduledAt) {
        if (redisTemplate == null) return;
        String time = scheduledAt == null ? "unknown" : scheduledAt.toString();
        try {
            redisTemplate.delete("life-event-follow-up:" + eventId + ":" + time);
        } catch (Exception e) {
            log.debug("释放事件回访幂等锁失败，eventId={}", eventId, e);
        }
    }

    @Transactional
    public boolean recordFollowUpSent(Long userId, Long eventId, LocalDateTime scheduledAt) {
        UserLifeEventEntity entity = findOwnedEvent(userId, eventId);
        if (entity == null || !"PENDING".equalsIgnoreCase(entity.getStatus()) || scheduledAt == null) return false;
        LocalDateTime currentSchedule = entity.getNextFollowUpAt();
        if (currentSchedule == null || !currentSchedule.equals(scheduledAt)) {
            log.info("事件回访计划已变化，忽略旧调度结果，eventId={}，scheduledAt={}，currentSchedule={}",
                    eventId, scheduledAt, currentSchedule);
            return false;
        }
        LocalDateTime now = nowInEventZone();
        int count = (entity.getFollowUpCount() == null ? 0 : entity.getFollowUpCount()) + 1;
        String phase = phaseFor(entity.getTargetDate(), entity.getEndDate(), entity.getStartTime(), entity.getEndTime());
        LocalDateTime nextFollowUpAt;
        boolean completed;
        if (count >= MAX_AUTOMATIC_FOLLOW_UPS) {
            nextFollowUpAt = null;
            completed = true;
            entity.setStatus("FOLLOWED_UP");
        } else if ("PAST".equals(phase)) {
            // 过去事件也保留第二次自动回访的机会；两次通知完成后再自动结束事件。
            nextFollowUpAt = now.plusDays(DEFAULT_FOLLOW_UP_DELAY_DAYS);
            completed = false;
        } else if ("UPCOMING".equals(phase)) {
            nextFollowUpAt = afterEndFollowUpAt(entity);
            completed = false;
        } else if (entity.getEndDate() != null) {
            nextFollowUpAt = atPreferredHour(entity.getEndDate().plusDays(1));
            completed = false;
        } else {
            nextFollowUpAt = now.plusDays(DEFAULT_FOLLOW_UP_DELAY_DAYS);
            completed = false;
        }
        LocalDateTime updatedAt = LocalDateTime.now();
        UpdateWrapper<UserLifeEventEntity> update = new UpdateWrapper<UserLifeEventEntity>()
                .eq("id", eventId)
                .eq("user_id", userId)
                .eq("status", "PENDING")
                .eq("next_follow_up_at", scheduledAt)
                .set("status", entity.getStatus())
                .set("last_follow_up_at", now)
                .set("follow_up_count", count)
                .set("temporal_phase", phase)
                .set("next_follow_up_at", nextFollowUpAt)
                .set("follow_up_completed", completed)
                .set("updated_at", updatedAt);
        int updated = userLifeEventMapper.update(null, update);
        if (updated != 1) {
            log.info("事件回访原子更新未命中，忽略旧调度结果，eventId={}，scheduledAt={}", eventId, scheduledAt);
            return false;
        }
        entity.setLastFollowUpAt(now);
        entity.setFollowUpCount(count);
        entity.setTemporalPhase(phase);
        entity.setNextFollowUpAt(nextFollowUpAt);
        entity.setFollowUpCompleted(completed);
        entity.setUpdatedAt(updatedAt);
        log.info("事件回访已记录，eventId={}，scheduledAt={}，followUpCount={}，nextFollowUpAt={}，completed={}",
                eventId, scheduledAt, entity.getFollowUpCount(), entity.getNextFollowUpAt(), entity.getFollowUpCompleted());
        return true;
    }

    public boolean markEventFollowedUp(Long userId, Long eventId) {
        UserLifeEventEntity entity = findOwnedEvent(userId, eventId);
        if (entity != null && "PENDING".equalsIgnoreCase(entity.getStatus())) {
            entity.setStatus("FOLLOWED_UP");
            entity.setFollowUpCompleted(true);
            entity.setNextFollowUpAt(null);
            entity.setUpdatedAt(LocalDateTime.now());
            userLifeEventMapper.updateById(entity);
        }
        return entity != null;
    }

    private void notifyTimelineChanged(Long userId, Long eventId) {
        if (lifeChapterService != null) lifeChapterService.onEventChanged(userId, eventId);
    }

    public String buildEventContextForChat(Long userId, Long eventId) {
        UserLifeEventEntity entity = findOwnedEvent(userId, eventId);
        if (entity == null) return "";
        List<Long> diaryIds = parseDiaryIds(entity.getDiaryIdsJson());
        StringBuilder context = new StringBuilder("[重点跟进事件背景，仅供理解，不是用户当前消息]\n");
        context.append("- 事件名称：").append(entity.getTitle()).append("\n");
        context.append("- 事件时间：").append(formatSchedule(entity)).append("\n");
        if (entity.getDescription() != null && !entity.getDescription().isBlank())
            context.append("- 背景描述：").append(entity.getDescription()).append("\n");
        if (diaryIds.isEmpty()) return context.toString();
        List<DiaryEntity> diaries = diaryMapper.selectList(new LambdaQueryWrapper<DiaryEntity>()
                .in(DiaryEntity::getId, diaryIds).eq(DiaryEntity::getAuthorUserId, userId)
                .eq(DiaryEntity::getIsDeleted, false));
        Map<Long, DiaryAnalysisEntity> analyses = Collections.emptyMap();
        if (diaryAnalysisMapper != null && !diaries.isEmpty()) {
            analyses = diaryAnalysisMapper.selectList(new LambdaQueryWrapper<DiaryAnalysisEntity>()
                    .in(DiaryAnalysisEntity::getDiaryId, diaries.stream().map(DiaryEntity::getId).toList()))
                    .stream().collect(java.util.stream.Collectors.toMap(DiaryAnalysisEntity::getDiaryId, a -> a, (a, b) -> a));
        }
        context.append("- 相关日记（仅用于本次事件聊天）：\n");
        for (DiaryEntity diary : diaries) {
            DiaryAnalysisEntity analysis = analyses.get(diary.getId());
            String summary = analysis == null ? "" : clean(analysis.getSummary(), 240);
            String excerpt = plain(diary.getContent());
            if (excerpt.length() > 120) excerpt = excerpt.substring(0, 120) + "...";
            context.append("  * 日期：").append(diary.getCreatedAt() == null ? "" : diary.getCreatedAt().toLocalDate()).append("\n");
            if (!summary.isBlank()) context.append("    AI摘要：").append(summary).append("\n");
            else if (!excerpt.isBlank()) context.append("    原文片段：").append(excerpt).append("\n");
        }
        return context.toString();
    }

    private List<UserLifeEventEntity> listMergeableEvents(Long userId) {
        return userLifeEventMapper.selectList(new LambdaQueryWrapper<UserLifeEventEntity>()
                .eq(UserLifeEventEntity::getUserId, userId)
                .isNull(UserLifeEventEntity::getDeletedAt)
                .ne(UserLifeEventEntity::getStatus, "CANCELLED"));
    }

    private List<UserLifeEventEntity> listDeletedEvents(Long userId) {
        return userLifeEventMapper.selectList(new LambdaQueryWrapper<UserLifeEventEntity>()
                .eq(UserLifeEventEntity::getUserId, userId)
                .isNotNull(UserLifeEventEntity::getDeletedAt));
    }

    private void deduplicateExistingEvents(List<UserLifeEventEntity> events) {
        events.sort(Comparator.comparing(UserLifeEventEntity::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
        for (int i = 0; i < events.size(); i++) {
            UserLifeEventEntity keeper = events.get(i);
            if (keeper.getId() == null || keeper.getTargetDate() == null) continue;
            for (int j = events.size() - 1; j > i; j--) {
                UserLifeEventEntity duplicate = events.get(j);
                if (duplicate.getId() == null || duplicate.getTargetDate() == null
                        || !isSameEvent(keeper, duplicate.getTitle(), duplicate.getTargetDate(),
                        duplicate.getEndDate() == null ? duplicate.getTargetDate() : duplicate.getEndDate(), "")) continue;
                if (keeper.getCreatedAt() != null && duplicate.getCreatedAt() != null
                        && duplicate.getCreatedAt().isBefore(keeper.getCreatedAt())) continue;
                mergeEvent(keeper, scheduleOf(duplicate), duplicate.getDescription(), null,
                        duplicate.getTemporalPhase(), null, duplicate.getImportance());
                if ("PENDING".equalsIgnoreCase(duplicate.getStatus())
                        && !"FOLLOWED_UP".equalsIgnoreCase(keeper.getStatus())) keeper.setStatus("PENDING");
                if (!normalize(keeper.getTitle()).equals(normalize(duplicate.getTitle()))) {
                    LinkedHashSet<String> aliases = new LinkedHashSet<>(parseStrings(keeper.getTitleAliasesJson()));
                    aliases.add(duplicate.getTitle());
                    keeper.setTitleAliasesJson(writeStrings(aliases));
                }
                userLifeEventMapper.updateById(keeper);
                userLifeEventMapper.deleteById(duplicate.getId());
                events.remove(j);
            }
        }
    }

    private boolean isSameEvent(UserLifeEventEntity candidate, String title, LocalDate start, LocalDate end,
                                String diaryContent) {
        String normalized = normalize(title);
        boolean titleMatch = titleMatches(normalize(candidate.getTitle()), normalized)
                || parseStrings(candidate.getTitleAliasesJson()).stream().map(this::normalize)
                .anyMatch(alias -> titleMatches(alias, normalized));
        if (!titleMatch && diaryContent != null && !diaryContent.isBlank()) {
            String content = normalize(plain(diaryContent));
            titleMatch = content.contains(normalize(candidate.getTitle()))
                    || content.contains(normalized);
        }
        if (!titleMatch) return false;
        LocalDate candidateStart = candidate.getTargetDate();
        LocalDate candidateEnd = candidate.getEndDate() == null ? candidateStart : candidate.getEndDate();
        if (candidateStart == null) return false;
        long distance = candidateEnd.isBefore(start) ? ChronoUnit.DAYS.between(candidateEnd, start)
                : end.isBefore(candidateStart) ? ChronoUnit.DAYS.between(end, candidateStart) : 0;
        return distance <= 14;
    }

    private boolean titleMatches(String existing, String incoming) {
        return !existing.isBlank() && !incoming.isBlank()
                && (existing.equals(incoming) || existing.contains(incoming) || incoming.contains(existing));
    }

    private void mergeEvent(UserLifeEventEntity entity, ParsedSchedule incoming, String description, Long diaryId,
                            String incomingPhase, FollowUpSuggestion followUp, BigDecimal importance) {
        LocalDate oldStart = entity.getTargetDate();
        LocalDate oldEnd = entity.getEndDate() == null ? oldStart : entity.getEndDate();
        LocalDate newStart = oldStart.isBefore(incoming.startDate()) ? oldStart : incoming.startDate();
        LocalDate newEnd = oldEnd.isAfter(incoming.endDate()) ? oldEnd : incoming.endDate();
        entity.setTargetDate(newStart);
        entity.setEndDate(newEnd.equals(newStart) ? null : newEnd);
        if (incoming.startTime() != null && (entity.getStartTime() == null || incoming.startTime().isBefore(entity.getStartTime()))) entity.setStartTime(incoming.startTime());
        if (incoming.endTime() != null && (entity.getEndTime() == null || incoming.endTime().isAfter(entity.getEndTime()))) entity.setEndTime(incoming.endTime());
        if ((entity.getDescription() == null || entity.getDescription().isBlank()) && description != null) entity.setDescription(clean(description, 1000));
        applyTemporalAndFollowUp(entity, incomingPhase, followUp, importance, incoming, true);
        List<Long> ids = parseDiaryIds(entity.getDiaryIdsJson());
        if (diaryId != null && !ids.contains(diaryId)) ids.add(diaryId);
        entity.setDiaryIdsJson(writeIds(ids));
        if (diaryId != null) entity.setLastDiaryId(diaryId);
        entity.setUpdatedAt(LocalDateTime.now());
    }

    private ParsedSchedule scheduleOf(UserLifeEventEntity entity) {
        return new ParsedSchedule(entity.getTargetDate(), entity.getEndDate() == null ? entity.getTargetDate() : entity.getEndDate(), entity.getStartTime(), entity.getEndTime());
    }

    private ParsedSchedule parseRequest(LifeEventUpsertRequest request) {
        if (request == null) throw badRequest("事件内容不能为空");
        String title = clean(request.title(), 128);
        if (title.isBlank()) throw badRequest("事件名称不能为空");
        try { return parseSchedule(request.targetDate(), request.endDate(), request.startTime(), request.endTime()); }
        catch (IllegalArgumentException e) { throw badRequest(e.getMessage()); }
    }

    private ParsedSchedule parseSchedule(String startDateRaw, String endDateRaw, String startTimeRaw, String endTimeRaw) {
        if (startDateRaw == null || startDateRaw.isBlank()) throw new IllegalArgumentException("开始日期不能为空");
        LocalDate start;
        LocalDate end = null;
        LocalTime startTime = parseTime(startTimeRaw, "开始时间");
        LocalTime endTime = parseTime(endTimeRaw, "结束时间");
        try {
            start = LocalDate.parse(startDateRaw.trim(), DATE_FORMAT);
            if (endDateRaw != null && !endDateRaw.isBlank()) end = LocalDate.parse(endDateRaw.trim(), DATE_FORMAT);
        } catch (Exception e) { throw new IllegalArgumentException("日期格式必须是 YYYY-MM-DD"); }
        if (end == null) end = start;
        if (end.isBefore(start)) throw new IllegalArgumentException("结束日期不能早于开始日期");
        if (endTime != null && startTime == null) throw new IllegalArgumentException("结束时间不能单独填写");
        if (start.equals(end) && startTime != null && endTime != null && endTime.isBefore(startTime)) throw new IllegalArgumentException("同一天的结束时间不能早于开始时间");
        return new ParsedSchedule(start, end, startTime, endTime);
    }

    private LocalTime parseTime(String raw, String label) {
        if (raw == null || raw.isBlank()) return null;
        try { return LocalTime.parse(raw.trim(), TIME_FORMAT); }
        catch (Exception e) { throw new IllegalArgumentException(label + "格式必须是 HH:mm"); }
    }

    private void applySchedule(UserLifeEventEntity entity, ParsedSchedule schedule) {
        entity.setTargetDate(schedule.startDate());
        entity.setEndDate(schedule.endDate().equals(schedule.startDate()) ? null : schedule.endDate());
        entity.setStartTime(schedule.startTime());
        entity.setEndTime(schedule.endTime());
    }

    private List<Long> validateDiaryIds(Long userId, List<Long> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty()) return new ArrayList<>();
        List<Long> ids = requestedIds.stream().filter(Objects::nonNull).distinct().toList();
        List<DiaryEntity> owned = diaryMapper.selectList(new LambdaQueryWrapper<DiaryEntity>()
                .in(DiaryEntity::getId, ids).eq(DiaryEntity::getAuthorUserId, userId).eq(DiaryEntity::getIsDeleted, false));
        if (owned.size() != ids.size()) throw new ResponseStatusException(NOT_FOUND, "只能关联当前用户未删除的日记");
        return new ArrayList<>(ids);
    }

    private UserLifeEventEntity requireOwned(Long userId, Long eventId) {
        UserLifeEventEntity entity = findOwnedEvent(userId, eventId);
        if (entity == null) throw new ResponseStatusException(NOT_FOUND, "事件不存在");
        return entity;
    }
    private UserLifeEventEntity findOwnedEvent(Long userId, Long eventId) {
        if (userId == null || eventId == null) return null;
        return userLifeEventMapper.selectOne(new LambdaQueryWrapper<UserLifeEventEntity>()
                .eq(UserLifeEventEntity::getId, eventId)
                .eq(UserLifeEventEntity::getUserId, userId)
                .isNull(UserLifeEventEntity::getDeletedAt));
    }

    private UserLifeEventEntity findOwnedEventIncludingDeleted(Long userId, Long eventId) {
        if (userId == null || eventId == null) return null;
        return userLifeEventMapper.selectOne(new LambdaQueryWrapper<UserLifeEventEntity>()
                .eq(UserLifeEventEntity::getId, eventId)
                .eq(UserLifeEventEntity::getUserId, userId));
    }
    private LifeEventView toView(UserLifeEventEntity entity) {
        List<Long> ids = parseDiaryIds(entity.getDiaryIdsJson());
        String phase = validPhaseOrDerived(entity);
        int followUpCount = entity.getFollowUpCount() == null ? 0 : entity.getFollowUpCount();
        return new LifeEventView(entity.getId(), entity.getTitle(), entity.getDescription(), date(entity.getTargetDate()), date(entity.getEndDate()), time(entity.getStartTime()), time(entity.getEndTime()), visibleStatus(entity.getStatus()), ids, ids.size(), entity.getLastDiaryId(), entity.getFollowUpNote(), value(entity.getCreatedAt()), value(entity.getUpdatedAt()), phase, minuteValue(entity.getNextFollowUpAt()), minuteValue(entity.getLastFollowUpAt()), followUpCount, clean(entity.getFollowUpReason(), 512), Boolean.TRUE.equals(entity.getFollowUpCompleted()), entity.getImportance());
    }

    private void applyTemporalAndFollowUp(UserLifeEventEntity entity, String aiPhase,
                                          FollowUpSuggestion followUp, BigDecimal importance,
                                          ParsedSchedule schedule, boolean merged) {
        String phase = phaseFor(entity.getTargetDate(), entity.getEndDate(), entity.getStartTime(), entity.getEndTime());
        entity.setTemporalPhase(phase);
        if (importance != null) entity.setImportance(importance);
        if (followUp != null && followUp.reason() != null && !followUp.reason().isBlank()) {
            entity.setFollowUpReason(clean(followUp.reason(), 512));
        }
        if ("FOLLOWED_UP".equalsIgnoreCase(entity.getStatus())) {
            entity.setFollowUpCompleted(true);
            entity.setNextFollowUpAt(null);
            return;
        }
        if (merged && Boolean.TRUE.equals(entity.getFollowUpCompleted())) {
            return;
        }
        entity.setFollowUpCompleted(false);
        LocalDateTime now = nowInEventZone();
        if (!merged || entity.getNextFollowUpAt() == null
                || ("PAST".equals(phase) && entity.getNextFollowUpAt().isAfter(now))) {
            int delay = followUp == null || followUp.delayDays() == null
                    ? DEFAULT_FOLLOW_UP_DELAY_DAYS : followUp.delayDays();
            String timing = followUp == null ? null : normalizeTiming(followUp.timing());
            entity.setNextFollowUpAt(initialFollowUpAt(entity, phase, timing, delay, now));
        }
    }

    private LocalDateTime initialFollowUpAt(UserLifeEventEntity entity, String phase, String timing,
                                            int delay, LocalDateTime now) {
        if ("UPCOMING".equals(phase)) {
            if (!"AFTER_END".equals(timing) && !"AFTER_EVENT".equals(timing)) {
                LocalDateTime beforeStart = entity.getTargetDate().atStartOfDay().minusDays(delay).withHour(10);
                return beforeStart.isAfter(now) ? beforeStart : now;
            }
            return afterEndFollowUpAt(entity, delay, now);
        }
        if ("PAST".equals(phase)) {
            LocalDate end = entity.getEndDate() == null ? entity.getTargetDate() : entity.getEndDate();
            LocalDateTime afterEnd = atPreferredHour(end.plusDays(delay));
            return afterEnd.isAfter(now) ? afterEnd : now;
        }
        if (("AFTER_END".equals(timing) || "AFTER_EVENT".equals(timing)) && entity.getEndDate() != null) {
            return afterEndFollowUpAt(entity, delay, now);
        }
        int inProgressDelay = Math.min(3, Math.max(1, delay));
        return atPreferredHour(now.toLocalDate().plusDays(inProgressDelay));
    }

    private LocalDateTime afterEndFollowUpAt(UserLifeEventEntity entity) {
        return afterEndFollowUpAt(entity, DEFAULT_FOLLOW_UP_DELAY_DAYS, nowInEventZone());
    }

    private LocalDateTime afterEndFollowUpAt(UserLifeEventEntity entity, int delay, LocalDateTime now) {
        LocalDate end = entity.getEndDate() == null ? entity.getTargetDate() : entity.getEndDate();
        LocalDateTime next = atPreferredHour(end.plusDays(Math.min(3, Math.max(1, delay))));
        return next.isAfter(now) ? next : now;
    }

    private LocalDateTime atPreferredHour(LocalDate date) {
        return date.atTime(10, 0);
    }

    private String validPhaseOrDerived(UserLifeEventEntity entity) {
        return phaseFor(entity.getTargetDate(), entity.getEndDate(), entity.getStartTime(), entity.getEndTime());
    }

    private String validateTemporalPhase(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim().toUpperCase(Locale.ROOT);
        return Set.of("UPCOMING", "ONGOING", "PAST").contains(value) ? value : null;
    }

    private BigDecimal validateImportance(BigDecimal value) {
        if (value == null) return null;
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("重要性必须在 0 到 1 之间");
        }
        return value;
    }

    private FollowUpSuggestion validateFollowUp(FollowUpSuggestion suggestion) {
        if (suggestion == null) return null;
        int delay = suggestion.delayDays() == null ? DEFAULT_FOLLOW_UP_DELAY_DAYS : suggestion.delayDays();
        if (delay < 1 || delay > MAX_FOLLOW_UP_DELAY_DAYS) {
            throw new IllegalArgumentException("回访间隔必须在 1 到 30 天之间");
        }
        String timing = normalizeTiming(suggestion.timing());
        if (suggestion.timing() != null && !suggestion.timing().isBlank() && timing == null) {
            throw new IllegalArgumentException("回访时机不合法");
        }
        return new FollowUpSuggestion(timing, delay, clean(suggestion.reason(), 512));
    }

    private String normalizeTiming(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim().toUpperCase(Locale.ROOT);
        return Set.of("BEFORE_START", "AFTER_END", "IN_PROGRESS", "AFTER_EVENT").contains(value) ? value : null;
    }

    private String phaseFor(LocalDate start, LocalDate end, LocalTime startTime, LocalTime endTime) {
        if (start == null) return "PAST";
        LocalDateTime now = nowInEventZone();
        LocalDate today = now.toLocalDate();
        LocalDate actualEnd = end == null ? start : end;
        LocalDateTime startAt = startTime == null ? start.atStartOfDay() : start.atTime(startTime);
        if (now.isBefore(startAt)) return "UPCOMING";
        if (end != null || endTime != null) {
            LocalDateTime endAt = endTime != null ? actualEnd.atTime(endTime)
                    : actualEnd.atTime(LocalTime.MAX);
            if (now.isAfter(endAt)) return "PAST";
            return "ONGOING";
        }
        if (today.isAfter(actualEnd)) return "PAST";
        return "ONGOING";
    }

    private LocalDateTime nowInEventZone() {
        return LocalDateTime.now(eventTimeZone);
    }
    private boolean isFollowUpDue(UserLifeEventEntity entity) {
        if (Boolean.TRUE.equals(entity.getFollowUpCompleted())) return false;
        LocalDateTime next = entity.getNextFollowUpAt();
        return next != null && !next.isAfter(nowInEventZone());
    }

    private LocalDateTime followUpAt(UserLifeEventEntity entity) {
        return entity.getNextFollowUpAt();
    }
    private LocalDateTime dueAt(UserLifeEventEntity entity) {
        LocalDate date = entity.getEndDate() == null ? entity.getTargetDate() : entity.getEndDate();
        if (entity.getEndTime() != null) return date.atTime(entity.getEndTime());
        if (entity.getEndDate() != null) return date.atTime(LocalTime.MAX);
        if (entity.getStartTime() != null) return date.atTime(entity.getStartTime());
        return date.atStartOfDay();
    }

    private static ZoneId parseTimeZone(String timeZoneId) {
        if (timeZoneId == null || timeZoneId.isBlank()) return DEFAULT_EVENT_TIME_ZONE;
        return ZoneId.of(timeZoneId.trim());
    }
    private String formatSchedule(UserLifeEventEntity entity) {
        String result = date(entity.getTargetDate());
        if (entity.getEndDate() != null) result += " - " + date(entity.getEndDate());
        if (entity.getStartTime() != null) {
            result += " " + time(entity.getStartTime());
            if (entity.getEndTime() != null) result += " - " + time(entity.getEndTime());
        }
        return result;
    }
    private String visibleStatus(String status) { return "ARCHIVED".equalsIgnoreCase(status) ? "FOLLOWED_UP" : status; }
    private String date(LocalDate value) { return value == null ? "" : value.toString(); }
    private String time(LocalTime value) { return value == null ? "" : value.format(TIME_FORMAT); }
    private String value(LocalDateTime value) { return value == null ? "" : value.toString(); }
    private String minuteValue(LocalDateTime value) { return value == null ? "" : value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")); }
    private String clean(String value, int max) { if (value == null) return ""; String s = value.trim(); return s.length() > max ? s.substring(0, max) : s; }
    private String plain(String value) { return value == null ? "" : value.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim(); }
    private String normalize(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}，。！？、；：‘’“”《》（）【】]+", ""); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(BAD_REQUEST, message); }
    private List<Long> parseDiaryIds(String json) { try { return json == null || json.isBlank() ? new ArrayList<>() : new ArrayList<>(objectMapper.readValue(json, new TypeReference<List<Long>>() {})); } catch (Exception e) { return new ArrayList<>(); } }
    private List<String> parseStrings(String json) { try { return json == null || json.isBlank() ? List.of() : objectMapper.readValue(json, new TypeReference<List<String>>() {}); } catch (Exception e) { return List.of(); } }
    private String writeIds(List<Long> ids) { try { return objectMapper.writeValueAsString(ids == null ? List.of() : ids); } catch (Exception e) { return "[]"; } }
    private String writeStrings(Set<String> values) { try { return objectMapper.writeValueAsString(values == null ? List.of() : values); } catch (Exception e) { return "[]"; } }

    private <T> T withUserLock(Long userId, Supplier<T> action) {
        if (redisTemplate == null) return action.get();
        String key = LOCK_PREFIX + userId;
        String token = UUID.randomUUID().toString();
        boolean acquired = false;
        try {
            for (int i = 0; i < 20 && !acquired; i++) {
                acquired = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, token, LOCK_TTL));
                if (!acquired) Thread.sleep(100L);
            }
            if (!acquired) throw new IllegalStateException("事件归并锁繁忙，稍后重试");
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("事件归并锁被中断", e);
        } finally {
            if (acquired) redisTemplate.execute(RELEASE_LOCK, List.of(key), token);
        }
    }
    private record ParsedSchedule(LocalDate startDate, LocalDate endDate, LocalTime startTime, LocalTime endTime) {}
}
