package com.moodcopilot.ai;

import com.moodcopilot.entity.UserProfileMemoryEntity;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Selects eligible context; rendering is delegated to PromptRenderer. */
@Component
public class ContextPlanner {
    private static final String PLANNER_VERSION = "2";

    private final MemoryOrchestrator memoryOrchestrator;
    private final PromptRenderer promptRenderer;
    private final ContextMetadataRecorder metadataRecorder;
    private final ZoneId businessTimeZone;

    public ContextPlanner(MemoryOrchestrator memoryOrchestrator) {
        this(memoryOrchestrator, new XmlPromptRenderer(), null, ZoneId.of("Asia/Shanghai"));
    }

    public ContextPlanner(MemoryOrchestrator memoryOrchestrator, PromptRenderer promptRenderer) {
        this(memoryOrchestrator, promptRenderer, null, ZoneId.of("Asia/Shanghai"));
    }

    @Autowired
    public ContextPlanner(MemoryOrchestrator memoryOrchestrator,
            @Value("${moodcopilot.time-zone:Asia/Shanghai}") String timeZone,
            ContextMetadataRecorder metadataRecorder) {
        this(memoryOrchestrator, new XmlPromptRenderer(), metadataRecorder, parseZone(timeZone));
    }

    public ContextPlanner(MemoryOrchestrator memoryOrchestrator, PromptRenderer promptRenderer,
            ContextMetadataRecorder metadataRecorder, ZoneId businessTimeZone) {
        this.memoryOrchestrator = memoryOrchestrator;
        this.promptRenderer = promptRenderer == null ? new XmlPromptRenderer() : promptRenderer;
        this.metadataRecorder = metadataRecorder;
        this.businessTimeZone = businessTimeZone == null ? ZoneId.of("Asia/Shanghai") : businessTimeZone;
    }

    /** Compatibility overload for callers which still provide a rendered RAG string. */
    public ContextPlan plan(long userId, String coreMemory, List<String> references, String ragContext) {
        List<ContextItem> legacyRag = ragContext == null || ragContext.isBlank()
                ? List.of()
                : List.of(new ContextItem(ragContext, new ContextSource(
                        "legacy_rag", "legacy", "system", "retrieved_context", null,
                        null, ContextSource.TrustLevel.SUPPORTING, userId), 0D, 20, false));
        return planEnvelope(userId, null, coreMemory, references, legacyRag, ContextPurpose.CHAT);
    }

    public ContextPlan planEnvelope(long userId, Long conversationId, String coreMemory,
            List<String> references, List<ContextItem> retrievedContext, ContextPurpose purpose) {
        List<UserProfileMemoryEntity> currentMemories = memoryOrchestrator.current(userId);
        if (currentMemories == null) currentMemories = List.of();

        List<ContextItem> coreItems = currentMemories.stream()
                .filter(this::isEligibleFormal)
                .filter(memory -> Boolean.TRUE.equals(memory.getIsCore()))
                .sorted(Comparator.comparing(this::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(15)
                .map(memory -> memoryItem(userId, memory, true))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        // The compatibility argument can also contain an explicitly selected event context.
        // Keep that context, but never treat the whole serialized memory prompt as one fact.
        String eventContext = extractEventContext(coreMemory);
        if (!eventContext.isBlank()) {
            coreItems.add(new ContextItem(limit(eventContext, 2800), new ContextSource(
                    "LIFE_EVENT", "referenced-event", "user", "event_context", null,
                    null, ContextSource.TrustLevel.AUTHORITATIVE, userId), 1D, 45, false));
        } else if (coreItems.isEmpty() && coreMemory != null && !coreMemory.isBlank()) {
            // Preserve callers/tests that still supply a pre-rendered background without a
            // database memory snapshot. This branch is only a compatibility fallback.
            coreItems.add(new ContextItem(limit(coreMemory, 6000), new ContextSource(
                    "FORMAL_MEMORY", "legacy-core", "user", "structured_memory", null,
                    null, ContextSource.TrustLevel.AUTHORITATIVE, userId), 1D, 50, false));
        }

        List<ContextItem> shortTerm = currentMemories.stream()
                .filter(this::isEligibleShortTerm)
                .sorted(Comparator.comparing(this::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .map(memory -> memoryItem(userId, memory))
                .toList();

        List<ContextItem> userReferences = references == null ? List.of() : references.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .limit(2)
                .map(value -> new ContextItem(limit(value, 2800), new ContextSource(
                        "USER_DIARY", "reference", "user", "original", null,
                        null, ContextSource.TrustLevel.AUTHORITATIVE, userId), 1D, 40, false))
                .toList();

        ContextEnvelope envelope = new ContextEnvelope(
                java.util.UUID.randomUUID().toString(), conversationId, userId,
                purpose == null ? ContextPurpose.CHAT : purpose, Instant.now(), PLANNER_VERSION,
                coreItems, shortTerm, userReferences,
                filterRetrieved(retrievedContext, userId), List.of(), List.of());
        if (metadataRecorder != null) metadataRecorder.record(envelope);
        return new ContextPlan(promptRenderer.render(envelope), envelope);
    }

    private List<ContextItem> filterRetrieved(List<ContextItem> items, long userId) {
        if (items == null) return List.of();
        return items.stream()
                .filter(item -> item != null && item.source() != null)
                .filter(item -> item.source().userId() != null && item.source().userId() == userId)
                .filter(item -> !item.content().isBlank())
                .filter(item -> isAllowedSource(item.source().sourceType()))
                .sorted(Comparator.comparingInt(ContextItem::priority).reversed()
                        .thenComparingDouble(ContextItem::relevanceScore))
                .filter(new java.util.function.Predicate<>() {
                    private final Set<String> seen = new HashSet<>();

                    @Override
                    public boolean test(ContextItem item) {
                        String key = item.source().sourceType() + "\u0000"
                                + (item.source().sourceId() == null ? "" : item.source().sourceId())
                                + "\u0000" + item.content().replaceAll("\\s+", " ").trim();
                        return seen.add(key);
                    }
                })
                .limit(20)
                .toList();
    }

    private boolean isAllowedSource(String sourceType) {
        if (sourceType == null) return false;
        String normalized = sourceType.toLowerCase(java.util.Locale.ROOT);
        return !normalized.contains("candidate") && !normalized.contains("rejected")
                && !normalized.contains("expired") && !normalized.contains("superseded")
                && !normalized.contains("deleted");
    }

    private boolean isEligibleFormal(UserProfileMemoryEntity memory) {
        if (memory == null || !"active".equalsIgnoreCase(memory.getStatus())) return false;
        if ("short_term_state".equals(memory.getMemoryType())) return false;
        LocalDate today = LocalDate.now(businessTimeZone);
        return (memory.getValidFrom() == null || !today.isBefore(memory.getValidFrom()))
                && (memory.getValidUntil() == null || !today.isAfter(memory.getValidUntil()));
    }

    private boolean isEligibleShortTerm(UserProfileMemoryEntity memory) {
        if (memory == null || !"short_term_state".equals(memory.getMemoryType())) return false;
        if (!"active".equalsIgnoreCase(memory.getStatus())) return false;
        LocalDate today = LocalDate.now(businessTimeZone);
        return (memory.getValidFrom() == null || !today.isBefore(memory.getValidFrom()))
                && (memory.getValidUntil() == null || today.isBefore(memory.getValidUntil().plusDays(1)));
    }

    private ContextItem memoryItem(long userId, UserProfileMemoryEntity memory) {
        return memoryItem(userId, memory, false);
    }

    private ContextItem memoryItem(long userId, UserProfileMemoryEntity memory, boolean core) {
        String content = limit(memory.getAttributeKey(), 64) + "：" + limit(memory.getAttributeValue(), 500);
        LocalDateTime updated = updatedAt(memory);
        Instant eventTime = updated == null ? null : updated.atZone(businessTimeZone).toInstant();
        return new ContextItem(content, new ContextSource(
                "FORMAL_MEMORY", String.valueOf(memory.getId()), "user",
                core ? "structured_memory" : "short_term_state",
                eventTime, null, ContextSource.TrustLevel.AUTHORITATIVE, userId),
                memory.getConfidence() == null ? 0.5D : memory.getConfidence(), core ? 50 : 30, false);
    }

    private String extractEventContext(String value) {
        if (value == null) return "";
        int start = value.indexOf("[重点跟进事件背景");
        return start < 0 ? "" : value.substring(start);
    }

    private LocalDateTime updatedAt(UserProfileMemoryEntity memory) {
        return memory.getUpdatedAt() != null ? memory.getUpdatedAt() : memory.getUpdateTime();
    }

    private String limit(String value, int maxLength) {
        if (value == null) return "";
        String normalized = value.replaceAll("[\\p{Cntrl}&&[^\\n]]", "").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }

    private static ZoneId parseZone(String value) {
        try {
            return value == null || value.isBlank() ? ZoneId.of("Asia/Shanghai") : ZoneId.of(value.trim());
        } catch (RuntimeException ignored) {
            return ZoneId.of("Asia/Shanghai");
        }
    }

    public record ContextPlan(String context, ContextEnvelope envelope) {
    }
}
