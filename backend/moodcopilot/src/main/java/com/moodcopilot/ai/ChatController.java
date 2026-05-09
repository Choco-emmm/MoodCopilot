package com.moodcopilot.ai;

import com.moodcopilot.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ApiResponse<Map<String, String>> chat(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        String reply = chatService.chat(message);
        return ApiResponse.ok(Map.of("reply", reply));
    }
}
