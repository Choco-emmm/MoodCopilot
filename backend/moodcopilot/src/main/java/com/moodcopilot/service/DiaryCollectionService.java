package com.moodcopilot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.moodcopilot.dto.AddDiaryToCollectionRequest;
import com.moodcopilot.dto.CreateCollectionRequest;
import com.moodcopilot.dto.UpdateCollectionRequest;
import com.moodcopilot.dto.UpdateDiarySortRequest;
import com.moodcopilot.entity.DiaryCollectionEntity;
import com.moodcopilot.entity.DiaryCollectionRelationEntity;
import com.moodcopilot.entity.DiaryEntity;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.mapper.DiaryCollectionMapper;
import com.moodcopilot.mapper.DiaryCollectionRelationMapper;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.mapper.UserMapper;
import com.moodcopilot.view.CollectionDiaryView;
import com.moodcopilot.view.DiaryCollectionView;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DiaryCollectionService extends ServiceImpl<DiaryCollectionMapper, DiaryCollectionEntity> {

    private final DiaryCollectionRelationMapper relationMapper;
    private final DiaryMapper diaryMapper;
    private final UserMapper userMapper;

    public DiaryCollectionService(DiaryCollectionRelationMapper relationMapper,
                                DiaryMapper diaryMapper,
                                UserMapper userMapper) {
        this.relationMapper = relationMapper;
        this.diaryMapper = diaryMapper;
        this.userMapper = userMapper;
    }

    /**
     * 创建合集
     */
    @Transactional
    public DiaryCollectionView createCollection(CreateCollectionRequest request) {
        UserEntity currentUser = getCurrentUser();

        DiaryCollectionEntity collection = new DiaryCollectionEntity();
        collection.setUserId(currentUser.getId());
        collection.setName(request.name());
        collection.setDescription(request.description());
        collection.setCoverUrl(request.coverUrl());
        collection.setVisibility(request.visibility() != null ? request.visibility() : "PUBLIC");
        collection.setCreateTime(LocalDateTime.now());
        collection.setUpdateTime(LocalDateTime.now());

        save(collection);
        return DiaryCollectionView.from(collection);
    }

    /**
     * 获取单个合集详情（含权限过滤）
     */
    public DiaryCollectionView getCollection(Long collectionId) {
        DiaryCollectionEntity collection = getById(collectionId);
        if (collection == null) {
            throw new IllegalArgumentException("合集不存在");
        }

        UserEntity currentUser = getCurrentUser();
        boolean isOwner = currentUser != null && collection.getUserId().equals(currentUser.getId());

        // 非所有者只能看公开合集
        if (!isOwner && !"PUBLIC".equals(collection.getVisibility())) {
            throw new SecurityException("无权访问此合集");
        }

        return DiaryCollectionView.from(collection);
    }

    /**
     * 更新合集
     */
    @Transactional
    public DiaryCollectionView updateCollection(Long collectionId, UpdateCollectionRequest request) {
        UserEntity currentUser = getCurrentUser();
        DiaryCollectionEntity collection = getById(collectionId);

        if (collection == null) {
            throw new IllegalArgumentException("合集不存在");
        }

        // 检查权限
        if (!collection.getUserId().equals(currentUser.getId())) {
            throw new SecurityException("无权操作此合集");
        }

        collection.setName(request.name());
        collection.setDescription(request.description());
        collection.setCoverUrl(request.coverUrl());
        collection.setVisibility(request.visibility());
        collection.setUpdateTime(LocalDateTime.now());

        updateById(collection);
        return DiaryCollectionView.from(collection);
    }

    /**
     * 删除合集
     */
    @Transactional
    public void deleteCollection(Long collectionId) {
        UserEntity currentUser = getCurrentUser();
        DiaryCollectionEntity collection = getById(collectionId);

        if (collection == null) {
            throw new IllegalArgumentException("合集不存在");
        }

        // 检查权限
        if (!collection.getUserId().equals(currentUser.getId())) {
            throw new SecurityException("无权操作此合集");
        }

        // 级联删除关系表中的记录
        LambdaQueryWrapper<DiaryCollectionRelationEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DiaryCollectionRelationEntity::getCollectionId, collectionId);
        relationMapper.delete(queryWrapper);

        // 删除合集
        removeById(collectionId);
    }

    /**
     * 添加日记到合集
     */
    @Transactional
    public void addDiariesToCollection(Long collectionId, AddDiaryToCollectionRequest request) {
        UserEntity currentUser = getCurrentUser();
        DiaryCollectionEntity collection = getById(collectionId);

        if (collection == null) {
            throw new IllegalArgumentException("合集不存在");
        }

        // 检查权限
        if (!collection.getUserId().equals(currentUser.getId())) {
            throw new SecurityException("无权操作此合集");
        }

        // 批量添加日记
        for (Long diaryId : request.diaryIds()) {
            // 检查日记是否存在且属于当前用户
            DiaryEntity diary = diaryMapper.selectById(diaryId);
            if (diary == null) {
                continue; // 跳过不存在的日记
            }

            // 校验日记所有权
            if (!diary.getAuthorUserId().equals(currentUser.getId())) {
                throw new SecurityException("只能添加自己的日记到合集");
            }

            // 私密日记不可加入公开合集，静默跳过
            if ("PUBLIC".equals(collection.getVisibility()) && "PRIVATE".equals(diary.getVisibility())) {
                continue;
            }

            // 检查是否已存在（避免重复添加）
            LambdaQueryWrapper<DiaryCollectionRelationEntity> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(DiaryCollectionRelationEntity::getCollectionId, collectionId)
                       .eq(DiaryCollectionRelationEntity::getDiaryId, diaryId);

            long count = relationMapper.selectCount(queryWrapper);
            if (count > 0) {
                continue; // 已存在，跳过
            }

            // 创建关系
            DiaryCollectionRelationEntity relation = new DiaryCollectionRelationEntity();
            relation.setCollectionId(collectionId);
            relation.setDiaryId(diaryId);
            relation.setSortOrder((double) System.currentTimeMillis()); // 使用时间戳作为初始排序值
            relation.setCreateTime(LocalDateTime.now());

            relationMapper.insert(relation);
        }
    }

    /**
     * 从合集移除日记
     */
    @Transactional
    public void removeDiariesFromCollection(Long collectionId, List<Long> diaryIds) {
        UserEntity currentUser = getCurrentUser();
        DiaryCollectionEntity collection = getById(collectionId);

        if (collection == null) {
            throw new IllegalArgumentException("合集不存在");
        }

        // 检查权限
        if (!collection.getUserId().equals(currentUser.getId())) {
            throw new SecurityException("无权操作此合集");
        }

        // 批量删除关系
        LambdaQueryWrapper<DiaryCollectionRelationEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DiaryCollectionRelationEntity::getCollectionId, collectionId)
                   .in(DiaryCollectionRelationEntity::getDiaryId, diaryIds);

        relationMapper.delete(queryWrapper);
    }

    /**
     * 查询用户的合集列表（分页）
     */
    public IPage<DiaryCollectionView> getUserCollections(Long userId, int page, int size) {
        // 如果查询的是别人的合集，只能查公开的
        UserEntity currentUser = getCurrentUser();
        String visibility = (currentUser == null || !currentUser.getId().equals(userId)) ? "PUBLIC" : null;

        Page<DiaryCollectionEntity> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<DiaryCollectionEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DiaryCollectionEntity::getUserId, userId);

        if (visibility != null) {
            queryWrapper.eq(DiaryCollectionEntity::getVisibility, visibility);
        }

        queryWrapper.orderByDesc(DiaryCollectionEntity::getCreateTime);

        IPage<DiaryCollectionEntity> result = page(pageParam, queryWrapper);
        return result.convert(DiaryCollectionView::from);
    }

    /**
     * 查询合集内的日记列表（分页与排序）
     */
    public IPage<CollectionDiaryView> getCollectionDiaries(Long collectionId, int page, int size, String sortBy) {
        DiaryCollectionEntity collection = getById(collectionId);
        if (collection == null) {
            throw new IllegalArgumentException("合集不存在");
        }

        Page<DiaryEntity> pageParam = new Page<>(page, size);

        // 使用自定义 SQL 查询，支持跨表排序
        IPage<DiaryEntity> diaryPage = diaryMapper.selectDiariesByCollectionId(pageParam, collectionId, sortBy);

        // 批量获取排序值
        java.util.Map<Long, Double> sortOrderMap = new java.util.HashMap<>();
        java.util.List<Long> diaryIds = diaryPage.getRecords().stream()
                .map(com.moodcopilot.entity.DiaryEntity::getId)
                .collect(java.util.stream.Collectors.toList());
        if (!diaryIds.isEmpty()) {
            LambdaQueryWrapper<DiaryCollectionRelationEntity> qw = new LambdaQueryWrapper<>();
            qw.eq(DiaryCollectionRelationEntity::getCollectionId, collectionId)
              .in(DiaryCollectionRelationEntity::getDiaryId, diaryIds);
            java.util.List<DiaryCollectionRelationEntity> relations = relationMapper.selectList(qw);
            for (DiaryCollectionRelationEntity r : relations) {
                sortOrderMap.put(r.getDiaryId(), r.getSortOrder());
            }
        }

        UserEntity currentUser = getCurrentUser();
        java.util.Map<Long, Double> finalSortOrderMap = sortOrderMap;

        IPage<CollectionDiaryView> result = diaryPage.convert(diary -> {
            if (diary == null) {
                return null;
            }

            // 权限过滤：如果是查看别人的合集，过滤掉私密日记
            if (currentUser != null && !collection.getUserId().equals(currentUser.getId())
                && "PRIVATE".equals(diary.getVisibility())) {
                return null;
            }

            Double sortOrder = finalSortOrderMap.get(diary.getId());
            return CollectionDiaryView.from(diary, null, false, sortOrder);
        });

        // 过滤掉因权限问题产生的 null 记录
        result.setRecords(result.getRecords().stream()
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toList()));

        return result;
    }

    /**
     * 更新日记在合集中的排序顺序
     */
    @Transactional
    public void updateDiarySortOrder(Long collectionId, Long diaryId, Double prevSortOrder, Double nextSortOrder) {
        // 计算新的排序值
        Double newSortOrder;
        if (prevSortOrder == null) {
            // 拖到最顶部，next + 100000.0
            newSortOrder = nextSortOrder + 100000.0;
        } else if (nextSortOrder == null) {
            // 拖到最底部，prev - 100000.0
            newSortOrder = prevSortOrder - 100000.0;
        } else {
            // 中间，(prev + next) / 2.0
            newSortOrder = (prevSortOrder + nextSortOrder) / 2.0;
        }

        // 更新排序值
        relationMapper.updateSortOrder(collectionId, diaryId, newSortOrder);
    }

    /**
     * 检查日记是否在指定合集中
     */
    public boolean isDiaryInCollection(Long collectionId, Long diaryId) {
        LambdaQueryWrapper<DiaryCollectionRelationEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DiaryCollectionRelationEntity::getCollectionId, collectionId)
                   .eq(DiaryCollectionRelationEntity::getDiaryId, diaryId);
        return relationMapper.selectCount(queryWrapper) > 0;
    }

    /**
     * 获取日记所属的合集列表（公开/私密根据查看者权限过滤）
     */
    public List<DiaryCollectionView> getCollectionsForDiary(Long diaryId) {
        UserEntity currentUser = getCurrentUser();

        // 查询所有关联关系
        LambdaQueryWrapper<DiaryCollectionRelationEntity> relWrapper = new LambdaQueryWrapper<>();
        relWrapper.eq(DiaryCollectionRelationEntity::getDiaryId, diaryId);
        List<DiaryCollectionRelationEntity> relations = relationMapper.selectList(relWrapper);

        if (relations.isEmpty()) {
            return List.of();
        }

        List<Long> collectionIds = relations.stream()
                .map(DiaryCollectionRelationEntity::getCollectionId)
                .collect(Collectors.toList());

        // 查询合集
        List<DiaryCollectionEntity> collections = listByIds(collectionIds);

        // 过滤：本人看自己的日记 → 显示所有合集；他人看 → 只显示公开合集
        return collections.stream()
                .filter(c -> currentUser != null && c.getUserId().equals(currentUser.getId())
                        || "PUBLIC".equals(c.getVisibility()))
                .map(DiaryCollectionView::from)
                .collect(Collectors.toList());
    }

    /**
     * 获取当前用户
     */
    private UserEntity getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserEntity) {
            return (UserEntity) authentication.getPrincipal();
        }
        return null;
    }
}