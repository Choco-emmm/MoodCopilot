package com.moodcopilot.diary;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.moodcopilot.common.ApiResponse;
import com.moodcopilot.dto.AddDiaryToCollectionRequest;
import com.moodcopilot.dto.CreateCollectionRequest;
import com.moodcopilot.dto.UpdateCollectionRequest;
import com.moodcopilot.dto.UpdateDiarySortRequest;
import com.moodcopilot.service.DiaryCollectionService;
import com.moodcopilot.view.CollectionDiaryView;
import com.moodcopilot.view.DiaryCollectionView;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/collections")
public class DiaryCollectionController {

    private final DiaryCollectionService collectionService;

    public DiaryCollectionController(DiaryCollectionService collectionService) {
        this.collectionService = collectionService;
    }

    /**
     * 创建合集
     */
    @PostMapping
    public ApiResponse<DiaryCollectionView> createCollection(@RequestBody CreateCollectionRequest request) {
        DiaryCollectionView collection = collectionService.createCollection(request);
        return ApiResponse.ok(collection);
    }

    /**
     * 获取单个合集详情
     */
    @GetMapping("/{collectionId}")
    public ApiResponse<DiaryCollectionView> getCollection(@PathVariable Long collectionId) {
        DiaryCollectionView collection = collectionService.getCollection(collectionId);
        return ApiResponse.ok(collection);
    }

    /**
     * 获取当前用户的合集列表
     */
    @GetMapping("/mine")
    public ApiResponse<IPage<DiaryCollectionView>> myCollections(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        // 获取当前用户ID
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof com.moodcopilot.entity.UserEntity)) {
            throw new SecurityException("用户未登录");
        }
        com.moodcopilot.entity.UserEntity user = (com.moodcopilot.entity.UserEntity) auth.getPrincipal();

        IPage<DiaryCollectionView> collections = collectionService.getUserCollections(user.getId(), page, size);
        return ApiResponse.ok(collections);
    }

    /**
     * 获取指定用户的公开合集列表
     */
    @GetMapping("/user/{userId}")
    public ApiResponse<IPage<DiaryCollectionView>> userCollections(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        IPage<DiaryCollectionView> collections = collectionService.getUserCollections(userId, page, size);
        return ApiResponse.ok(collections);
    }

    /**
     * 更新合集
     */
    @PutMapping("/{collectionId}")
    public ApiResponse<DiaryCollectionView> updateCollection(
            @PathVariable Long collectionId,
            @RequestBody UpdateCollectionRequest request) {

        DiaryCollectionView collection = collectionService.updateCollection(collectionId, request);
        return ApiResponse.ok(collection);
    }

    /**
     * 删除合集
     */
    @DeleteMapping("/{collectionId}")
    public ApiResponse<Void> deleteCollection(@PathVariable Long collectionId) {
        collectionService.deleteCollection(collectionId);
        return ApiResponse.ok();
    }

    /**
     * 添加日记到合集
     */
    @PostMapping("/{collectionId}/diaries")
    public ApiResponse<Void> addDiariesToCollection(
            @PathVariable Long collectionId,
            @RequestBody AddDiaryToCollectionRequest request) {

        collectionService.addDiariesToCollection(collectionId, request);
        return ApiResponse.ok();
    }

    /**
     * 从合集移除日记
     */
    @DeleteMapping("/{collectionId}/diaries")
    public ApiResponse<Void> removeDiariesFromCollection(
            @PathVariable Long collectionId,
            @RequestParam List<Long> diaryIds) {

        collectionService.removeDiariesFromCollection(collectionId, diaryIds);
        return ApiResponse.ok();
    }

    /**
     * 获取合集内的日记列表
     */
    @GetMapping("/{collectionId}/diaries")
    public ApiResponse<IPage<CollectionDiaryView>> getCollectionDiaries(
            @PathVariable Long collectionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "ADDED_TIME_DESC") String sortBy) {

        IPage<CollectionDiaryView> diaries = collectionService.getCollectionDiaries(
                collectionId, page, size, sortBy);
        return ApiResponse.ok(diaries);
    }

    /**
     * 检查日记是否在指定合集中
     */
    @GetMapping("/{collectionId}/diaries/{diaryId}/exists")
    public ApiResponse<Boolean> checkDiaryExists(
            @PathVariable Long collectionId,
            @PathVariable Long diaryId) {

        // 这里简单实现，实际可以根据需要优化
        // 例如直接查询关系表是否存在记录
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof com.moodcopilot.entity.UserEntity)) {
            throw new SecurityException("用户未登录");
        }

        // 查询关系表检查是否存在
        boolean exists = collectionService.isDiaryInCollection(collectionId, diaryId);
        return ApiResponse.ok(exists);
    }

    /**
     * 更新日记在合集中的排序顺序
     */
    @PutMapping("/{collectionId}/diaries/{diaryId}/sort")
    public ApiResponse<Void> updateDiarySortOrder(
            @PathVariable Long collectionId,
            @PathVariable Long diaryId,
            @RequestBody UpdateDiarySortRequest request) {

        collectionService.updateDiarySortOrder(collectionId, diaryId, request.getPrevSortOrder(), request.getNextSortOrder());
        return ApiResponse.ok();
    }

    /**
     * 获取日记所属的合集列表
     */
    @GetMapping("/by-diary/{diaryId}")
    public ApiResponse<List<DiaryCollectionView>> getCollectionsForDiary(@PathVariable Long diaryId) {
        List<DiaryCollectionView> collections = collectionService.getCollectionsForDiary(diaryId);
        return ApiResponse.ok(collections);
    }
}