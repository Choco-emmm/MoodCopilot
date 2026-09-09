import re
import sys

def main():
    with open('src/main/java/com/moodcopilot/ai/ChatService.java', 'r', encoding='utf-8') as f:
        content = f.read()

    # formatChatHistory -> CustomChatMessage
    content = content.replace(
        'private String formatChatHistory(ChatMemory memory) {',
        'private String formatChatHistory(List<com.moodcopilot.entity.dto.CustomChatMessage> memory) {'
    )
    content = content.replace(
        'List<Message> history = memory.get("default", 20);',
        'List<com.moodcopilot.entity.dto.CustomChatMessage> history = memory;'
    )
    content = content.replace(
        'for (Message msg : history) {',
        'for (com.moodcopilot.entity.dto.CustomChatMessage msg : history) {'
    )
    content = content.replace(
        'String role = msg instanceof UserMessage ? "USER" : "AI";',
        'String role = "user".equalsIgnoreCase(msg.role()) ? "USER" : "AI";'
    )
    content = content.replace(
        'String text = msg.getText() != null ? msg.getText() : "";',
        'String text = msg.content() != null ? msg.content() : "";'
    )

    # persistChatMemory -> CustomChatMessage
    content = content.replace(
        'private void persistChatMemory(long conversationId, ChatMemory memory) {',
        'private void persistChatMemory(long conversationId, List<com.moodcopilot.entity.dto.CustomChatMessage> memory) {'
    )
    content = content.replace(
        'List<Message> messages = memory.get(String.valueOf(conversationId), 100);',
        'List<com.moodcopilot.entity.dto.CustomChatMessage> messages = memory;'
    )

    # compressChatHistory -> CustomChatMessage
    content = content.replace(
        'private String compressChatHistory(Long userId, Long conversationId, ChatMemory memory) {',
        'private String compressChatHistory(Long userId, Long conversationId, List<com.moodcopilot.entity.dto.CustomChatMessage> memory) {'
    )
    content = content.replace(
        'List<Message> history = memory.get("default", 100);',
        'List<com.moodcopilot.entity.dto.CustomChatMessage> history = memory;'
    )
    content = content.replace(
        'memory.clear();',
        'memory.clear();' # stays the same
    )
    content = content.replace(
        'memory.add("default", recent);',
        'memory.addAll(recent);'
    )
    content = content.replace(
        'List<Message> recent = new ArrayList<>(history.subList(history.size() - KEEP_RECENT_MSG_COUNT, history.size()));',
        'List<com.moodcopilot.entity.dto.CustomChatMessage> recent = new java.util.ArrayList<>(history.subList(history.size() - KEEP_RECENT_MSG_COUNT, history.size()));'
    )
    content = content.replace(
        'List<Message> toCompress = history.subList(0, history.size() - KEEP_RECENT_MSG_COUNT);',
        'List<com.moodcopilot.entity.dto.CustomChatMessage> toCompress = history.subList(0, history.size() - KEEP_RECENT_MSG_COUNT);'
    )
    content = content.replace(
        'UserMessage userMessage = (UserMessage) msg;',
        '' # we don't have UserMessage cast anymore
    )
    content = content.replace(
        'sb.append("User: ").append(userMessage.getText()).append("\\n");',
        'sb.append("User: ").append(msg.content()).append("\\n");'
    )
    content = content.replace(
        'AssistantMessage aiMessage = (AssistantMessage) msg;',
        ''
    )
    content = content.replace(
        'sb.append("AI: ").append(aiMessage.getText()).append("\\n");',
        'sb.append("AI: ").append(msg.content()).append("\\n");'
    )
    content = content.replace(
        'if (msg instanceof UserMessage) {',
        'if ("user".equalsIgnoreCase(msg.role())) {'
    )
    content = content.replace(
        '} else if (msg instanceof AssistantMessage) {',
        '} else if ("assistant".equalsIgnoreCase(msg.role()) || "ai".equalsIgnoreCase(msg.role())) {'
    )

    # Spring AI Flash memory advisor replacement
    # find `.advisors(new MessageChatMemoryAdvisor(request.memory()))`
    # and replace with `.messages(convertToSpringMessages(request.memory()))`
    content = content.replace(
        '.advisors(new MessageChatMemoryAdvisor(request.memory()))',
        '.messages(convertToSpringMessages(request.memory()))'
    )

    # Add convertToSpringMessages method
    converter_method = '''    private List<org.springframework.ai.chat.messages.Message> convertToSpringMessages(List<com.moodcopilot.entity.dto.CustomChatMessage> customMsgs) {
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
    if 'convertToSpringMessages' not in content:
        content = content.replace('public class ChatService {', 'public class ChatService {\n' + converter_method)

    # In chat() method, we must append the UserMessage and AssistantMessage to custom memory manually!
    # Let's find:
    # Flux<String> stream = chatChatClient.prompt()
    #         .user(message)
    # ...
    # And after, we need to append.
    # It does this now inside `MessageChatMemoryAdvisor`. But we removed it.
    
    with open('src/main/java/com/moodcopilot/ai/ChatService.java', 'w', encoding='utf-8') as f:
        f.write(content)

if __name__ == '__main__':
    main()
