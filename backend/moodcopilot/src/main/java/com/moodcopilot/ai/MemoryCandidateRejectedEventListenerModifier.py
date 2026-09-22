import re

with open("backend/moodcopilot/src/main/java/com/moodcopilot/ai/MemoryCandidateRejectedEventListener.java", "r", encoding="utf-8") as f:
    code = f.read()

if "ChatService chatService" not in code:
    code = code.replace("private final MemoryOrchestrator memoryOrchestrator;", "private final MemoryOrchestrator memoryOrchestrator;\n    private final ChatService chatService;")
    code = code.replace("MemoryExtractionService memoryExtractionService,", "MemoryExtractionService memoryExtractionService,\n            @org.springframework.context.annotation.Lazy ChatService chatService,")
    code = code.replace("this.memoryOrchestrator = memoryOrchestrator;", "this.memoryOrchestrator = memoryOrchestrator;\n        this.chatService = chatService;")

logic_replacement = """
    @EventListener
    @Async
    public void onMemoryCandidateRejected(MemoryCandidateRejectedEvent event) {
        if (event.getReason() == null || event.getReason().isBlank() || "USER_REJECTED".equals(event.getReason())) {
            return;
        }
        
        String contextContent = null;
        String sourceType = "explicit";
        Long sourceId = null;

        if (event.getSourceDiaryId() != null) {
            DiaryEntity diary = diaryMapper.selectById(event.getSourceDiaryId());
            if (diary != null) {
                contextContent = diary.getContent();
                sourceType = "diary_post_process";
                sourceId = diary.getId();
            }
        } else if (event.getSourceConversationId() != null) {
            try {
                Object history = chatService.loadHistory(event.getSourceConversationId());
                if (history != null) {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    contextContent = mapper.writeValueAsString(history);
                    sourceType = "chat";
                    sourceId = event.getSourceConversationId();
                }
            } catch (Exception e) {
                log.warn("Failed to load chat history for memory re-extraction", e);
            }
        }

        if (contextContent == null || contextContent.isBlank()) {
            return;
        }

        log.info("User {} rejected memory candidate {} with reason: {}. Re-extracting...", 
                event.getUserId(), event.getCandidateId(), event.getReason());

        String hint = "【用户修改意见】用户刚刚拒绝了上一版提取的长期记忆（原内容：“" + event.getOriginalText() + "”），"
                + "原因是：“" + event.getReason() + "”。请结合用户的意见，重新从上下文中提取修正后的记忆，不能再犯相同的错误。如果不需要提取任何新记忆则返回空数组。";
                
        // Extract memory with hint
        List<MemoryExtractionService.MemoryAttribute> attributes = memoryExtractionService.extractMemoryFromDiary(
                contextContent, sourceType, event.getUserId(), sourceId, hint);

        if (attributes != null && !attributes.isEmpty()) {
            Long diaryId = "diary_post_process".equals(sourceType) ? sourceId : null;
            Long convId = "chat".equals(sourceType) ? sourceId : null;
            memoryOrchestrator.processExtractedMemories(event.getUserId(), attributes,
                    "explicit", diaryId, convId, null, null);
        }
    }
"""

code = re.sub(r"@EventListener\s*@Async\s*public void onMemoryCandidateRejected\(MemoryCandidateRejectedEvent event\) \{.*\}\s*\}", logic_replacement + "\n}", code, flags=re.DOTALL)

with open("backend/moodcopilot/src/main/java/com/moodcopilot/ai/MemoryCandidateRejectedEventListener.java", "w", encoding="utf-8") as f:
    f.write(code)
print("Success")
