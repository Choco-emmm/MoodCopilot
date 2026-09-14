import sys

with open('backend/moodcopilot/src/main/java/com/moodcopilot/ai/ChatService.java', 'r', encoding='utf-8') as f:
    content = f.read()

# Add appendToChatMemory method
helper = '''
    private void appendToChatMemory(Long conversationId, List<com.moodcopilot.entity.dto.CustomChatMessage> memory, String role, String content, String reasoningContent) {
        if (memory == null) return;
        boolean alreadyHas = false;
        if (!memory.isEmpty()) {
            com.moodcopilot.entity.dto.CustomChatMessage lastMem = memory.get(memory.size() - 1);
            if (role.equalsIgnoreCase(lastMem.role()) && content != null && content.equals(lastMem.content())) {
                alreadyHas = true;
            }
        }
        if (!alreadyHas) {
            memory.add(new com.moodcopilot.entity.dto.CustomChatMessage(
                java.util.UUID.randomUUID().toString(), role, content, reasoningContent, null, null, null, null
            ));
        }
        
        try {
            String json = objectMapper.writeValueAsString(memory);
            redisTemplate.opsForValue().set(MSG_PREFIX + conversationId, json, java.time.Duration.ofDays(7));
        } catch (Exception e) {
            log.warn("Failed to auto-save chat history to Redis for conversationId=" + conversationId, e);
        }
    }
'''

if 'appendToChatMemory' not in content:
    content = content.replace('public Object loadHistory(Long conversationId) {', helper + '\n    public Object loadHistory(Long conversationId) {')


# Update reply method (PRO)
old_pro = '''if (exec.useReasoning()) {
            log.info("非流式路由到reasoning，conversationId={}，messageLength={}", conversationId,
                    message == null ? 0 : message.length());
            return callReasoningModel(request, message, auth, conversationId, ragCtx);
        }'''
new_pro = '''if (exec.useReasoning()) {
            log.info("非流式路由到reasoning，conversationId={}，messageLength={}", conversationId,
                    message == null ? 0 : message.length());
            String result = callReasoningModel(request, message, auth, conversationId, ragCtx);
            appendToChatMemory(conversationId, request.memory(), "user", message, null);
            appendToChatMemory(conversationId, request.memory(), "assistant", result, null);
            return result;
        }'''
content = content.replace(old_pro, new_pro)


# Update reply method (FLASH)
old_flash = '''AiCallTiming.completed(log, "CHAT", "FLASH", aiStartedAt, "SUCCESS",
                    message == null ? 0 : message.length(), result == null ? 0 : result.length());
            return result;'''
new_flash = '''AiCallTiming.completed(log, "CHAT", "FLASH", aiStartedAt, "SUCCESS",
                    message == null ? 0 : message.length(), result == null ? 0 : result.length());
            appendToChatMemory(conversationId, request.memory(), "user", message, null);
            appendToChatMemory(conversationId, request.memory(), "assistant", result, null);
            return result;'''
content = content.replace(old_flash, new_flash)


with open('backend/moodcopilot/src/main/java/com/moodcopilot/ai/ChatService.java', 'w', encoding='utf-8') as f:
    f.write(content)

print("Patched ChatService.java")
