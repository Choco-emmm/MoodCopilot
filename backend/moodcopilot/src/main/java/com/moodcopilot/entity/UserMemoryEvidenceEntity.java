package com.moodcopilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("user_memory_evidence")
public class UserMemoryEvidenceEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long memoryId;
    private Long candidateId;
    private String sourceType;
    private Long sourceDiaryId;
    private Long sourceConversationId;
    private String evidenceText;
    private LocalDate evidenceDate;
    private Double modelConfidence;
    private Double evidenceQuality;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getMemoryId() { return memoryId; }
    public void setMemoryId(Long memoryId) { this.memoryId = memoryId; }
    public Long getCandidateId() { return candidateId; }
    public void setCandidateId(Long candidateId) { this.candidateId = candidateId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getSourceDiaryId() { return sourceDiaryId; }
    public void setSourceDiaryId(Long sourceDiaryId) { this.sourceDiaryId = sourceDiaryId; }
    public Long getSourceConversationId() { return sourceConversationId; }
    public void setSourceConversationId(Long sourceConversationId) { this.sourceConversationId = sourceConversationId; }
    public String getEvidenceText() { return evidenceText; }
    public void setEvidenceText(String evidenceText) { this.evidenceText = evidenceText; }
    public LocalDate getEvidenceDate() { return evidenceDate; }
    public void setEvidenceDate(LocalDate evidenceDate) { this.evidenceDate = evidenceDate; }
    public Double getModelConfidence() { return modelConfidence; }
    public void setModelConfidence(Double modelConfidence) { this.modelConfidence = modelConfidence; }
    public Double getEvidenceQuality() { return evidenceQuality; }
    public void setEvidenceQuality(Double evidenceQuality) { this.evidenceQuality = evidenceQuality; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
