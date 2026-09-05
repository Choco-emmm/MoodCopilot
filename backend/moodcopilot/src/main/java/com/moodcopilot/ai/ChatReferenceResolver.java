package com.moodcopilot.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodcopilot.entity.DiaryEntity;
import com.moodcopilot.entity.UserLifeEventEntity;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.mapper.UserLifeEventMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Resolves explicitly selected references with an owner check and bounded data. */
@Service
public class ChatReferenceResolver {
    private static final int MAX_REFERENCES = 4;
    private static final int MAX_CONTENT_LENGTH = 5000;

    private final DiaryMapper diaryMapper;
    private final UserLifeEventMapper eventMapper;
    private final ZoneId businessTimeZone;

    public ChatReferenceResolver(DiaryMapper diaryMapper, UserLifeEventMapper eventMapper,
            @Value("${moodcopilot.time-zone:Asia/Shanghai}") String timeZone) {
        this.diaryMapper = diaryMapper;
        this.eventMapper = eventMapper;
        this.businessTimeZone = parseZone(timeZone);
    }

    public List<UserReference> resolve(Long userId, List<ChatReferenceRequest> requests,
            ReferencePurpose defaultPurpose) {
        if (userId == null || userId <= 0 || requests == null || requests.isEmpty()) return List.of();
        Set<String> seen = new LinkedHashSet<>();
        List<UserReference> result = new ArrayList<>();
        for (ChatReferenceRequest request : requests) {
            if (result.size() >= MAX_REFERENCES || request == null || request.sourceId() == null
                    || request.sourceId() <= 0) continue;
            String type = normalizeType(request.sourceType());
            if (!seen.add(type + ":" + request.sourceId())) continue;
            ReferencePurpose purpose = parsePurpose(request.referencePurpose(), defaultPurpose);
            UserReference resolved = switch (type) {
                case "diary" -> resolveDiary(userId, request.sourceId(), purpose);
                case "event" -> resolveEvent(userId, request.sourceId(), purpose);
                default -> null;
            };
            if (resolved != null) result.add(resolved);
        }
        return List.copyOf(result);
    }

    private UserReference resolveDiary(Long userId, Long diaryId, ReferencePurpose purpose) {
        DiaryEntity diary = diaryMapper.selectOne(new LambdaQueryWrapper<DiaryEntity>()
                .eq(DiaryEntity::getId, diaryId)
                .eq(DiaryEntity::getAuthorUserId, userId)
                .eq(DiaryEntity::getIsDeleted, false));
        if (diary == null || diary.getContent() == null || diary.getContent().isBlank()) return null;
        String content = bounded(diary.getContent());
        return new UserReference(content, new ContextSource(
                "USER_DIARY", String.valueOf(diary.getId()), "user", "original",
                diary.getCreatedAt() == null ? null : diary.getCreatedAt().atZone(businessTimeZone).toInstant(),
                null, ContextSource.TrustLevel.AUTHORITATIVE, userId), purpose, 1D, 60, false);
    }

    private UserReference resolveEvent(Long userId, Long eventId, ReferencePurpose purpose) {
        UserLifeEventEntity event = eventMapper.selectOne(new LambdaQueryWrapper<UserLifeEventEntity>()
                .eq(UserLifeEventEntity::getId, eventId)
                .eq(UserLifeEventEntity::getUserId, userId)
                .isNull(UserLifeEventEntity::getDeletedAt));
        if (event == null || event.getTitle() == null || event.getTitle().isBlank()) return null;
        StringBuilder content = new StringBuilder("事件：").append(event.getTitle().trim());
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            content.append("\n说明：").append(event.getDescription().trim());
        }
        if (event.getTargetDate() != null) {
            content.append("\n时间：").append(event.getTargetDate());
            if (event.getEndDate() != null) content.append(" 至 ").append(event.getEndDate());
        }
        return new UserReference(bounded(content.toString()), new ContextSource(
                "LIFE_EVENT", String.valueOf(event.getId()), "user", "event_record",
                event.getTargetDate() == null ? null : event.getTargetDate().atStartOfDay(businessTimeZone).toInstant(),
                null, ContextSource.TrustLevel.AUTHORITATIVE, userId), purpose, 1D, 60, false);
    }

    private String normalizeType(String value) {
        if (value == null) return "";
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "diary", "user_diary" -> "diary";
            case "event", "life_event" -> "event";
            default -> "";
        };
    }

    private ReferencePurpose parsePurpose(String value, ReferencePurpose fallback) {
        if (value == null || value.isBlank()) return fallback == null ? ReferencePurpose.DISCUSS : fallback;
        try {
            return ReferencePurpose.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback == null ? ReferencePurpose.DISCUSS : fallback;
        }
    }

    private String bounded(String value) {
        String normalized = value == null ? "" : value.replaceAll("[\\p{Cntrl}&&[^\\n]]", "").trim();
        return normalized.length() <= MAX_CONTENT_LENGTH
                ? normalized : normalized.substring(0, MAX_CONTENT_LENGTH) + "...";
    }

    private ZoneId parseZone(String value) {
        try {
            return value == null || value.isBlank() ? ZoneId.of("Asia/Shanghai") : ZoneId.of(value.trim());
        } catch (RuntimeException ignored) {
            return ZoneId.of("Asia/Shanghai");
        }
    }
}
