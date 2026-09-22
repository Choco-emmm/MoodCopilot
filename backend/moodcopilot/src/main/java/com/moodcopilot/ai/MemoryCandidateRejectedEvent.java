package com.moodcopilot.ai;

import org.springframework.context.ApplicationEvent;

public class MemoryCandidateRejectedEvent extends ApplicationEvent {
    private final long userId;
    private final long candidateId;
    private final Long sourceDiaryId;
    private final Long sourceConversationId;
    private final String reason;
    private final String originalText;

    public MemoryCandidateRejectedEvent(Object source, long userId, long candidateId, Long sourceDiaryId, Long sourceConversationId, String reason, String originalText) {
        super(source);
        this.userId = userId;
        this.candidateId = candidateId;
        this.sourceDiaryId = sourceDiaryId;
        this.sourceConversationId = sourceConversationId;
        this.reason = reason;
        this.originalText = originalText;
    }

    public long getUserId() { return userId; }
    public long getCandidateId() { return candidateId; }
    public Long getSourceDiaryId() { return sourceDiaryId; }
    public Long getSourceConversationId() { return sourceConversationId; }
    public String getReason() { return reason; }
    public String getOriginalText() { return originalText; }
}
