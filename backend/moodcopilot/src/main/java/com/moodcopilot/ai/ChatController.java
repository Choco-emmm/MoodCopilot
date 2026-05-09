package com.moodcopilot.ai;

import com.moodcopilot.common.ApiResponse;
import com.moodcopilot.entity.UserEntity;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        return chatService.chat(message);
    }

    @PutMapping("/history")
    public ApiResponse<Void> saveHistory(@RequestBody Map<String, Object> body) {
        UserEntity user = (UserEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        chatService.saveHistory(user.getId(), body);
        return ApiResponse.ok(null);
    }

    @GetMapping("/history")
    public ApiResponse<Object> loadHistory() {
        UserEntity user = (UserEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ApiResponse.ok(chatService.loadHistory(user.getId()));
    }

    @DeleteMapping("/memory")
    public ApiResponse<Void> clearMemory() {
        UserEntity user = (UserEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        chatService.clearMemory(user.getId());
        return ApiResponse.ok(null);
    }
}
