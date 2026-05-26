package com.moodcopilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("diary_knowledge_graph")
public class DiaryKnowledgeGraphEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long diaryId;

    private String headEntity;

    private String relation;

    private String tailEntity;

    private Integer tailPolarity;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getDiaryId() {
        return diaryId;
    }

    public void setDiaryId(Long diaryId) {
        this.diaryId = diaryId;
    }

    public String getHeadEntity() {
        return headEntity;
    }

    public void setHeadEntity(String headEntity) {
        this.headEntity = headEntity;
    }

    public String getRelation() {
        return relation;
    }

    public void setRelation(String relation) {
        this.relation = relation;
    }

    public String getTailEntity() {
        return tailEntity;
    }

    public void setTailEntity(String tailEntity) {
        this.tailEntity = tailEntity;
    }

    public Integer getTailPolarity() {
        return tailPolarity;
    }

    public void setTailPolarity(Integer tailPolarity) {
        this.tailPolarity = tailPolarity;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
