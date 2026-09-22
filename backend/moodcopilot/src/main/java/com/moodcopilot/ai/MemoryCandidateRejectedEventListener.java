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

    public MemoryCandidateRejectedEventListener(DiaryMapper diaryMapper,
            MemoryExtractionService memoryExtractionService,
            @org.springframework.context.annotation.Lazy MemoryOrchestrator memoryOrchestrator) {
        this.diaryMapper = diaryMapper;
        this.memoryExtractionService = memoryExtractionService;
        this.memoryOrchestrator = memoryOrchestrator;
    }

    @EventListener
    @Async
    public void onMemoryCandidateRejected(MemoryCandidateRejectedEvent event) {
        if (event.getSourceDiaryId() == null || event.getReason() == null || event.getReason().isBlank() || "USER_REJECTED".equals(event.getReason())) {
            return;
        }
        
        DiaryEntity diary = diaryMapper.selectById(event.getSourceDiaryId());
        if (diary == null) {
            return;
        }

        log.info("User {} rejected memory candidate {} with reason: {}. Re-extracting...", 
                event.getUserId(), event.getCandidateId(), event.getReason());

        String hint = "【用户修改意见】用户刚刚拒绝了上一版提取的长期记忆（原内容：“" + event.getOriginalText() + "”），"
                + "原因是：“" + event.getReason() + "”。请结合用户的意见，重新从日记中提取修正后的记忆，不能再犯相同的错误。如果不需要提取任何新记忆则返回空数组。";
                
        // Extract memory with hint
        List<MemoryExtractionService.MemoryAttribute> attributes = memoryExtractionService.extractMemoryFromDiary(
                diary.getContent(), "diary_post_process", event.getUserId(), diary.getId(), hint);

        if (attributes != null && !attributes.isEmpty()) {
            memoryOrchestrator.processExtractedMemories(event.getUserId(), attributes,
                    "explicit", diary.getId(), null, null, null);
        }
    }
}
