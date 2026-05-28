package com.moodcopilot.view;

import com.moodcopilot.entity.DiaryCollectionEntity;

import java.time.LocalDateTime;

public record DiaryCollectionView(
        Long id,
        Long userId,
        String name,
        String description,
        String coverUrl,
        String visibility,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {

    public static DiaryCollectionView from(DiaryCollectionEntity entity) {
        return new DiaryCollectionView(
                entity.getId(),
                entity.getUserId(),
                entity.getName(),
                entity.getDescription(),
                entity.getCoverUrl(),
                entity.getVisibility(),
                entity.getCreateTime(),
                entity.getUpdateTime()
        );
    }
}