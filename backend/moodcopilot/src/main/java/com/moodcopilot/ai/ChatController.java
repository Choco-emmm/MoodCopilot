package com.moodcopilot.ai;

import com.moodcopilot.common.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final MemoryExtractionService memoryExtractionService;

    public ChatController(ChatService chatService, MemoryExtractionService memoryExtractionService) {
        this.chatService = chatService;
        this.memoryExtractionService = memoryExtractionService;
    }

    // ---- 会话管理 ----

    @GetMapping("/conversations")
    public ApiResponse<Object> listConversations() {
        return ApiResponse.ok(chatService.listConversations());
    }

    @PostMapping("/conversations")
    public ApiResponse<Object> createConversation(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(chatService.createConversation(body.get("title")));
    }

    @DeleteMapping("/conversations/{id}")
    public ApiResponse<Void> deleteConversation(@PathVariable Long id) {
        chatService.deleteConversation(id);
        return ApiResponse.ok(null);
    }

    // ---- 聊天消息 ----

    @PostMapping(value = "/conversations/{id}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String message = (String) body.get("message");
        @SuppressWarnings("unchecked")
        List<String> references = (List<String>) body.get("references");
        String memoryBackground = memoryExtractionService.buildUserMemoryPrompt();
        return chatService.chat(id, message, references, memoryBackground);
    }

    @PostMapping("/conversations/{id}/reply")
    public ApiResponse<String> reply(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String message = (String) body.get("message");
        @SuppressWarnings("unchecked")
        List<String> references = (List<String>) body.get("references");
        String memoryBackground = memoryExtractionService.buildUserMemoryPrompt();
        return ApiResponse.ok(chatService.reply(id, message, references, memoryBackground));
    }

    @GetMapping("/conversations/{id}/history")
    public ApiResponse<Object> loadHistory(@PathVariable Long id) {
        return ApiResponse.ok(chatService.loadHistory(id));
    }

    @PutMapping("/conversations/{id}/history")
    public ApiResponse<Void> saveHistory(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        chatService.saveHistory(id, body);
        return ApiResponse.ok(null);
    }
}
