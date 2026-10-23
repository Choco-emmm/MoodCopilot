package com.moodcopilot.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.entity.DiaryEntity;
import java.util.List;

@Component
public class MemoryCandidateRejectedEventListener {
    private static final Logger log = LoggerFactory.getLogger(MemoryCandidateRejectedEventListener.class);

    private final DiaryMapper diaryMapper;
    private final MemoryExtractionService memoryExtractionService;
    private final MemoryOrchestrator memoryOrchestrator;
    private final ChatService chatService;

    public MemoryCandidateRejectedEventListener(DiaryMapper diaryMapper,
            MemoryExtractionService memoryExtractionService,
            @org.springframework.context.annotation.Lazy ChatService chatService,
            @org.springframework.context.annotation.Lazy MemoryOrchestrator memoryOrchestrator) {
        this.diaryMapper = diaryMapper;
        this.memoryExtractionService = memoryExtractionService;
        this.memoryOrchestrator = memoryOrchestrator;
        this.chatService = chatService;
    }

    
    @EventListener
    @Async
    public void onMemoryCandidateRejected(MemoryCandidateRejectedEvent event) {
        if (event.getReason() == null || event.getReason().isBlank() || "USER_REJECTED".equals(event.getReason())) {
            return;
        }
        
        String contextContent = null;
        String sourceType = "chat_candidate";
        Long sourceId = null;

        if (event.getSourceDiaryId() != null) {
            DiaryEntity diary = diaryMapper.selectById(event.getSourceDiaryId());
            if (diary != null && Long.valueOf(event.getUserId()).equals(diary.getAuthorUserId()) && !Boolean.TRUE.equals(diary.getIsDeleted())) {
                contextContent = diary.getContent();
                sourceType = "diary_inferred";
                sourceId = diary.getId();
            }
        } else if (event.getSourceConversationId() != null) {
            try {
                contextContent = chatService.loadUserEvidenceForMemory(event.getUserId(), event.getSourceConversationId());
                sourceId = event.getSourceConversationId();
            } catch (Exception e) {
                log.warn("记忆重提取读取来源失败 userId={} errorType={}", event.getUserId(), e.getClass().getSimpleName());
            }
        }

        if (contextContent == null || contextContent.isBlank()) {
            return;
        }

        log.info("候选拒绝后重新提取 userId={} candidateId={} reasonLength={}",
                event.getUserId(), event.getCandidateId(), event.getReason().length());

        String hint = "【用户修改意见】用户刚刚拒绝了上一版提取的长期记忆（原内容：“" + event.getOriginalText() + "”），"
                + "原因是：“" + event.getReason() + "”。请结合用户的意见，重新从上下文中提取修正后的记忆，不能再犯相同的错误。如果不需要提取任何新记忆则返回空数组。";
                
        // Extract memory with hint
        List<MemoryExtractionService.MemoryAttribute> attributes = memoryExtractionService.extractMemoryFromContext(
                contextContent, sourceType, event.getUserId(), sourceId, hint);

        if (attributes != null && !attributes.isEmpty()) {
            Long diaryId = "diary_inferred".equals(sourceType) ? sourceId : null;
            Long convId = "chat_candidate".equals(sourceType) ? sourceId : null;
            memoryOrchestrator.processExtractedMemories(event.getUserId(), attributes,
                    sourceType, diaryId, convId, contextContent, null);
        }
    }

}
