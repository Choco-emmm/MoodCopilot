package com.moodcopilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("life_chapter_diaries")
public class LifeChapterDiaryEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long chapterId;
    private Long diaryId;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public Long getDiaryId() { return diaryId; }
    public void setDiaryId(Long diaryId) { this.diaryId = diaryId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
