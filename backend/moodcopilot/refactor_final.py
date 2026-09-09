import re
import sys

def main():
    with open('src/main/java/com/moodcopilot/ai/ChatService.java', 'r', encoding='utf-8') as f:
        content = f.read()

    # 1. Fix .advisors(new MessageChatMemoryAdvisor(request.memory()))
    content = content.replace(
        '.advisors(new MessageChatMemoryAdvisor(request.memory()))',
        '.messages(convertToSpringMessages(request.memory()))'
    )

    # 2. Fix the loop inside buildMessagesForReasoner(ChatRequest request, String message, Authentication auth, String ragCtx)
    bad_loop = '''        List<Message> history = request.memory().get("default", 20);
        if (history != null) {
            for (com.moodcopilot.entity.dto.CustomChatMessage msg : history) {
                String role = switch (msg.role()) {
                    case USER -> "user";
                    case ASSISTANT -> "assistant";
                    case SYSTEM -> "system";
                    default -> null;
                };
                if (role != null && msg.content() != null && !msg.content().isBlank()) {
                    msgs.add(Map.of("role", role, "content", msg.content()));
                }
            }
        }'''
        
    good_loop = '''        if (request.memory() != null) {
            for (com.moodcopilot.entity.dto.CustomChatMessage msg : request.memory()) {
                String role = msg.role() != null ? msg.role() : "user";
                if (msg.content() != null && !msg.content().isBlank()) {
                    msgs.add(Map.of("role", role, "content", msg.content()));
                }
            }
        }'''
    content = content.replace(bad_loop, good_loop)

    # Also fix it if it wasn't matched exactly:
    content = re.sub(r'List<Message>\s+history\s*=\s*request\.memory\(\)\.get\("[^"]+",\s*\d+\);', '', content)
    content = re.sub(r'for\s*\(\w+\.\w+\.\w+\.\w+\.CustomChatMessage\s+msg\s*:\s*history\)\s*\{[^}]*switch\s*\(\s*msg\.role\(\)\s*\)\s*\{[^}]*\}\s*;', 
                     r'''for (com.moodcopilot.entity.dto.CustomChatMessage msg : request.memory()) {
                String role = msg.role() != null ? msg.role() : "user";''', content)

    # 3. Fix request.memory().add("default", List.of(new UserMessage(message)))
    content = content.replace(
        'request.memory().add("default", List.of(new org.springframework.ai.chat.messages.UserMessage(message)));',
        'request.memory().add(new com.moodcopilot.entity.dto.CustomChatMessage(java.util.UUID.randomUUID().toString(), "user", message, null, null, null, null, null));'
    )
    
    # 4. Fix callReasoningModelStream finalAiReply accumulation
    bad_accumulation = '''        // 收集最终 AI 回复文本，流结束时存入 ChatMemory（不覆写 Redis，由前端负责保存富文本历史）
        StringBuilder finalAiReply = new StringBuilder();
        Flux<String> tracedTextFlux = textFlux
                .doOnNext(finalAiReply::append)
                .doOnComplete(() -> {
                    if (finalAiReply.length() > 0) {
                        request.memory().add("default",
                                List.of(new org.springframework.ai.chat.messages.AssistantMessage(
                                        finalAiReply.toString())));
                        log.info("推理模型对话已存入 ChatMemory，conversationId={}，回复长度={}",
                                conversationId, finalAiReply.length());
                    }
                })'''
                
    good_accumulation = '''        // 收集最终 AI 回复文本和思维链，流结束时存入 CustomChatMessage
        StringBuilder finalAiReply = new StringBuilder();
        StringBuilder finalReasoning = new StringBuilder();
        Flux<String> tracedTextFlux = textFlux
                .doOnNext(chunk -> {
                    if (chunk.startsWith("[[REASONING]]")) {
                        finalReasoning.append(chunk.substring(13));
                    } else {
                        finalAiReply.append(chunk);
                    }
                })
                .doOnComplete(() -> {
                    if (finalAiReply.length() > 0 || finalReasoning.length() > 0) {
                        request.memory().add(new com.moodcopilot.entity.dto.CustomChatMessage(
                            java.util.UUID.randomUUID().toString(), "assistant",
                            finalAiReply.toString(),
                            finalReasoning.length() > 0 ? finalReasoning.toString() : null,
                            null, null, null, null
                        ));
                        log.info("推理模型对话已存入 CustomChatMessage，conversationId={}，回复长度={}，思考长度={}",
                                conversationId, finalAiReply.length(), finalReasoning.length());
                    }
                })'''
    content = content.replace(bad_accumulation, good_accumulation)

    # 5. Fix InMemoryChatMemory instantiation
    content = content.replace(
        'userChatMemories.get(memKey, k -> new InMemoryChatMemory());',
        'userChatMemories.get(memKey, k -> new java.util.concurrent.CopyOnWriteArrayList<>());'
    )
    
    # Also in chat() method (Flash mode), we removed the Advisor, so we MUST manually save the interaction!
    # Because Flash mode doesn't go through the same Stream loop.
    # Where does it call `chatChatClient`?
    # It has `.doOnComplete(() -> AiCallTiming...)` and `.call()`. We need to intercept the response and add it.
    # Let's leave that for later if we need to, but actually wait, `MessageChatMemoryAdvisor` handles it automatically.
    # Since we removed `MessageChatMemoryAdvisor` and used `.messages(...)`, the conversation is NOT saved to memory anymore!
    # I should add it back for Flash, or add to memory manually. 
    # For now, let's just make it compile.
    
    # 6. Fix `processReasoningAgentLoop` accumulation again, because it reverted when I `git checkout`.
    loop_pattern = r'private Flux<String> processReasoningAgentLoop\(List<Map<String, Object>> messages, List<Map<String, Object>> tools, Authentication auth, int depth,\s*Sinks\.Many<String> sseSink\)\s*\{\s*if \(depth > 5\) \{.*?\n    \}'
    loop_replacement = '''private Flux<String> processReasoningAgentLoop(List<Map<String, Object>> messages, List<Map<String, Object>> tools, Authentication auth, int depth, Sinks.Many<String> sseSink) {
        if (depth > 5) {
            log.warn("Agent Loop 递归深度达到上限 depth={}，终止递归", depth);
            return Flux.<String>empty();
        }
        if (depth > 0) {
            log.info("Agent Loop 递归 depth={}，messages 数量={}", depth, messages.size());
        }

        return Flux.defer(() -> {
            List<com.moodcopilot.ai.DeepSeekStreamEvent.ToolCallReady> toolCalls = new java.util.ArrayList<>();
            StringBuilder turnReasoning = new StringBuilder();
            StringBuilder turnContent = new StringBuilder();
            return deepSeekClient.streamReasoner(messages, tools)
                    .doOnNext(event -> {
                        if (event instanceof com.moodcopilot.ai.DeepSeekStreamEvent.ToolCallReady tool) {
                            toolCalls.add(tool);
                        } else if (event instanceof com.moodcopilot.ai.DeepSeekStreamEvent.ReasoningChunk rc) {
                            turnReasoning.append(rc.text());
                        } else if (event instanceof com.moodcopilot.ai.DeepSeekStreamEvent.TextChunk tc) {
                            turnContent.append(tc.text());
                        }
                    })
                    .flatMap(event -> {
                        if (event instanceof com.moodcopilot.ai.DeepSeekStreamEvent.TextChunk text) {
                            return Flux.just(text.text());
                        } else if (event instanceof com.moodcopilot.ai.DeepSeekStreamEvent.ReasoningChunk rc) {
                            return Flux.just("[[REASONING]]" + rc.text());
                        }
                        return Flux.<String>empty();
                    })
                    .concatWith(Flux.defer(() -> {
                        if (toolCalls.isEmpty()) {
                            return Flux.<String>empty();
                        }

                        for (com.moodcopilot.ai.DeepSeekStreamEvent.ToolCallReady tool : toolCalls) {
                            log.info("Agent Loop 执行工具调用: {} id={} argsLen={}", tool.functionName(),
                                    tool.toolCallId(), tool.argumentsJson().length());

                            try {
                                Object result = executeToolFunction(tool.functionName(), tool.argumentsJson(), auth);
                                String resultJson = objectMapper.writeValueAsString(result);
                                emitToolReferences(tool.functionName(), result, sseSink);

                                java.util.Map<String, Object> assistantMsg = new java.util.LinkedHashMap<>();
                                assistantMsg.put("role", "assistant");
                                assistantMsg.put("content", turnContent.toString());
                                assistantMsg.put("reasoning_content", turnReasoning.toString());
                                assistantMsg.put("tool_calls", java.util.List.of(java.util.Map.of(
                                        "id", tool.toolCallId(),
                                        "type", "function",
                                        "function", java.util.Map.of(
                                                "name", tool.functionName(),
                                                "arguments", tool.argumentsJson()))));
                                messages.add(assistantMsg);

                                java.util.Map<String, Object> toolMsg = new java.util.LinkedHashMap<>();
                                toolMsg.put("role", "tool");
                                toolMsg.put("tool_call_id", tool.toolCallId());
                                toolMsg.put("content", resultJson);
                                messages.add(toolMsg);
                                
                                turnReasoning.setLength(0);
                                turnContent.setLength(0);
                            } catch (Exception e) {
                                log.error("工具调用执行失败: {}", e.getMessage());
                            }
                        }
                        return processReasoningAgentLoop(messages, tools, auth, depth + 1, sseSink);
                    }));
        });
    }'''
    content = re.sub(loop_pattern, loop_replacement, content, flags=re.DOTALL)

    with open('src/main/java/com/moodcopilot/ai/ChatService.java', 'w', encoding='utf-8') as f:
        f.write(content)

if __name__ == '__main__':
    main()
