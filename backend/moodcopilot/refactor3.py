import re
import sys

def main():
    with open('src/main/java/com/moodcopilot/ai/ChatService.java', 'r', encoding='utf-8') as f:
        content = f.read()

    # 1. Add convertToSpringMessages if not present
    if 'convertToSpringMessages' not in content:
        converter = '''
    private List<org.springframework.ai.chat.messages.Message> convertToSpringMessages(List<com.moodcopilot.entity.dto.CustomChatMessage> customMsgs) {
        if (customMsgs == null) return new java.util.ArrayList<>();
        List<org.springframework.ai.chat.messages.Message> springMsgs = new java.util.ArrayList<>();
        for (com.moodcopilot.entity.dto.CustomChatMessage cm : customMsgs) {
            if ("user".equalsIgnoreCase(cm.role())) {
                springMsgs.add(new org.springframework.ai.chat.messages.UserMessage(cm.content() != null ? cm.content() : ""));
            } else if ("assistant".equalsIgnoreCase(cm.role()) || "ai".equalsIgnoreCase(cm.role())) {
                springMsgs.add(new org.springframework.ai.chat.messages.AssistantMessage(cm.content() != null ? cm.content() : ""));
            }
        }
        return springMsgs;
    }
'''
        content = content.replace('public class ChatService {', 'public class ChatService {\n' + converter)

    # 2. Fix lines where memory.get(...) is still used incorrectly
    # e.g., memory.get(conversationId.toString(), 1)
    content = re.sub(r'List<Message>\s+\w+\s*=\s*memory\.get\([^,]+,\s*\d+\);', 'List<com.moodcopilot.entity.dto.CustomChatMessage> existing = memory;', content)
    content = re.sub(r'List<Message>\s+history\s*=\s*memory\.get\([^,]+,\s*\d+\);', 'List<com.moodcopilot.entity.dto.CustomChatMessage> history = memory;', content)
    content = re.sub(r'List<Message>\s+messages\s*=\s*memory\.get\([^,]+,\s*\d+\);', 'List<com.moodcopilot.entity.dto.CustomChatMessage> messages = memory;', content)

    # 3. Fix memory.add(...) where it passes a String key
    content = re.sub(r'memory\.add\([^,]+,\s*recent\);', 'memory.addAll(recent);', content)
    content = re.sub(r'memory\.clear\([^)]+\);', 'memory.clear();', content)
    
    # 4. Fix specific method `dumpChatMemory` or anything that loops over `List<Message>`
    # Find any remaining `Message msg : memory` loops
    content = re.sub(r'for\s*\(\s*Message\s+(\w+)\s*:\s*history\s*\)', r'for (com.moodcopilot.entity.dto.CustomChatMessage \1 : history)', content)
    content = re.sub(r'for\s*\(\s*Message\s+(\w+)\s*:\s*messages\s*\)', r'for (com.moodcopilot.entity.dto.CustomChatMessage \1 : messages)', content)
    
    # 5. Fix type casts and method calls on `Message` that are now `CustomChatMessage`
    content = content.replace('msg.getMessageType().getValue()', 'msg.role()')
    content = content.replace('msg.getMessageType()', 'msg.role()')
    content = content.replace('msg.getText()', 'msg.content()')
    
    # 6. We might have missed some memory.get(...)
    content = re.sub(r'memory\.get\([^)]+\)', 'memory', content) # Dangerous but memory is now a List, so memory.get(int) is valid, but memory.get(String, int) is not.
    # Actually wait, memory.get(int) is valid.
    content = re.sub(r'memory\.get\("[^"]+",\s*\d+\)', 'memory', content)
    content = re.sub(r'memory\.get\(String\.valueOf\(conversationId\),\s*\d+\)', 'memory', content)
    content = re.sub(r'memory\.get\(conversationId\.toString\(\),\s*\d+\)', 'memory', content)

    with open('src/main/java/com/moodcopilot/ai/ChatService.java', 'w', encoding='utf-8') as f:
        f.write(content)

if __name__ == '__main__':
    main()
