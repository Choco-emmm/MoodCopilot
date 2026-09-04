package com.moodcopilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("user_life_chapter_version_sources")
public class UserLifeChapterVersionSourceEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long versionId;
    private String sourceType;
    private Long sourceId;
    private LocalDateTime createdAt;
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getVersionId() { return versionId; }
    public void setVersionId(Long value) { versionId = value; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String value) { sourceType = value; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long value) { sourceId = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
}
