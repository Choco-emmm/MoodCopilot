package com.moodcopilot.ai;

import com.moodcopilot.common.ApiResponse;
import com.moodcopilot.entity.UserProfileMemoryEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.moodcopilot.entity.UserMemoryCandidateEntity;

@RestController
@RequestMapping("/api/memory")
public class UserProfileMemoryController {

    private final MemoryExtractionService memoryExtractionService;
    private final MemoryConsolidationService memoryConsolidationService;
    private final MemoryOrchestrator memoryOrchestrator;

    public UserProfileMemoryController(MemoryExtractionService memoryExtractionService,
                                       MemoryConsolidationService memoryConsolidationService,
                                       MemoryOrchestrator memoryOrchestrator) {
        this.memoryExtractionService = memoryExtractionService;
        this.memoryConsolidationService = memoryConsolidationService;
        this.memoryOrchestrator = memoryOrchestrator;
    }

    @PostMapping("/consolidate/preview")
    public ApiResponse<List<MemoryConsolidationService.ConsolidationItem>> previewConsolidate() {
        var user = (com.moodcopilot.entity.UserEntity) org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.ok(memoryConsolidationService.previewConsolidation(user.getId()));
    }

    @PostMapping("/consolidate/apply")
    public ApiResponse<Void> applyConsolidate(@RequestBody List<MemoryConsolidationService.ConsolidationItem> attributes) {
        var user = (com.moodcopilot.entity.UserEntity) org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        memoryConsolidationService.applyConsolidation(user.getId(), attributes);
        return ApiResponse.ok(null);
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        List<UserProfileMemoryEntity> memories = memoryExtractionService.listCurrentUserMemories();
        var user = (com.moodcopilot.entity.UserEntity) org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Map<Long, MemoryOrchestrator.SourceSummary> sources = memoryOrchestrator.sourceSummariesForMemories(
                user.getId(), memories.stream().map(UserProfileMemoryEntity::getId).toList());
        Map<Long, MemoryOrchestrator.DiarySourcePreview> diaryPreviews = memoryOrchestrator.diarySourcePreviews(user.getId(),
                sources.values().stream().flatMap(source -> source.diaryIds().stream()).distinct().toList());
        List<Map<String, Object>> result = memories.stream()
                .map(m -> {
                    Map<String, Object> map = new java.util.LinkedHashMap<>();
                    map.put("id", m.getId());
                    map.put("attributeKey", m.getAttributeKey());
                    map.put("attributeValue", m.getAttributeValue());
                    map.put("isCore", Boolean.TRUE.equals(m.getIsCore()));
                    map.put("memoryType", m.getMemoryType());
                    map.put("sourceType", m.getSourceType());
                    map.put("sourceDiaryId", m.getSourceDiaryId());
                    map.put("sourceConversationId", m.getSourceConversationId());
                    map.put("confidence", m.getConfidence());
                    map.put("validFrom", m.getValidFrom());
                    map.put("validUntil", m.getValidUntil());
                    map.put("status", m.getStatus());
                    map.put("previousMemoryId", m.getPreviousMemoryId());
                    map.put("supersededAt", m.getSupersededAt());
                    map.put("supersededReason", m.getSupersededReason());
                    map.put("lastEvidenceAt", m.getLastEvidenceAt());
                    map.put("updatedAt", m.getUpdatedAt() != null ? m.getUpdatedAt() : m.getUpdateTime());
                    MemoryOrchestrator.SourceSummary source = sources.get(m.getId());
                    map.put("evidenceCount", source == null ? 0 : source.evidenceCount());
                    map.put("sourceDiaryIds", source == null ? List.of() : source.diaryIds());
                    map.put("sourceDiaryPreviews", sourceDiaryPreviews(source, diaryPreviews));
                    map.put("sourceConversationIds", source == null ? List.of() : source.conversationIds());
                    if (m.getUpdateTime() != null) {
                        map.put("updateTime", m.getUpdateTime().toString());
                    }
                    return map;
                })
                .toList();
        return ApiResponse.ok(result);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        memoryExtractionService.deleteMemory(id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        String newValue = body.containsKey("attributeValue") ? (String) body.get("attributeValue") : null;
        Boolean isCore = body.containsKey("isCore") ? (Boolean) body.get("isCore") : null;
        memoryExtractionService.updateMemory(id, newValue, isCore);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(
            @AuthenticationPrincipal com.moodcopilot.entity.UserEntity user, @PathVariable long id) {
        return ApiResponse.ok(memoryMap(memoryOrchestrator.detail(user.getId(), id)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<Void> patch(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    @GetMapping("/{id}/history")
    public ApiResponse<List<Map<String, Object>>> history(
            @AuthenticationPrincipal com.moodcopilot.entity.UserEntity user, @PathVariable long id) {
        return ApiResponse.ok(memoryOrchestrator.history(user.getId(), id).stream().map(this::memoryMap).toList());
    }

    @GetMapping("/{id}/evidence")
    public ApiResponse<List<Map<String, Object>>> evidence(
            @AuthenticationPrincipal com.moodcopilot.entity.UserEntity user, @PathVariable long id) {
        List<com.moodcopilot.entity.UserMemoryEvidenceEntity> evidence = memoryOrchestrator.evidence(user.getId(), id);
        Map<Long, MemoryOrchestrator.DiarySourcePreview> diaryPreviews = memoryOrchestrator.diarySourcePreviews(user.getId(),
                evidence.stream().map(com.moodcopilot.entity.UserMemoryEvidenceEntity::getSourceDiaryId).filter(java.util.Objects::nonNull).distinct().toList());
        return ApiResponse.ok(evidence.stream().map(e -> {
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("id", e.getId());
            item.put("sourceType", e.getSourceType());
            item.put("sourceDiaryId", e.getSourceDiaryId());
            item.put("sourceConversationId", e.getSourceConversationId());
            item.put("evidenceText", e.getEvidenceText());
            item.put("evidenceDate", e.getEvidenceDate());
            item.put("modelConfidence", e.getModelConfidence());
            item.put("evidenceQuality", e.getEvidenceQuality());
            item.put("createdAt", e.getCreatedAt());
            item.put("sourceDiaryPreview", e.getSourceDiaryId() == null ? null : diaryPreviews.get(e.getSourceDiaryId()));
            return item;
        }).toList());
    }

    @GetMapping("/candidates")
    public ApiResponse<List<Map<String, Object>>> candidates(
            @AuthenticationPrincipal com.moodcopilot.entity.UserEntity user,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sort) {
        List<UserMemoryCandidateEntity> candidates = memoryOrchestrator.listCandidates(user.getId(), status, page, size, sort);
        Map<Long, MemoryOrchestrator.SourceSummary> sources = memoryOrchestrator.sourceSummariesForCandidates(
                user.getId(), candidates.stream().map(UserMemoryCandidateEntity::getId).toList());
        Map<Long, MemoryOrchestrator.DiarySourcePreview> diaryPreviews = memoryOrchestrator.diarySourcePreviews(user.getId(),
                sources.values().stream().flatMap(source -> source.diaryIds().stream()).distinct().toList());
        Map<String, Long> groupCounts = candidates.stream().collect(java.util.stream.Collectors.groupingBy(
                candidate -> candidate.getMemoryType() + ":" + candidate.getAttributeKey(), java.util.stream.Collectors.counting()));
        return ApiResponse.ok(candidates.stream().map(candidate -> {
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("id", candidate.getId());
            item.put("attributeKey", candidate.getAttributeKey());
            item.put("attributeValue", candidate.getAttributeValue());
            item.put("memoryType", candidate.getMemoryType());
            item.put("sourceType", candidate.getSourceType());
            item.put("sourceDiaryId", candidate.getSourceDiaryId());
            item.put("sourceConversationId", candidate.getSourceConversationId());
            item.put("confidence", candidate.getConfidence());
            item.put("isCore", candidate.getIsCore());
            item.put("status", candidate.getStatus());
            item.put("evidenceSummary", candidate.getEvidenceSummary());
            item.put("validFrom", candidate.getValidFrom());
            item.put("validUntil", candidate.getValidUntil());
            item.put("updatedAt", candidate.getUpdatedAt());
            item.put("candidateGroupKey", candidate.getMemoryType() + ":" + candidate.getAttributeKey());
            item.put("hasConflict", groupCounts.getOrDefault(candidate.getMemoryType() + ":" + candidate.getAttributeKey(), 0L) > 1);
            MemoryOrchestrator.SourceSummary source = sources.get(candidate.getId());
            item.put("evidenceCount", source == null ? 0 : source.evidenceCount());
            item.put("sourceDiaryIds", source == null ? List.of() : source.diaryIds());
            item.put("sourceDiaryPreviews", sourceDiaryPreviews(source, diaryPreviews));
            item.put("sourceConversationIds", source == null ? List.of() : source.conversationIds());
            item.put("mergedIntoId", candidate.getMergedIntoId());
            item.put("mergeReason", candidate.getMergeReason());
            return item;
        }).toList());
    }

    @GetMapping("/candidates/{id}/evidence")
    public ApiResponse<List<Map<String, Object>>> candidateEvidence(
            @AuthenticationPrincipal com.moodcopilot.entity.UserEntity user, @PathVariable long id) {
        List<com.moodcopilot.entity.UserMemoryEvidenceEntity> evidence = memoryOrchestrator.candidateEvidence(user.getId(), id);
        Map<Long, MemoryOrchestrator.DiarySourcePreview> diaryPreviews = memoryOrchestrator.diarySourcePreviews(user.getId(),
                evidence.stream().map(com.moodcopilot.entity.UserMemoryEvidenceEntity::getSourceDiaryId).filter(java.util.Objects::nonNull).distinct().toList());
        return ApiResponse.ok(evidence.stream().map(e -> {
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("id", e.getId());
            item.put("sourceType", e.getSourceType());
            item.put("sourceDiaryId", e.getSourceDiaryId());
            item.put("sourceConversationId", e.getSourceConversationId());
            item.put("evidenceText", e.getEvidenceText());
            item.put("evidenceDate", e.getEvidenceDate());
            item.put("modelConfidence", e.getModelConfidence());
            item.put("evidenceQuality", e.getEvidenceQuality());
            item.put("createdAt", e.getCreatedAt());
            item.put("sourceDiaryPreview", e.getSourceDiaryId() == null ? null : diaryPreviews.get(e.getSourceDiaryId()));
            return item;
        }).toList());
    }

    @PostMapping("/candidates/{id}/approve")
    public ApiResponse<Void> approve(@AuthenticationPrincipal com.moodcopilot.entity.UserEntity user,
                                     @PathVariable long id) {
        memoryOrchestrator.approveCandidate(user.getId(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/candidates/{id}/reject")
    public ApiResponse<Void> reject(@AuthenticationPrincipal com.moodcopilot.entity.UserEntity user,
                                    @PathVariable long id) {
        memoryOrchestrator.rejectCandidate(user.getId(), id);
        return ApiResponse.ok();
    }

    private Map<String, Object> memoryMap(UserProfileMemoryEntity m) {
        Map<String, Object> item = new java.util.LinkedHashMap<>();
        item.put("id", m.getId());
        item.put("attributeKey", m.getAttributeKey());
        item.put("attributeValue", m.getAttributeValue());
        item.put("memoryType", m.getMemoryType());
        item.put("sourceType", m.getSourceType());
        item.put("sourceDiaryId", m.getSourceDiaryId());
        item.put("sourceConversationId", m.getSourceConversationId());
        item.put("confidence", m.getConfidence());
        item.put("validFrom", m.getValidFrom());
        item.put("validUntil", m.getValidUntil());
        item.put("status", m.getStatus());
        item.put("isCore", m.getIsCore());
        item.put("previousMemoryId", m.getPreviousMemoryId());
        item.put("supersededAt", m.getSupersededAt());
        item.put("supersededReason", m.getSupersededReason());
        item.put("lastEvidenceAt", m.getLastEvidenceAt());
        item.put("updatedAt", m.getUpdatedAt() != null ? m.getUpdatedAt() : m.getUpdateTime());
        return item;
    }

    private List<MemoryOrchestrator.DiarySourcePreview> sourceDiaryPreviews(
            MemoryOrchestrator.SourceSummary source,
            Map<Long, MemoryOrchestrator.DiarySourcePreview> previews) {
        if (source == null || source.diaryIds().isEmpty()) return List.of();
        return source.diaryIds().stream()
                .map(id -> memoryOrchestrator.withEvidenceDate(previews.get(id), source.diaryEvidenceDates().get(id)))
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
