package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private final ObjectMapper objectMapper;

    public ChatController(ChatService chatService, MemoryExtractionService memoryExtractionService,
            ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.memoryExtractionService = memoryExtractionService;
        this.objectMapper = objectMapper;
    }

    // ---- 一次性批量初始化画像（初始化后可删除此接口）----

    @PostMapping("/admin/init-memory")
    public ApiResponse<String> initMemory() {
        log.info("收到批量初始化长期画像请求");
        memoryExtractionService.batchInitAllUsers();
        return ApiResponse.ok("批量画像初始化任务已提交（异步执行）");
    }

    // ---- 会话管理 ----

    @GetMapping("/welcome-topics")
    public ApiResponse<Object> getWelcomeTopics() {
        return ApiResponse.ok(chatService.getWelcomeTopics());
    }

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
        UserEntity user = (UserEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = user.getId();
        Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
        StringBuilder aiReplyBuffer = new StringBuilder();
        ChatService.ChatStreamContext ctx;
        try {
            ctx = chatService.chat(id, message, references, memoryBackground);
        } catch (com.moodcopilot.common.RateLimitException e) {
            log.info("AI 限流触发，conversationId={}，type={}", id, e.getType());
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, e.getMessage(), e);
        }

        // 解析 RAG 上下文为结构化引用列表
        List<Map<String, String>> refItems = parseRagReferences(ctx.ragContext());
        Flux<String> refsEvent;
        try {
            Map<String, Object> refsPayload = new LinkedHashMap<>();
            refsPayload.put("type", "references");
            refsPayload.put("items", refItems);
            refsEvent = Flux.just(objectMapper.writeValueAsString(refsPayload));
        } catch (Exception e) {
            log.warn("RAG 引用序列化失败: {}", e.getMessage());
            refsEvent = Flux.empty();
        }

        Flux<String> chunkStream = ctx.stream()
                .doOnNext(chunk -> {
                    if (chunk != null && !chunk.isBlank() && !chunk.startsWith("[[TOOL_EVENT]]")) {
                        aiReplyBuffer.append(chunk);
                    }
                })
                .map(chunk -> {
                    if (chunk != null && chunk.startsWith("[[TOOL_EVENT]]")) {
                        return chunk.substring("[[TOOL_EVENT]]".length());
                    }
                    try {
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("type", "chunk");
                        payload.put("content", chunk != null ? chunk : "");
                        return objectMapper.writeValueAsString(payload);
                    } catch (Exception e) {
                        return chunk != null ? chunk : "";
                    }
                });

        Flux<String> doneEvent;
        try {
            Map<String, Object> donePayload = new LinkedHashMap<>();
            donePayload.put("type", "done");
            doneEvent = Flux.just(objectMapper.writeValueAsString(donePayload));
        } catch (Exception e) {
            doneEvent = Flux.empty();
        }

        return Flux.concat(refsEvent, chunkStream, doneEvent)
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
                    try {
                        Map<String, Object> errPayload = new LinkedHashMap<>();
                        errPayload.put("type", "chunk");
                        errPayload.put("content", "\n\n[服务器暂时无法回应，请稍后重试。]");
                        return Flux.just(objectMapper.writeValueAsString(errPayload));
                    } catch (Exception ex) {
                        return Flux.just("\n\n[服务器暂时无法回应，请稍后重试。]");
                    }
                })
                .contextWrite(c -> c.put(Authentication.class, currentAuth));
    }

    /** 从 RAG XML 上下文中提取结构化引用条目供前端展示 */
    private List<Map<String, String>> parseRagReferences(String ragCtx) {
        List<Map<String, String>> items = new ArrayList<>();
        if (ragCtx == null || ragCtx.isBlank()) return items;
        Pattern itemPattern = Pattern.compile(
                "<context_item type=\"(\\w+)\"(?: diary_id=\"(\\d+)\")?(?: date=\"([^\"]*)\")?>(.*?)</context_item>",
                Pattern.DOTALL);
        Matcher m = itemPattern.matcher(ragCtx);
        while (m.find()) {
            String type = m.group(1);
            String diaryId = m.group(2) != null ? m.group(2) : "";
            String date = m.group(3) != null ? m.group(3) : "";
            String inner = m.group(4);
            String snippet = extractAllTextContent(inner);
            Map<String, String> item = new LinkedHashMap<>();
            item.put("type", type);
            item.put("diaryId", diaryId);
            item.put("date", date);
            item.put("snippet", snippet.length() > 120 ? snippet.substring(0, 120) + "…" : snippet);
            items.add(item);
        }
        return items;
    }

    /** 提取 XML 内层所有文本内容作为摘要，不再仅限第一个标签，避免遗漏日记正文 */
    private String extractAllTextContent(String xml) {
        if (xml == null || xml.isBlank()) return "";
        // 移除所有XML标签，用空格替换，然后合并连续空格
        String plain = xml.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        // 去掉 XML 转义
        return plain.replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'");
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
     *
     * 根因：模型有时会在工具调用前输出"我帮你查"等过渡语，而纯文本模型可能本能地先否认
     * （如"系统好像没记录"），然后 RAG 上下文已把 VLM 图片描述塞给它，导致后半段又引用具体信息。
     * 本方法用多轮启发式规则把前置废话和错误推理切除，只保留基于工具/RAG 数据的实质回复。
     */
    private String removePreToolDuplicate(String raw) {
        if (raw == null || raw.length() < 30) return raw;

        // ── 规则 1：检测"查/检索"类过渡语 + 后续否定陈述，切到真正的转折点 ──
        // 典型："我来帮你查一下。从你的日记记录来看，系统里好像没有...不过之前有篇日记..."
        // 目标：定位到 "不过我查" / "不过从记录" / "但我发现" / "根据数据" 等转折词，
        // 如果前面包含"帮你查/检索/看看"等过渡语 AND 后面有实质性内容，则丢弃转折词之前的所有内容。
        Pattern pivotPattern = Pattern.compile(
                "(?:不过|但是|但|然而|可实际上|实际上)[^。！？\\n]{0,30}?" +
                "(?:我(?:查|检索|搜索|翻|找|调|看|确认)" +
                "|从(?:记录|数据|日记|系统)" +
                "|根据(?:你|数据|记录|检索)" +
                "|显示|提到|记载|记录到)",
                Pattern.CASE_INSENSITIVE);
        Matcher pivotM = pivotPattern.matcher(raw);
        while (pivotM.find()) {
            int pivotIdx = pivotM.start();
            // 检查转折词之前是否包含"帮你查/检索/看看"等过渡语
            String before = raw.substring(0, pivotIdx);
            if (before.length() < 10) continue;
            boolean hasFiller = before.matches(".*?(?:我(?:帮你|来|去|给你).*?(?:查|看看|搜索|检索|确认)).*");
            boolean hasDenial = before.matches(".*?(?:没有(?:直接)?(?:记录|找到|发现|看到)|找不到|未发现).*");
            if (hasFiller || hasDenial) {
                String after = raw.substring(pivotIdx).trim();
                if (after.length() > 20 && !after.equals(raw.trim())) {
                    log.info("去重兜底触发（规则1-转折词），pivotIdx={} {}→{} 字符",
                            pivotIdx, raw.length(), after.length());
                    return after;
                }
            }
        }

        // ── 规则 2：检测割裂式自我矛盾 ──
        // 当模型先说"没有/找不到"，后面却又引用具体信息 → 典型的 RAG 注入后自我打脸
        Pattern selfContradiction = Pattern.compile(
                "^.*?(?:没有(?:直接)?(?:记录|找到|发现|看到)|找不到|未发现|系统(?:里|中)(?:好像)?(?:没|不)).*?[。！]",
                Pattern.CASE_INSENSITIVE);
        Matcher contradictM = selfContradiction.matcher(raw);
        if (contradictM.find()) {
            int end = contradictM.end();
            String rest = raw.substring(end).trim();
            // 只有当后半段确实引用了具体数据（含"描述为"/"显示"/"提到"/"记载"/"标题"/"歌手"等）
            // 才判定为矛盾并切除前半段
            boolean hasDataRef = rest.matches(".*?(?:描述为|显示|提到|记载|记录到|标题|歌手|歌曲|歌词|歌名|图片|照片|上传|分享|专辑).*");
            if (hasDataRef && rest.length() > 20 && !rest.equals(raw.trim())) {
                log.info("去重兜底触发（规则2-矛盾），{}→{} 字符", raw.length(), rest.length());
                return rest;
            }
        }

        // ── 规则 3：过渡语 + 后续实质性内容（原规则1的重写） ──
        Pattern preface = Pattern.compile(
                "^.*?(?:我(?:帮你|来|去|给你).*?(?:查[一]?下|看看|搜索|检索|确认[一]?下|调[取]?数据)).*?[。！\\n]",
                Pattern.CASE_INSENSITIVE);
        Matcher m = preface.matcher(raw);
        if (m.find() && m.end() < raw.length() - 20) {
            String candidate = raw.substring(m.end()).trim();
            if (candidate.length() > 20) {
                log.info("去重兜底触发（规则3-过渡语），{}→{} 字符", raw.length(), candidate.length());
                return candidate;
            }
        }

        // ── 规则 4：检测明显的"转折重新开始"标记，保留后半段 ──
        String[] markers = {
            "好的，根据", "好了，我查", "我查了一下", "我帮你查", "我检索到",
            "根据你的记录", "从你的日记", "数据显示", "以下是你", "这里是你"
        };
        for (String marker : markers) {
            int idx = raw.indexOf(marker);
            if (idx > 15 && idx < raw.length() / 2) {
                String after = raw.substring(idx).trim();
                if (after.length() > 20) {
                    log.info("去重兜底触发（规则4-标记），marker=\"{}\" {}→{} 字符",
                            marker, raw.length(), after.length());
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
