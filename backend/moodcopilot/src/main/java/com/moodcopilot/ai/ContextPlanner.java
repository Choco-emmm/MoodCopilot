package com.moodcopilot.ai;

import com.moodcopilot.entity.UserProfileMemoryEntity;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger log = LoggerFactory.getLogger(ContextPlanner.class);
    private static final String PLANNER_VERSION = "2";
    private static final Set<String> ALLOWED_SOURCE_TYPES = Set.of(
            "USER_MESSAGE", "USER_DIARY", "USER_UPLOADED_IMAGE", "USER_PROVIDED_LYRICS", "FORMAL_MEMORY",
            "LIFE_EVENT", "LIFE_SEGMENT", "SYSTEM_IMAGE_CAPTION", "SYSTEM_SUMMARY",
            "SYSTEM_GRAPH_DERIVATION", "ASSISTANT_MESSAGE", "TOOL_RESULT", "EXTERNAL_CONTENT", "legacy_rag");

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
        return planEnvelopeWithReferencePurpose(userId, conversationId, coreMemory, references,
                ReferencePurpose.DISCUSS, retrievedContext, purpose, List.of());
    }

    public ContextPlan planEnvelope(long userId, Long conversationId, String coreMemory,
            List<String> references, List<ContextItem> retrievedContext, ContextPurpose purpose,
            List<ContextItem> timelineContext) {
        return planEnvelopeWithReferencePurpose(userId, conversationId, coreMemory, references,
                ReferencePurpose.DISCUSS, retrievedContext, purpose, timelineContext);
    }

    /**
     * Structured planning entry point for callers that know why the user attached
     * the reference. The legacy text-only entry points intentionally default to DISCUSS.
     */
    public ContextPlan planEnvelopeWithReferencePurpose(long userId, Long conversationId, String coreMemory,
            List<String> references, ReferencePurpose referencePurpose, List<ContextItem> retrievedContext,
            ContextPurpose purpose, List<ContextItem> timelineContext) {
        return planEnvelopeWithReferencePurpose(userId, conversationId, coreMemory, references, referencePurpose,
                retrievedContext, purpose, timelineContext, List.of());
    }

    /**
     * Structured reference entry point. References in this overload have already
     * been resolved and owner-checked by the server.
     */
    public ContextPlan planEnvelopeWithReferencePurpose(long userId, Long conversationId, String coreMemory,
            List<String> references, ReferencePurpose referencePurpose, List<ContextItem> retrievedContext,
            ContextPurpose purpose, List<ContextItem> timelineContext, List<UserReference> resolvedReferences) {
        return planEnvelopeWithReferencePurpose(userId, conversationId, coreMemory, references, referencePurpose,
                retrievedContext, purpose, timelineContext, resolvedReferences, null);
    }

    /**
     * Plans context with the request's task type. General-purpose coding requests
     * must not implicitly expose private profile/timeline data; explicit references
     * remain available because the user selected them for this turn.
     */
    public ContextPlan planEnvelopeWithReferencePurpose(long userId, Long conversationId, String coreMemory,
            List<String> references, ReferencePurpose referencePurpose, List<ContextItem> retrievedContext,
            ContextPurpose purpose, List<ContextItem> timelineContext, List<UserReference> resolvedReferences,
            TaskContext taskContext) {
        boolean allowImplicitPrivateContext = taskContext == null
                || !"CODING".equalsIgnoreCase(taskContext.taskType());
        List<UserProfileMemoryEntity> currentMemories;
        try {
            currentMemories = allowImplicitPrivateContext ? memoryOrchestrator.current(userId) : List.of();
        } catch (RuntimeException e) {
            // Context is an enhancement. A temporary profile database failure must
            // not prevent a user from receiving a normal chat response.
            log.warn("读取用户画像失败，继续使用无画像上下文 userId={} errorType={}", userId,
                    e.getClass().getSimpleName());
            currentMemories = List.of();
        }
        if (currentMemories == null) currentMemories = List.of();

        List<ContextItem> coreItems = currentMemories.stream()
                .filter(memory -> belongsToUser(memory, userId))
                .filter(this::isEligibleFormal)
                .filter(memory -> Boolean.TRUE.equals(memory.getIsCore()))
                .sorted(Comparator.comparing(this::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(15)
                .map(memory -> memoryItem(userId, memory, true))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        // The compatibility argument can also contain an explicitly selected event context.
        // Keep that context, but never treat the whole serialized memory prompt as one fact.
        String eventContext = allowImplicitPrivateContext ? extractEventContext(coreMemory) : "";
        if (!eventContext.isBlank()) {
            coreItems.add(new ContextItem(limit(eventContext, 2800), new ContextSource(
                    "LIFE_EVENT", "referenced-event", "user", "event_context", null,
                    null, ContextSource.TrustLevel.AUTHORITATIVE, userId), 1D, 45, false));
        } else if (allowImplicitPrivateContext && coreItems.isEmpty() && coreMemory != null && !coreMemory.isBlank()) {
            // Preserve callers/tests that still supply a pre-rendered background without a
            // database memory snapshot. This branch is only a compatibility fallback;
            // the text has no verifiable source metadata, so it must not be presented as
            // an authoritative formal memory.
            coreItems.add(new ContextItem(limit(coreMemory, 6000), new ContextSource(
                    "SYSTEM_SUMMARY", "legacy-core", "system", "legacy_context", null,
                    null, ContextSource.TrustLevel.UNTRUSTED, userId), 1D, 50, false));
        }

        List<ContextItem> shortTerm = currentMemories.stream()
                .filter(memory -> belongsToUser(memory, userId))
                .filter(this::isEligibleShortTerm)
                .sorted(Comparator.comparing(this::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .map(memory -> memoryItem(userId, memory))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        // Ordinary formal memories are not loaded wholesale into normal chat. Chat
        // retrieves them on demand through the owner-checked tool; analysis-like
        // contexts may still use a bounded snapshot supplied by this planner.
        List<ContextItem> ordinaryFormal = purpose == ContextPurpose.CHAT ? new ArrayList<>()
                : currentMemories.stream()
                        .filter(memory -> belongsToUser(memory, userId))
                        .filter(this::isEligibleFormal)
                        .filter(memory -> !Boolean.TRUE.equals(memory.getIsCore()))
                        .sorted(Comparator.comparing(this::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                        .limit(10)
                        .map(memory -> memoryItem(userId, memory))
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        markMemoryConflicts(coreItems, shortTerm, ordinaryFormal);

        List<UserReference> userReferences = new ArrayList<>();
        Set<String> seenReferences = new HashSet<>();
        Set<String> resolvedReferenceContents = new HashSet<>();
        if (resolvedReferences != null) {
            resolvedReferences.stream()
                    .filter(reference -> reference != null && reference.source() != null)
                    .filter(reference -> reference.source().userId() != null
                            && reference.source().userId() == userId)
                    .filter(reference -> !reference.content().isBlank())
                    .filter(reference -> !SensitiveDataDetector.containsSensitiveData(reference.content()))
                    .filter(reference -> isAllowedSource(reference.source().sourceType()))
                    .filter(reference -> userReferences.size() < 4)
                    .filter(reference -> seenReferences.add(referenceKey(reference)))
                    .forEach(reference -> {
                        userReferences.add(reference);
                        resolvedReferenceContents.add(normalizeReferenceContent(reference.content()));
                    });
        }
        if (references != null) references.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .filter(value -> !SensitiveDataDetector.containsSensitiveData(value))
                .filter(value -> !resolvedReferenceContents.contains(normalizeReferenceContent(value)))
                // The legacy API carries reference text but no verifiable source ID.
                // Treat it as an explicit user-provided reference instead of claiming
                // that arbitrary client text is an original diary record.
                .map(value -> new UserReference(limit(value, 2800), new ContextSource(
                        "USER_MESSAGE", "reference", "user", "explicit_reference", null,
                        null, ContextSource.TrustLevel.AUTHORITATIVE, userId),
                        referencePurpose == null ? ReferencePurpose.DISCUSS : referencePurpose,
                        1D, 60, false))
                .filter(reference -> userReferences.size() < 4)
                .filter(reference -> seenReferences.add(referenceKey(reference)))
                .forEach(userReferences::add);

        List<ContextItem> selectedRetrieved = new ArrayList<>(ordinaryFormal);
        // RAG hits supplied by a caller are still subject to source ownership and
        // eligibility. Coding requests may use explicit references, but do not get
        // implicit historical retrievals from this planner.
        if (allowImplicitPrivateContext) {
            selectedRetrieved.addAll(filterRetrieved(retrievedContext, userId));
        }
        ContextEnvelope envelope = new ContextEnvelope(
                java.util.UUID.randomUUID().toString(), conversationId, userId,
                purpose == null ? ContextPurpose.CHAT : purpose, Instant.now(), PLANNER_VERSION,
                coreItems, shortTerm, userReferences,
                filterRetrieved(selectedRetrieved, userId),
                allowImplicitPrivateContext ? filterRetrieved(timelineContext, userId) : List.of(), List.of());
        return new ContextPlan(promptRenderer.render(envelope), envelope);
    }

    /**
     * Selects RAG results for legacy callers that already own the surrounding
     * task prompt. Rendering remains outside the retrieval service, and the
     * same ownership, eligibility, ordering and deduplication rules apply.
     */
    public List<ContextItem> selectRetrievedContext(long userId, List<ContextItem> items) {
        return filterRetrieved(items, userId);
    }

    private List<ContextItem> filterRetrieved(List<ContextItem> items, long userId) {
        if (items == null) return List.of();
        return items.stream()
                .filter(item -> item != null && item.source() != null)
                .filter(item -> item.source().userId() != null && item.source().userId() == userId)
                .filter(item -> !item.content().isBlank())
                .filter(item -> !SensitiveDataDetector.containsSensitiveData(item.content()))
                .filter(item -> isAllowedSource(item.source().sourceType()))
                .filter(item -> isAuthorizedSource(item.source()))
                .sorted(Comparator.comparingInt(ContextItem::priority).reversed()
                        .thenComparing(Comparator.comparingDouble(ContextItem::relevanceScore).reversed()))
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
        if (!ALLOWED_SOURCE_TYPES.stream().anyMatch(value -> value.equalsIgnoreCase(sourceType))) return false;
        return !normalized.contains("candidate") && !normalized.contains("rejected")
                && !normalized.contains("expired") && !normalized.contains("superseded")
                && !normalized.contains("deleted");
    }

    private boolean isAuthorizedSource(ContextSource source) {
        if (source == null || source.sourceId() == null || source.sourceId().isBlank()) return false;
        String sourceType = source.sourceType().toLowerCase(java.util.Locale.ROOT);
        String authorType = source.authorType().toLowerCase(java.util.Locale.ROOT);
        return !sourceType.contains("assistant") && !authorType.contains("assistant");
    }

    private String referenceKey(UserReference reference) {
        ContextSource source = reference.source();
        return source.sourceType() + "\u0000" + (source.sourceId() == null ? "" : source.sourceId())
                + "\u0000" + normalizeReferenceContent(reference.content());
    }

    private String normalizeReferenceContent(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    @SafeVarargs
    private final void markMemoryConflicts(List<ContextItem>... buckets) {
        java.util.Map<String, Set<String>> valuesByKey = new java.util.HashMap<>();
        for (List<ContextItem> bucket : buckets) {
            if (bucket == null) continue;
            for (ContextItem item : bucket) {
                if (item == null || item.source() == null
                        || !"FORMAL_MEMORY".equalsIgnoreCase(item.source().sourceType())) continue;
                String[] parts = item.content().split("：", 2);
                if (parts.length == 2) {
                    valuesByKey.computeIfAbsent(parts[0], ignored -> new HashSet<>()).add(parts[1]);
                }
            }
        }
        if (valuesByKey.values().stream().noneMatch(values -> values.size() > 1)) return;
        for (List<ContextItem> bucket : buckets) {
            if (bucket == null) continue;
            for (int i = 0; i < bucket.size(); i++) {
                ContextItem item = bucket.get(i);
                if (item == null || item.source() == null
                        || !"FORMAL_MEMORY".equalsIgnoreCase(item.source().sourceType())) continue;
                String[] parts = item.content().split("：", 2);
                if (parts.length == 2 && valuesByKey.getOrDefault(parts[0], Set.of()).size() > 1) {
                    bucket.set(i, new ContextItem(item.content(), item.source(), item.relevanceScore(), item.priority(), true));
                }
            }
        }
    }

    private boolean isEligibleFormal(UserProfileMemoryEntity memory) {
        if (memory == null || !"active".equalsIgnoreCase(memory.getStatus())) return false;
        if (SensitiveDataDetector.containsSensitiveData(memory.getAttributeKey())
                || SensitiveDataDetector.containsSensitiveData(memory.getAttributeValue())) return false;
        if ("short_term_state".equals(memory.getMemoryType())) return false;
        LocalDate today = LocalDate.now(businessTimeZone);
        return (memory.getValidFrom() == null || !today.isBefore(memory.getValidFrom()))
                && (memory.getValidUntil() == null || !today.isAfter(memory.getValidUntil()));
    }

    private boolean belongsToUser(UserProfileMemoryEntity memory, long userId) {
        return memory != null && memory.getUserId() != null && memory.getUserId() == userId;
    }

    private boolean isEligibleShortTerm(UserProfileMemoryEntity memory) {
        if (memory == null || !"short_term_state".equals(memory.getMemoryType())) return false;
        if (!"active".equalsIgnoreCase(memory.getStatus())) return false;
        LocalDate today = LocalDate.now(businessTimeZone);
        return (memory.getValidFrom() == null || !today.isBefore(memory.getValidFrom()))
                && (memory.getValidUntil() == null || today.isBefore(memory.getValidUntil()));
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
                "short_term_state".equalsIgnoreCase(memory.getMemoryType()) ? "short_term_state" : "structured_memory",
                eventTime, null, ContextSource.TrustLevel.AUTHORITATIVE, userId),
                memory.getConfidence() == null ? 0.5D : memory.getConfidence(),
                core ? 50 : ("short_term_state".equalsIgnoreCase(memory.getMemoryType()) ? 30 : 35), false);
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
