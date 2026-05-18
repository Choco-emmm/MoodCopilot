package com.moodcopilot.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moodcopilot.ai.RagMemoryService;
import com.moodcopilot.ai.RagMemoryService.BatchIndexItem;
import com.moodcopilot.common.ApiResponse;
import com.moodcopilot.entity.DiaryEntity;
import com.moodcopilot.entity.UserProfileMemoryEntity;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.mapper.UserProfileMemoryMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    private final AdminReportService adminReportService;
    private final DiaryMapper diaryMapper;
    private final UserProfileMemoryMapper userProfileMemoryMapper;
    private final RagMemoryService ragMemoryService;

    public AdminReportController(AdminReportService adminReportService,
            DiaryMapper diaryMapper,
            UserProfileMemoryMapper userProfileMemoryMapper,
            RagMemoryService ragMemoryService) {
        this.adminReportService = adminReportService;
        this.diaryMapper = diaryMapper;
        this.userProfileMemoryMapper = userProfileMemoryMapper;
        this.ragMemoryService = ragMemoryService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<AdminReportView> reportPage = adminReportService.list(status, page, size);
        return ApiResponse.ok(Map.of(
                "items", reportPage.getRecords(),
                "total", reportPage.getTotal(),
                "page", reportPage.getCurrent(),
                "size", reportPage.getSize()
        ));
    }

    @PostMapping("/{id}/resolve")
    public ApiResponse<Void> resolve(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        adminReportService.resolve(id, note(body));
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<Void> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        adminReportService.reject(id, note(body));
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/hide-target")
    public ApiResponse<Void> hideTarget(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        adminReportService.hideTarget(id, note(body));
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/ban-user")
    public ApiResponse<Void> banUser(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        adminReportService.banUserAndHideTarget(id, note(body));
        return ApiResponse.ok();
    }

    private String note(Map<String, String> body) {
        return body == null ? null : body.get("note");
    }

    /**
     * 批量回填所有已有日记到 RAG 向量库。只索引有内容且未删除的日记。
     */
    @PostMapping("/rag/reindex")
    public ApiResponse<Map<String, Object>> reindexRag() {
        List<DiaryEntity> diaries = diaryMapper.selectList(
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getIsDeleted, false)
                        .isNotNull(DiaryEntity::getContent)
                        .ne(DiaryEntity::getContent, ""));
        List<BatchIndexItem> items = diaries.stream()
                .map(d -> new BatchIndexItem(d.getAuthorUserId(), d.getId(), d.getContent()))
                .toList();
        int count = ragMemoryService.batchIndexDiaries(items);
        return ApiResponse.ok(Map.of("total", diaries.size(), "indexed", count));
    }

    /**
     * 批量回填所有用户长期画像到 RAG 向量库。
     */
    @PostMapping("/rag/reindex-memories")
    public ApiResponse<Map<String, Object>> reindexMemoriesRag() {
        List<UserProfileMemoryEntity> allMemories = userProfileMemoryMapper.selectList(
                new LambdaQueryWrapper<>());
        Map<Long, List<UserProfileMemoryEntity>> grouped = allMemories.stream()
                .collect(java.util.stream.Collectors.groupingBy(UserProfileMemoryEntity::getUserId));
        int count = ragMemoryService.batchIndexProfiles(grouped);
        return ApiResponse.ok(Map.of("users", count, "total_memories", allMemories.size()));
    }
}
