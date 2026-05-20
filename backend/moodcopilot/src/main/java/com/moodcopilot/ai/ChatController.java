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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        String memoryBackground = memoryExtractionService.buildCoreUserMemoryPrompt();
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
                        String cleanReply = removePreToolDuplicate(aiReplyBuffer.toString());
                        memoryExtractionService.extractAndSyncMemoryFromChat(userId, message, references,
                                cleanReply);
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
        String memoryBackground = memoryExtractionService.buildCoreUserMemoryPrompt();
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

    /**
     * 检测并移除 Function Calling 导致的前置废话。
     * 当模型在调用工具前先输出了"我帮你查一下"等过渡语，
     * 这些前置文本和工具返回后的正式回复会被拼接在一起，导致割裂和重复。
     * 此方法用启发式规则检测并保留后半段（基于工具数据的实质性回复）。
     */
    private String removePreToolDuplicate(String raw) {
        if (raw == null || raw.length() < 30) return raw;

        // 规则 1：检测 "帮你查" 类过渡语后是否还有足够长的实质性内容
        Pattern preface = Pattern.compile("^.*?(我(?:帮你|来|去).*?(?:查|看看|搜索|检索)).*?[。！\\n]");
        Matcher m = preface.matcher(raw);
        if (m.find() && m.end() < raw.length() - 20) {
            String candidate = raw.substring(m.end()).trim();
            if (candidate.length() > 20) {
                log.info("去重兜底触发（规则1），截取后半段 {}→{} 字符", raw.length(), candidate.length());
                return candidate;
            }
        }

        // 规则 2：检测明显的"转折重新开始"标记，保留后半段
        String[] markers = {"好的，根据", "好了，我查", "我查了一下", "我帮你查", "我检索到"};
        for (String marker : markers) {
            int idx = raw.indexOf(marker);
            if (idx > 20 && idx < raw.length() / 2) {
                String after = raw.substring(idx).trim();
                if (after.length() > 20) {
                    log.info("去重兜底触发（规则2），标记=\"{}\" {}→{} 字符", marker, raw.length(), after.length());
                    return after;
                }
            }
        }

        return raw;
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
