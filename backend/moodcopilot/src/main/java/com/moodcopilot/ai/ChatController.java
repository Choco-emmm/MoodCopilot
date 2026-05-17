package com.moodcopilot.ai;

import com.moodcopilot.common.ApiResponse;
import com.moodcopilot.entity.UserEntity;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final MemoryExtractionService memoryExtractionService;

    public ChatController(ChatService chatService, MemoryExtractionService memoryExtractionService) {
        this.chatService = chatService;
        this.memoryExtractionService = memoryExtractionService;
    }

    // ---- 一次性批量初始化画像（初始化后可删除此接口）----

    @PostMapping("/admin/init-memory")
    public ApiResponse<String> initMemory() {
        log.info("收到批量初始化长期画像请求");
        memoryExtractionService.batchInitAllUsers();
        return ApiResponse.ok("批量画像初始化任务已提交（异步执行）");
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

    /**
     * SSE 流式聊天入口。
     * 每次请求都会先把当前用户的长期画像转成背景 prompt，再交给 ChatService 统一拼装完整上下文。
     */
    @PostMapping(value = "/conversations/{id}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@PathVariable Long id, @RequestBody Map<String, Object> body,
                             HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");
        String message = (String) body.get("message");
        @SuppressWarnings("unchecked")
        List<String> references = (List<String>) body.get("references");
        String memoryBackground = memoryExtractionService.buildUserMemoryPrompt();
        log.info("收到流式聊天请求，conversationId={}，messageLength={}，referenceCount={}",
                id, message == null ? 0 : message.length(), references == null ? 0 : references.size());
        // 在异步流开始前捕获 userId 和 Authentication，避免异步回调中 SecurityContext 丢失
        UserEntity user = (UserEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = user.getId();
        Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
        StringBuilder aiReplyBuffer = new StringBuilder();
        Flux<String> chatFlux;
        try {
            chatFlux = chatService.chat(id, message, references, memoryBackground);
        } catch (com.moodcopilot.common.RateLimitException e) {
            log.info("AI 限流触发，conversationId={}，type={}", id, e.getType());
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, e.getMessage(), e);
        }
        return chatFlux
                .doOnNext(chunk -> {
                    // 流式返回按 chunk 到达，这里先拼完整回复，完成时再统一更新画像。
                    if (chunk != null && !chunk.isBlank()) {
                        aiReplyBuffer.append(chunk);
                    }
                })
                .doOnComplete(() -> {
                    log.info("流式聊天完成，准备触发画像增量更新，conversationId={}，replyLength={}",
                            id, aiReplyBuffer.length());
                    try {
                        memoryExtractionService.extractAndSyncMemoryFromChat(userId, message, references,
                                aiReplyBuffer.toString());
                        log.info("流式聊天后画像增量更新已提交，conversationId={}", id);
                    } catch (Exception e) {
                        log.warn("聊天后触发长期画像更新失败，conversationId={}，reason={}", id, e.getMessage());
                    }
                })
                .onErrorResume(e -> {
                    log.warn("SSE 流异常终止，conversationId={}，error={}", id, e.getMessage());
                    return Flux.just("\n\n[服务器暂时无法回应，请稍后重试。]");
                })
                .contextWrite(ctx -> ctx.put(Authentication.class, currentAuth));
    }

    /**
     * 非流式回复入口。
     * 公网或移动端可以用这个接口避免依赖 SSE 连接。
     */
    @PostMapping("/conversations/{id}/reply")
    public ApiResponse<String> reply(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String message = (String) body.get("message");
        @SuppressWarnings("unchecked")
        List<String> references = (List<String>) body.get("references");
        String memoryBackground = memoryExtractionService.buildUserMemoryPrompt();
        log.info("收到非流式聊天请求，conversationId={}，messageLength={}，referenceCount={}",
                id, message == null ? 0 : message.length(), references == null ? 0 : references.size());
        UserEntity user = (UserEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = user.getId();
        String reply;
        try {
            reply = chatService.reply(id, message, references, memoryBackground);
        } catch (com.moodcopilot.common.RateLimitException e) {
            log.info("AI 限流触发（非流式），conversationId={}，type={}", id, e.getType());
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, e.getMessage(), e);
        }
        log.info("非流式聊天完成，准备触发画像增量更新，conversationId={}，replyLength={}",
                id, reply == null ? 0 : reply.length());
        try {
            memoryExtractionService.extractAndSyncMemoryFromChat(userId, message, references, reply);
            log.info("非流式聊天后画像增量更新已提交，conversationId={}", id);
        } catch (Exception e) {
            log.warn("非流式聊天后触发长期画像更新失败，conversationId={}，reason={}", id, e.getMessage());
        }
        return ApiResponse.ok(reply);
    }

    @GetMapping("/conversations/{id}/history")
    public ApiResponse<Object> loadHistory(@PathVariable Long id) {
        return ApiResponse.ok(chatService.loadHistory(id));
    }

    @PutMapping("/conversations/{id}/history")
    public ApiResponse<Void> saveHistory(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        log.info("收到聊天历史保存请求，conversationId={}", id);
        chatService.saveHistory(id, body);
        return ApiResponse.ok(null);
    }
}
