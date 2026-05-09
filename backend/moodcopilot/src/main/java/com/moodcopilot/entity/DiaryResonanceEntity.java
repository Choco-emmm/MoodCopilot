package com.moodcopilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("diary_resonances")
public class DiaryResonanceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long diaryId;
    private Long userId;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getDiaryId() { return diaryId; }
    public void setDiaryId(Long diaryId) { this.diaryId = diaryId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    private String message;

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
