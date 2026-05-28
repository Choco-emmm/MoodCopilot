package com.moodcopilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("diary_collection_relation")
public class DiaryCollectionRelationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long collectionId;
    private Long diaryId;
    private Double sortOrder;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCollectionId() { return collectionId; }
    public void setCollectionId(Long collectionId) { this.collectionId = collectionId; }

    public Long getDiaryId() { return diaryId; }
    public void setDiaryId(Long diaryId) { this.diaryId = diaryId; }

    public Double getSortOrder() { return sortOrder; }
    public void setSortOrder(Double sortOrder) { this.sortOrder = sortOrder; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}