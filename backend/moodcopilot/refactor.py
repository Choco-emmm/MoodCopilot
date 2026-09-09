import re
import sys

def main():
    with open('src/main/java/com/moodcopilot/ai/ChatService.java', 'r', encoding='utf-8') as f:
        content = f.read()

    # 1. Update userChatMemories field
    content = content.replace(
        'private final Cache<String, ChatMemory> userChatMemories;',
        'private final Cache<String, List<com.moodcopilot.entity.dto.CustomChatMessage>> userChatMemories;'
    )

    # 2. Update constructor
    content = content.replace(
        'Cache<String, ChatMemory> userChatMemories,',
        'Cache<String, List<com.moodcopilot.entity.dto.CustomChatMessage>> userChatMemories,'
    )

    # 3. ChatRequest signature
    content = content.replace(
        'private record ChatRequest(String context, ChatMemory memory, String summary,',
        'private record ChatRequest(String context, List<com.moodcopilot.entity.dto.CustomChatMessage> memory, String summary,'
    )
    
    # 4. Update prepareChatRequest where memory is initialized
    content = content.replace(
        'ChatMemory memory = userChatMemories.get(memKey, k -> new InMemoryChatMemory());',
        'List<com.moodcopilot.entity.dto.CustomChatMessage> memory = userChatMemories.get(memKey, k -> new java.util.concurrent.CopyOnWriteArrayList<>());'
    )
    
    # 5. Rewrite restoreChatMemoryFromRedis (match method signature and body)
    pattern = r'private void restoreChatMemoryFromRedis\(Long conversationId, ChatMemory memory\) \{.*?\n    \}'
    replacement = '''private void restoreChatMemoryFromRedis(Long conversationId, List<com.moodcopilot.entity.dto.CustomChatMessage> memory) {
        if (!memory.isEmpty()) {
            return;
        }
        String redisKey = "chat:msgs:" + conversationId;
        String historyJson = redisTemplate.opsForValue().get(redisKey);
        if (historyJson == null || historyJson.isBlank()) {
            return;
        }
        try {
            List<com.moodcopilot.entity.dto.CustomChatMessage> history = objectMapper.readValue(historyJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            memory.addAll(history);
            log.info("已从 Redis 恢复聊天历史，conversationId={}，消息数={}", conversationId, history.size());
        } catch (Exception e) {
            log.warn("无法从 Redis 反序列化历史消息：conversationId={}", conversationId, e);
        }
    }'''
    content = re.sub(pattern, replacement, content, flags=re.DOTALL)
    
    # 6. Rewrite buildMessagesForReasoner
    pattern_build = r'private List<Map<String, Object>> buildMessagesForReasoner\(String message, ChatRequest request\) \{.*?\n    \}'
    replacement_build = '''private List<Map<String, Object>> buildMessagesForReasoner(String message, ChatRequest request) {
        List<Map<String, Object>> msgs = new java.util.ArrayList<>();
        
        Map<String, Object> systemMsg = new java.util.LinkedHashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", request.context());
        msgs.add(systemMsg);
        
        if (request.memory() != null) {
            for (com.moodcopilot.entity.dto.CustomChatMessage memMsg : request.memory()) {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("role", memMsg.role());
                m.put("content", memMsg.content() != null ? memMsg.content() : "");
                if (memMsg.reasoningContent() != null && !memMsg.reasoningContent().isEmpty()) {
                    m.put("reasoning_content", memMsg.reasoningContent());
                }
                if (memMsg.toolCalls() != null && !memMsg.toolCalls().isEmpty()) {
                    m.put("tool_calls", memMsg.toolCalls());
                }
                msgs.add(m);
            }
        }
        
        Map<String, Object> userMsg = new java.util.LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", message);
        msgs.add(userMsg);
        
        return msgs;
    }'''
    content = re.sub(pattern_build, replacement_build, content, flags=re.DOTALL)

    # 7. Rewrite processReasoningAgentLoop to handle reasoning chunks and accumulate them properly
    pattern_loop = r'private void processReasoningAgentLoop\(.*?\}\s*\}\s*\n    \}'
    # Wait, processReasoningAgentLoop is large. I will use replace_file_content for it if needed, or regex.
    # Let's write the modified version of the relevant parts directly.

    with open('src/main/java/com/moodcopilot/ai/ChatService.java', 'w', encoding='utf-8') as f:
        f.write(content)

if __name__ == '__main__':
    main()
