package com.moodcopilot.event;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.ai.JsonUtils;
import com.moodcopilot.config.AiPromptProperties;
import com.moodcopilot.entity.DiaryEntity;
import com.moodcopilot.entity.UserLifeEventEntity;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.mapper.UserLifeEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import org.springframework.web.server.ResponseStatusException;
@Service
public class LifeEventService {

    private static final Logger log = LoggerFactory.getLogger(LifeEventService.class);

    private final UserLifeEventMapper userLifeEventMapper;
    private final DiaryMapper diaryMapper;
    private final ChatClient analysisChatClient;
    private final ObjectMapper objectMapper;
    private final AiPromptProperties aiPrompts;

    public LifeEventService(UserLifeEventMapper userLifeEventMapper,
                            DiaryMapper diaryMapper,
                            @Qualifier("analysisChatClient") ChatClient analysisChatClient,
                            ObjectMapper objectMapper,
                            AiPromptProperties aiPrompts) {
        this.userLifeEventMapper = userLifeEventMapper;
        this.diaryMapper = diaryMapper;
        this.analysisChatClient = analysisChatClient;
        this.objectMapper = objectMapper;
        this.aiPrompts = aiPrompts;
    }

    public record ExtractedLifeEvent(String title, String description, String targetDate) {}

    public record LifeEventView(Long id, String title, String description, String targetDate,
            String status, List<Long> diaryIds, Long lastDiaryId, String followUpNote,
            String createdAt, String updatedAt) {}

    public void extractAndTrackLifeEvents(Long userId, Long diaryId, String content, LocalDateTime diaryCreatedAt) {
        if (content == null || content.isBlank() || content.length() < 10) return;
        try {
            LocalDate baseDate = diaryCreatedAt != null ? diaryCreatedAt.toLocalDate() : LocalDate.now();
            String userPrompt = "[日记记录日期]" + baseDate.format(DateTimeFormatter.ISO_LOCAL_DATE) + "\n\n[日记内容]\n" + content;
            String response = analysisChatClient.prompt()
                    .system(aiPrompts.getLifeEventExtractionSystemPrompt())
                    .user(userPrompt).call().content();
            String cleaned = JsonUtils.cleanJson(response);
            List<ExtractedLifeEvent> events = objectMapper.readValue(cleaned, new TypeReference<List<ExtractedLifeEvent>>() {});
            if (events == null || events.isEmpty()) return;
            List<UserLifeEventEntity> existingPending = userLifeEventMapper.selectList(
                    new LambdaQueryWrapper<UserLifeEventEntity>()
                            .eq(UserLifeEventEntity::getUserId, userId)
                            .eq(UserLifeEventEntity::getStatus, "PENDING"));
            for (ExtractedLifeEvent ev : events) {
                if (ev.title() == null || ev.title().isBlank() || ev.targetDate() == null) continue;
                LocalDate targetDate;
                try { targetDate = LocalDate.parse(ev.targetDate().trim()); } catch (Exception e) { continue; }
                UserLifeEventEntity matched = existingPending.stream().filter(p ->
                        isSameEvent(p.getTitle(), ev.title()) ||
                        (Math.abs(java.time.temporal.ChronoUnit.DAYS.between(p.getTargetDate(), targetDate)) <= 2
                                && (p.getTitle().contains(ev.title()) || ev.title().contains(p.getTitle()))))
                        .findFirst().orElse(null);
                if (matched != null) {
                    List<Long> ids = parseDiaryIds(matched.getDiaryIdsJson());
                    if (!ids.contains(diaryId)) ids.add(diaryId);
                    matched.setDiaryIdsJson(objectMapper.writeValueAsString(ids));
                    matched.setLastDiaryId(diaryId);
                    matched.setTargetDate(targetDate);
                    matched.setUpdatedAt(LocalDateTime.now());
                    userLifeEventMapper.updateById(matched);
                    log.info("已追加关联日记到事件 userId={}, eventId={}, diaryId={}", userId, matched.getId(), diaryId);
                } else {
                    UserLifeEventEntity e = new UserLifeEventEntity();
                    e.setUserId(userId); e.setTitle(ev.title().trim());
                    e.setDescription(ev.description() != null ? ev.description().trim() : "");
                    e.setTargetDate(targetDate); e.setStatus("PENDING");
                    e.setDiaryIdsJson(objectMapper.writeValueAsString(List.of(diaryId)));
                    e.setLastDiaryId(diaryId);
                    e.setCreatedAt(LocalDateTime.now()); e.setUpdatedAt(LocalDateTime.now());
                    userLifeEventMapper.insert(e);
                    existingPending.add(e);
                    log.info("已提取未来重要事件 userId={}, title={}", userId, e.getTitle());
                }
            }
        } catch (Exception e) {
            log.warn("提取重要事件失败 userId={}, diaryId={}: {}", userId, diaryId, e.getMessage());
            throw new IllegalStateException("重要事件提取失败", e);
        }
    }

    private boolean isSameEvent(String a, String b) {
        if (a == null || b == null) return false;
        a = a.trim().toLowerCase(); b = b.trim().toLowerCase();
        return a.equals(b) || a.contains(b) || b.contains(a);
    }

    private List<Long> parseDiaryIds(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try { return objectMapper.readValue(json, new TypeReference<List<Long>>() {}); }
        catch (Exception e) { return new ArrayList<>(); }
    }

    public List<LifeEventView> listUserEvents(Long userId) {
        List<UserLifeEventEntity> list = userLifeEventMapper.selectList(
                new LambdaQueryWrapper<UserLifeEventEntity>()
                        .eq(UserLifeEventEntity::getUserId, userId)
                        .orderByDesc(UserLifeEventEntity::getTargetDate));
        List<LifeEventView> views = new ArrayList<>();
        for (UserLifeEventEntity e : list) {
            views.add(new LifeEventView(e.getId(), e.getTitle(), e.getDescription(),
                    e.getTargetDate() != null ? e.getTargetDate().toString() : "", visibleStatus(e.getStatus()),
                    parseDiaryIds(e.getDiaryIdsJson()), e.getLastDiaryId(), e.getFollowUpNote(),
                    e.getCreatedAt() != null ? e.getCreatedAt().toString() : "",
                    e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : ""));
        }
        return views;
    }

    public LifeEventView updateEventStatus(Long userId, Long eventId, String status, String note) {
        UserLifeEventEntity entity = userLifeEventMapper.selectOne(
                new LambdaQueryWrapper<UserLifeEventEntity>()
                        .eq(UserLifeEventEntity::getId, eventId).eq(UserLifeEventEntity::getUserId, userId));
        if (entity == null) throw new IllegalArgumentException("事件不存在");
        if (status != null && !status.isBlank()) {
            String normalized = status.toUpperCase().trim();
            if (!"PENDING".equals(normalized) && !"FOLLOWED_UP".equals(normalized)) {
                throw new ResponseStatusException(BAD_REQUEST, "事件状态只能是 PENDING 或 FOLLOWED_UP");
            }
            entity.setStatus(normalized);
        }
        if (note != null) entity.setFollowUpNote(note);
        entity.setUpdatedAt(LocalDateTime.now());
        userLifeEventMapper.updateById(entity);
        return new LifeEventView(entity.getId(), entity.getTitle(), entity.getDescription(),
                entity.getTargetDate() != null ? entity.getTargetDate().toString() : "", visibleStatus(entity.getStatus()),
                parseDiaryIds(entity.getDiaryIdsJson()), entity.getLastDiaryId(), entity.getFollowUpNote(),
                entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : "",
                entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : "");
    }

    public Optional<UserLifeEventEntity> getPendingEventForFollowUp(Long userId) {
        LocalDate today = LocalDate.now();
        List<UserLifeEventEntity> pending = userLifeEventMapper.selectList(
                new LambdaQueryWrapper<UserLifeEventEntity>()
                        .eq(UserLifeEventEntity::getUserId, userId)
                        .eq(UserLifeEventEntity::getStatus, "PENDING")
                        .le(UserLifeEventEntity::getTargetDate, today)
                        .orderByDesc(UserLifeEventEntity::getTargetDate).last("LIMIT 1"));
        return pending.isEmpty() ? Optional.empty() : Optional.of(pending.get(0));
    }

    public boolean markEventFollowedUp(Long userId, Long eventId) {
        UserLifeEventEntity entity = findOwnedEvent(userId, eventId);
        if (entity != null && "PENDING".equals(entity.getStatus())) {
            entity.setStatus("FOLLOWED_UP"); entity.setUpdatedAt(LocalDateTime.now());
            userLifeEventMapper.updateById(entity);
            return true;
        }
        return entity != null;
    }

    private String visibleStatus(String status) {
        return "ARCHIVED".equalsIgnoreCase(status) ? "FOLLOWED_UP" : status;
    }

    public String buildEventContextForChat(Long userId, Long eventId) {
        UserLifeEventEntity entity = findOwnedEvent(userId, eventId);
        if (entity == null) return "";
        List<Long> diaryIds = parseDiaryIds(entity.getDiaryIdsJson());
        StringBuilder sb = new StringBuilder();
        sb.append("[重点跟进事件]\n- 事件名称：").append(entity.getTitle()).append("\n");
        sb.append("- 约定发生日期：").append(entity.getTargetDate()).append("\n");
        if (entity.getDescription() != null && !entity.getDescription().isBlank())
            sb.append("- 背景描述：").append(entity.getDescription()).append("\n");
        if (!diaryIds.isEmpty()) {
            List<DiaryEntity> diaries = diaryMapper.selectList(new LambdaQueryWrapper<DiaryEntity>()
                    .in(DiaryEntity::getId, diaryIds)
                    .eq(DiaryEntity::getAuthorUserId, userId)
                    .eq(DiaryEntity::getIsDeleted, false));
            sb.append("- 相关日记（共 ").append(diaries.size()).append(" 篇）：\n");
            for (DiaryEntity d : diaries) {
                if (d != null) {
                    String clean = d.getContent() != null ? d.getContent().replaceAll("<[^>]+>", "").trim() : "";
                    if (clean.length() > 100) clean = clean.substring(0, 100) + "...";
                    sb.append("  * ").append(d.getCreatedAt() != null ? d.getCreatedAt().toLocalDate() : "").append("：").append(clean).append("\n");
                }
            }
        }
        return sb.toString();
    }

    private UserLifeEventEntity findOwnedEvent(Long userId, Long eventId) {
        if (userId == null || eventId == null) return null;
        return userLifeEventMapper.selectOne(new LambdaQueryWrapper<UserLifeEventEntity>()
                .eq(UserLifeEventEntity::getId, eventId)
                .eq(UserLifeEventEntity::getUserId, userId));
    }
}
