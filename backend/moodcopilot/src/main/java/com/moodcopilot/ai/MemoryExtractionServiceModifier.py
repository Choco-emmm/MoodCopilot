import re

with open("backend/moodcopilot/src/main/java/com/moodcopilot/ai/MemoryExtractionService.java", "r", encoding="utf-8") as f:
    code = f.read()

method = """
    public List<MemoryAttribute> extractMemoryFromDiary(String diaryContent, String sourceType, Long userId, Long diaryId, String hint) {
        if (diaryContent == null || diaryContent.isBlank()) {
            return List.of();
        }
        try {
            String prompt = buildExtractionUserPrompt(diaryContent, null, null);
            if (hint != null && !hint.isBlank()) {
                prompt = prompt + "\\n\\n" + hint;
            }
            TaskContext taskContext = new TaskContext("GENERAL", "提取记忆", List.of(), null);
            
            String json = analysisChatClient.prompt()
                    .system(memoryExtractionSystemPrompt(userId, taskContext, null))
                    .user(prompt)
                    .call()
                    .content();
            
            String cleanedJson = JsonUtils.cleanJson(json);
            if (cleanedJson.isEmpty()) {
                return List.of();
            }
            MemoryExtractionResponse response = objectMapper.readValue(cleanedJson, MemoryExtractionResponse.class);
            if (response.memorySignals() != null) {
                return sanitizeAttributes(response.memorySignals());
            }
        } catch (Exception e) {
            log.error("Failed to re-extract memory for diary " + diaryId + " with hint", e);
        }
        return List.of();
    }
"""

if "extractMemoryFromDiary" not in code:
    code = code.replace("public void extractAndSyncMemory(", method + "\n    public void extractAndSyncMemory(", 1)
    with open("backend/moodcopilot/src/main/java/com/moodcopilot/ai/MemoryExtractionService.java", "w", encoding="utf-8") as f:
        f.write(code)
    print("Success")
