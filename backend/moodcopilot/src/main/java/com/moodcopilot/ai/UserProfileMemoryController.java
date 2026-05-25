package com.moodcopilot.ai;

import com.moodcopilot.common.ApiResponse;
import com.moodcopilot.entity.UserProfileMemoryEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/memory")
public class UserProfileMemoryController {

    private final MemoryExtractionService memoryExtractionService;
    private final MemoryConsolidationService memoryConsolidationService;

    public UserProfileMemoryController(MemoryExtractionService memoryExtractionService,
                                       MemoryConsolidationService memoryConsolidationService) {
        this.memoryExtractionService = memoryExtractionService;
        this.memoryConsolidationService = memoryConsolidationService;
    }

    @PostMapping("/consolidate/preview")
    public ApiResponse<List<MemoryExtractionService.MemoryAttribute>> previewConsolidate() {
        var user = (com.moodcopilot.entity.UserEntity) org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.ok(memoryConsolidationService.previewConsolidation(user.getId()));
    }

    @PostMapping("/consolidate/apply")
    public ApiResponse<Void> applyConsolidate(@RequestBody List<MemoryExtractionService.MemoryAttribute> attributes) {
        var user = (com.moodcopilot.entity.UserEntity) org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        memoryConsolidationService.applyConsolidation(user.getId(), attributes);
        return ApiResponse.ok(null);
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        List<UserProfileMemoryEntity> memories = memoryExtractionService.listCurrentUserMemories();
        List<Map<String, Object>> result = memories.stream()
                .map(m -> {
                    Map<String, Object> map = new java.util.LinkedHashMap<>();
                    map.put("id", m.getId());
                    map.put("attributeKey", m.getAttributeKey());
                    map.put("attributeValue", m.getAttributeValue());
                    map.put("isCore", Boolean.TRUE.equals(m.getIsCore()));
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
}
