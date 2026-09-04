package com.moodcopilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("user_life_chapter_source_moves")
public class UserLifeChapterSourceMoveEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String sourceType;
    private Long sourceId;
    private Long fromChapterId;
    private Long toChapterId;
    private String reason;
    private LocalDateTime createdAt;
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { userId = value; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String value) { sourceType = value; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long value) { sourceId = value; }
    public Long getFromChapterId() { return fromChapterId; }
    public void setFromChapterId(Long value) { fromChapterId = value; }
    public Long getToChapterId() { return toChapterId; }
    public void setToChapterId(Long value) { toChapterId = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
}
