package com.moodcopilot.event;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.ai.JsonUtils;
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

    private final UserLifeEventMapper userLifeEventMapper;
    private final DiaryMapper diaryMapper;
    private final DiaryAnalysisMapper diaryAnalysisMapper;
    private final ChatClient analysisChatClient;
    private final ObjectMapper objectMapper;
    private final AiPromptProperties aiPrompts;
    private final StringRedisTemplate redisTemplate;
    private final ZoneId eventTimeZone;

    @Autowired
    public LifeEventService(UserLifeEventMapper userLifeEventMapper, DiaryMapper diaryMapper,
                            DiaryAnalysisMapper diaryAnalysisMapper,
                            @Qualifier("analysisChatClient") ChatClient analysisChatClient,
                            ObjectMapper objectMapper, AiPromptProperties aiPrompts,
                            StringRedisTemplate redisTemplate,
                            @org.springframework.beans.factory.annotation.Value("${moodcopilot.time-zone:Asia/Shanghai}") String timeZoneId) {
        this(userLifeEventMapper, diaryMapper, diaryAnalysisMapper, analysisChatClient, objectMapper,
                aiPrompts, redisTemplate, parseTimeZone(timeZoneId));
    }

    private LifeEventService(UserLifeEventMapper userLifeEventMapper, DiaryMapper diaryMapper,
                             DiaryAnalysisMapper diaryAnalysisMapper, ChatClient analysisChatClient,
                             ObjectMapper objectMapper, AiPromptProperties aiPrompts,
                             StringRedisTemplate redisTemplate, ZoneId eventTimeZone) {
        this.userLifeEventMapper = userLifeEventMapper;
        this.diaryMapper = diaryMapper;
        this.diaryAnalysisMapper = diaryAnalysisMapper;
        this.analysisChatClient = analysisChatClient;
        this.objectMapper = objectMapper;
        this.aiPrompts = aiPrompts;
        this.redisTemplate = redisTemplate;
        this.eventTimeZone = eventTimeZone;
    }

    /** 兼容旧测试；生产 Bean 使用包含摘要 Mapper 和 Redis 的构造器。 */
    public LifeEventService(UserLifeEventMapper userLifeEventMapper, DiaryMapper diaryMapper,
                            ChatClient analysisChatClient, ObjectMapper objectMapper,
                            AiPromptProperties aiPrompts) {
        this(userLifeEventMapper, diaryMapper, null, analysisChatClient, objectMapper, aiPrompts,
                null, DEFAULT_EVENT_TIME_ZONE);
    }

    /** 兼容直接构造 Service 的测试；生产环境通过配置注入事件时区。 */
    public LifeEventService(UserLifeEventMapper userLifeEventMapper, DiaryMapper diaryMapper,
                            DiaryAnalysisMapper diaryAnalysisMapper, ChatClient analysisChatClient,
                            ObjectMapper objectMapper, AiPromptProperties aiPrompts,
                            StringRedisTemplate redisTemplate) {
        this(userLifeEventMapper, diaryMapper, diaryAnalysisMapper, analysisChatClient, objectMapper,
                aiPrompts, redisTemplate, DEFAULT_EVENT_TIME_ZONE);
    }

    public record ExtractedLifeEvent(String title, String description, String targetDate,
                                     String endDate, String startTime, String endTime) {}
    public record LifeEventUpsertRequest(String title, String description, String targetDate,
                                         String endDate, String startTime, String endTime,
                                         List<Long> diaryIds) {}
    public record LifeEventView(Long id, String title, String description, String targetDate,
                                String endDate, String startTime, String endTime, String status,
                                List<Long> diaryIds, int diaryCount, Long lastDiaryId,
                                String followUpNote, String createdAt, String updatedAt) {}
    public record LifeDiaryOption(Long id, String date, String excerpt, String summary) {}
    public record LifeDiaryPage(List<LifeDiaryOption> items, long total, int page, int size, boolean hasMore) {}

    @Transactional
    public void extractAndTrackLifeEvents(Long userId, Long diaryId, String content, LocalDateTime diaryCreatedAt) {
        if (content == null || content.isBlank() || content.length() < 10) return;
        try {
            LocalDate baseDate = diaryCreatedAt != null ? diaryCreatedAt.toLocalDate() : LocalDate.now();
            String prompt = "[日记记录日期]" + baseDate.format(DATE_FORMAT) + "\n\n[日记内容]\n" + content;
            String response = analysisChatClient.prompt().system(aiPrompts.getLifeEventExtractionSystemPrompt())
                    .user(prompt).call().content();
            List<ExtractedLifeEvent> events = objectMapper.readValue(JsonUtils.cleanJson(response),
                    new TypeReference<List<ExtractedLifeEvent>>() {});
            if (events == null || events.isEmpty()) return;
            withUserLock(userId, () -> {
                List<UserLifeEventEntity> existing = listMergeableEvents(userId);
                deduplicateExistingEvents(existing);
                for (ExtractedLifeEvent extracted : events) {
                    ParsedSchedule schedule;
                    try {
                        schedule = parseSchedule(extracted.targetDate(), extracted.endDate(), extracted.startTime(), extracted.endTime());
                    } catch (IllegalArgumentException ex) {
                        log.info("跳过非法 AI 事件 userId={}, diaryId={}, reason={}", userId, diaryId, ex.getMessage());
                        continue;
                    }
                    String title = clean(extracted.title(), 128);
                    if (title.isBlank()) continue;
                    UserLifeEventEntity matched = existing.stream()
                            .filter(candidate -> isSameEvent(candidate, title, schedule.startDate(), schedule.endDate()))
                            .findFirst().orElse(null);
                    if (matched == null) {
                        UserLifeEventEntity created = new UserLifeEventEntity();
                        created.setUserId(userId);
                        created.setTitle(title);
                        created.setDescription(clean(extracted.description(), 1000));
                        applySchedule(created, schedule);
                        created.setStatus("PENDING");
                        created.setDiaryIdsJson(writeIds(List.of(diaryId)));
                        created.setLastDiaryId(diaryId);
                        created.setCreatedAt(LocalDateTime.now());
                        created.setUpdatedAt(LocalDateTime.now());
                        userLifeEventMapper.insert(created);
                        existing.add(created);
                    } else {
                        mergeEvent(matched, schedule, extracted.description(), diaryId);
                        userLifeEventMapper.updateById(matched);
                    }
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("提取重要事件失败 userId={}, diaryId={}: {}", userId, diaryId, e.getMessage());
            throw new IllegalStateException("重要事件提取失败", e);
        }
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
        entity.setDiaryIdsJson(writeIds(diaryIds));
        entity.setLastDiaryId(diaryIds.isEmpty() ? null : diaryIds.get(diaryIds.size() - 1));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        userLifeEventMapper.insert(entity);
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
        if (request.diaryIds() != null) {
            List<Long> diaryIds = validateDiaryIds(userId, request.diaryIds());
            entity.setDiaryIdsJson(writeIds(diaryIds));
            entity.setLastDiaryId(diaryIds.isEmpty() ? null : diaryIds.get(diaryIds.size() - 1));
        }
        entity.setUpdatedAt(LocalDateTime.now());
        userLifeEventMapper.updateById(entity);
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
        return toView(entity);
    }

    public LifeEventView getEvent(Long userId, Long eventId) { return toView(requireOwned(userId, eventId)); }

    public List<LifeEventView> listUserEvents(Long userId) {
        return userLifeEventMapper.selectList(new LambdaQueryWrapper<UserLifeEventEntity>()
                .eq(UserLifeEventEntity::getUserId, userId)
                .orderByDesc(UserLifeEventEntity::getTargetDate)
                .orderByDesc(UserLifeEventEntity::getUpdatedAt)).stream().map(this::toView).toList();
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

    public LifeEventView updateEventStatus(Long userId, Long eventId, String status, String note) {
        UserLifeEventEntity entity = requireOwned(userId, eventId);
        if (status != null && !status.isBlank()) {
            String normalized = status.toUpperCase(Locale.ROOT).trim();
            if (!Set.of("PENDING", "FOLLOWED_UP").contains(normalized))
                throw new ResponseStatusException(BAD_REQUEST, "事件状态只能是 PENDING 或 FOLLOWED_UP");
            entity.setStatus(normalized);
        }
        if (note != null) entity.setFollowUpNote(clean(note, 2000));
        entity.setUpdatedAt(LocalDateTime.now());
        userLifeEventMapper.updateById(entity);
        return toView(entity);
    }

    public Optional<UserLifeEventEntity> getPendingEventForFollowUp(Long userId) {
        return userLifeEventMapper.selectList(new LambdaQueryWrapper<UserLifeEventEntity>()
                .eq(UserLifeEventEntity::getUserId, userId).eq(UserLifeEventEntity::getStatus, "PENDING"))
                .stream().filter(this::isDue).max(Comparator.comparing(this::dueAt));
    }

    public boolean markEventFollowedUp(Long userId, Long eventId) {
        UserLifeEventEntity entity = findOwnedEvent(userId, eventId);
        if (entity != null && "PENDING".equals(entity.getStatus())) {
            entity.setStatus("FOLLOWED_UP");
            entity.setUpdatedAt(LocalDateTime.now());
            userLifeEventMapper.updateById(entity);
        }
        return entity != null;
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
                .eq(UserLifeEventEntity::getUserId, userId).ne(UserLifeEventEntity::getStatus, "CANCELLED"));
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
                        duplicate.getEndDate() == null ? duplicate.getTargetDate() : duplicate.getEndDate())) continue;
                if (keeper.getCreatedAt() != null && duplicate.getCreatedAt() != null
                        && duplicate.getCreatedAt().isBefore(keeper.getCreatedAt())) continue;
                mergeEvent(keeper, scheduleOf(duplicate), duplicate.getDescription(), null);
                if ("PENDING".equalsIgnoreCase(duplicate.getStatus())) keeper.setStatus("PENDING");
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

    private boolean isSameEvent(UserLifeEventEntity candidate, String title, LocalDate start, LocalDate end) {
        String normalized = normalize(title);
        boolean titleMatch = titleMatches(normalize(candidate.getTitle()), normalized)
                || parseStrings(candidate.getTitleAliasesJson()).stream().map(this::normalize)
                .anyMatch(alias -> titleMatches(alias, normalized));
        if (!titleMatch) return false;
        LocalDate candidateStart = candidate.getTargetDate();
        LocalDate candidateEnd = candidate.getEndDate() == null ? candidateStart : candidate.getEndDate();
        if (candidateStart == null) return false;
        long distance = candidateEnd.isBefore(start) ? ChronoUnit.DAYS.between(candidateEnd, start)
                : end.isBefore(candidateStart) ? ChronoUnit.DAYS.between(end, candidateStart) : 0;
        return distance <= 7;
    }

    private boolean titleMatches(String existing, String incoming) {
        return !existing.isBlank() && !incoming.isBlank()
                && (existing.equals(incoming) || existing.contains(incoming) || incoming.contains(existing));
    }

    private void mergeEvent(UserLifeEventEntity entity, ParsedSchedule incoming, String description, Long diaryId) {
        LocalDate oldStart = entity.getTargetDate();
        LocalDate oldEnd = entity.getEndDate() == null ? oldStart : entity.getEndDate();
        LocalDate newStart = oldStart.isBefore(incoming.startDate()) ? oldStart : incoming.startDate();
        LocalDate newEnd = oldEnd.isAfter(incoming.endDate()) ? oldEnd : incoming.endDate();
        entity.setTargetDate(newStart);
        entity.setEndDate(newEnd.equals(newStart) ? null : newEnd);
        if (incoming.startTime() != null && (entity.getStartTime() == null || incoming.startTime().isBefore(entity.getStartTime()))) entity.setStartTime(incoming.startTime());
        if (incoming.endTime() != null && (entity.getEndTime() == null || incoming.endTime().isAfter(entity.getEndTime()))) entity.setEndTime(incoming.endTime());
        if ((entity.getDescription() == null || entity.getDescription().isBlank()) && description != null) entity.setDescription(clean(description, 1000));
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
        return userLifeEventMapper.selectOne(new LambdaQueryWrapper<UserLifeEventEntity>().eq(UserLifeEventEntity::getId, eventId).eq(UserLifeEventEntity::getUserId, userId));
    }
    private LifeEventView toView(UserLifeEventEntity entity) {
        List<Long> ids = parseDiaryIds(entity.getDiaryIdsJson());
        return new LifeEventView(entity.getId(), entity.getTitle(), entity.getDescription(), date(entity.getTargetDate()), date(entity.getEndDate()), time(entity.getStartTime()), time(entity.getEndTime()), visibleStatus(entity.getStatus()), ids, ids.size(), entity.getLastDiaryId(), entity.getFollowUpNote(), value(entity.getCreatedAt()), value(entity.getUpdatedAt()));
    }
    private boolean isDue(UserLifeEventEntity entity) {
        return entity.getTargetDate() != null && !dueAt(entity).isAfter(LocalDateTime.now(eventTimeZone));
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
