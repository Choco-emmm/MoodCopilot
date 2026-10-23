package com.moodcopilot.entity;

import com.baomidou.mybatisplus.annotation.*;

/** Content-free approval receipt; raw proposals remain in the existing run event stream. */
@TableName("memory_change_commands")
public class MemoryChangeCommandEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private String runId;
    private String toolCallId;
    private String attributeKey;
    private Long targetMemoryId;
    private String expectedValueHash;
    private String proposedValueHash;
    private String approvedValueHash;
    private String status;
    private String reasonCode;
    private Long resultMemoryId;
    private Boolean indexPending;
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { this.userId = value; }
    public String getRunId() { return runId; }
    public void setRunId(String value) { this.runId = value; }
    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String value) { this.toolCallId = value; }
    public String getAttributeKey() { return attributeKey; }
    public void setAttributeKey(String value) { this.attributeKey = value; }
    public Long getTargetMemoryId() { return targetMemoryId; }
    public void setTargetMemoryId(Long value) { this.targetMemoryId = value; }
    public String getExpectedValueHash() { return expectedValueHash; }
    public void setExpectedValueHash(String value) { this.expectedValueHash = value; }
    public String getProposedValueHash() { return proposedValueHash; }
    public void setProposedValueHash(String value) { this.proposedValueHash = value; }
    public String getApprovedValueHash() { return approvedValueHash; }
    public void setApprovedValueHash(String value) { this.approvedValueHash = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String value) { this.reasonCode = value; }
    public Long getResultMemoryId() { return resultMemoryId; }
    public void setResultMemoryId(Long value) { this.resultMemoryId = value; }
    public Boolean getIndexPending() { return indexPending; }
    public void setIndexPending(Boolean value) { this.indexPending = value; }
}
