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

    public UserProfileMemoryController(MemoryExtractionService memoryExtractionService) {
        this.memoryExtractionService = memoryExtractionService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        List<UserProfileMemoryEntity> memories = memoryExtractionService.listCurrentUserMemories();
        List<Map<String, Object>> result = memories.stream()
                .map(m -> Map.<String, Object>of(
                        "id", m.getId(),
                        "attributeKey", m.getAttributeKey(),
                        "attributeValue", m.getAttributeValue()))
                .toList();
        return ApiResponse.ok(result);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        memoryExtractionService.deleteMemory(id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable long id, @RequestBody Map<String, String> body) {
        String newValue = body.get("attributeValue");
        memoryExtractionService.updateMemory(id, newValue);
        return ApiResponse.ok(null);
    }
}
