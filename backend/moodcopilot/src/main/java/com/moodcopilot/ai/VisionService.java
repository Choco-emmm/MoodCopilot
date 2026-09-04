package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.DiaryImageMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import com.moodcopilot.oss.OssService;
import org.springframework.context.annotation.Lazy;

/**
 * 视觉模型服务：双路由架构。
 * - 常规模型（model）：描述画面与情感氛围
 * - OCR 模型（ocrModel）：精确提取图片中的文字，低温度防幻觉
 * <p>
 * 支持阿里云百炼 / SiliconFlow 等 OpenAI 兼容接口，通过配置切换。
 * VLM 失败不抛异常，返回空字符串，确保文本分析不受影响。
 */
@Service
public class VisionService {

    private record VisionImageTask(int index, String imageUrl, String channel) {
    }

    private static final Logger log = LoggerFactory.getLogger(VisionService.class);

    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final String ocrModel;
    private final int ocrMaxTokens;
    private final boolean enableOcr;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final OssService ossService;

    public VisionService(
            @Value("${moodcopilot.vision.api-key:}") String apiKey,
            @Value("${moodcopilot.vision.api-url:https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions}") String apiUrl,
            @Value("${moodcopilot.vision.model:qwen3-vl-flash}") String model,
            @Value("${moodcopilot.vision.ocr-model:qwen-vl-ocr}") String ocrModel,
            @Value("${moodcopilot.vision.ocr-max-tokens:2048}") int ocrMaxTokens,
            @Value("${moodcopilot.vision.enable-ocr-for-text-images:true}") boolean enableOcr,
            @Value("${moodcopilot.ai.http-timeout-seconds:90}") int httpTimeoutSeconds,
            ObjectMapper objectMapper,
            @Lazy OssService ossService) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.apiUrl = apiUrl;
        this.model = model;
        this.ocrModel = ocrModel;
        this.ocrMaxTokens = ocrMaxTokens > 0 ? ocrMaxTokens : 2048;
        this.enableOcr = enableOcr;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(Math.max(1, httpTimeoutSeconds)));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
        this.ossService = ossService;
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    /**
     * 描述多张图片，返回合并的文本。单张失败静默跳过。
     * 当 enableOcr=true 时，先尝试 OCR 提取文字，再将文字注入常规模型 prompt。
     */
    public String describeImages(List<String> imageUrls) {
        return describeImages(imageUrls, null);
    }

    public String describeImages(List<String> imageUrls, List<DiaryImageMeta> imageMeta) {
        if (imageUrls == null || imageUrls.isEmpty())
            return "";
        if (!isConfigured()) {
            log.warn("VLM 未配置（VISION_API_KEY 为空），跳过 {} 张图片的描述", imageUrls.size());
            return "";
        }
        List<VisionImageTask> tasks = buildImageTasks(imageUrls, imageMeta);
        long textCount = tasks.stream().filter(task -> "text".equals(task.channel())).count();
        long nonTextCount = tasks.size() - textCount;
        log.info("VLM 开始描述 {} 张图片 model={} ocrModel={} enableOcr={} textImages={} otherImages={}", imageUrls.size(),
                model, ocrModel, enableOcr, textCount, nonTextCount);

        List<String> parts = new ArrayList<>();
        try (ExecutorService executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Map.Entry<Integer, String>>> futures = tasks.stream()
                    .map(task -> CompletableFuture.supplyAsync(() -> {
                        String accessibleUrl = ossService != null ? ossService.getAccessibleUrl(task.imageUrl())
                                : task.imageUrl();
                        String desc = describeWithOcrRouting(accessibleUrl, task.index(), task.channel());
                        return Map.entry(task.index(), desc);
                    }, executor))
                    .toList();

            futures.stream()
                    .map(CompletableFuture::join)
                    .sorted(Comparator.comparingInt(Map.Entry::getKey))
                    .map(Map.Entry::getValue)
                    .filter(desc -> desc != null && !desc.isBlank())
                    .forEach(parts::add);
        }
        String result = parts.isEmpty() ? "" : String.join("; ", parts);
        if (!result.isBlank()) {
            log.info("VLM 图片描述完成 {} 张 → {} chars", parts.size(), result.length());
        }
        return result;
    }

    /**
     * 单张图片描述：OCR 路由 → 常规模型。
     * OCR 阶段专注提取文字，常规模型阶段只描述画面视觉。
     * 返回时 OCR 文字和视觉描述用标签分隔，方便下游分析模型区分处理。
     */
    private String describeWithOcrRouting(String imageUrl, int index, String channel) {
        // 尝试 OCR 提取文字
        String extractedText = "";
        boolean shouldUseOcr = enableOcr && "text".equals(channel);
        if (shouldUseOcr) {
            extractedText = analyzeWithOcr(imageUrl, index);
        }

        // 常规模型只描述画面视觉，不混入 OCR 文字
        String visualPrompt = "请用一句话描述这张图片的画面内容、拍摄类型（如自拍/风景/美食/截图/手写等）与情感氛围（40字以内）。不要评价图片质量。" +
                "注意：只需要描述你看到的画面本身，不要提及画面中的文字内容。";
        String visualDesc = callVisionModel(model, imageUrl, visualPrompt, 80, 0.3, "图片描述");

        // OCR 文字和视觉描述用标签分隔
        if (!extractedText.isBlank() && !visualDesc.isBlank()) {
            log.info("VLM OCR+视觉双通道完成 index={} ocrLen={} visualLen={}", index, extractedText.length(),
                    visualDesc.length());
            return "[视觉] " + visualDesc + " [OCR文字] " + extractedText;
        } else if (!visualDesc.isBlank()) {
            return visualDesc;
        } else {
            return "";
        }
    }

    private List<VisionImageTask> buildImageTasks(List<String> imageUrls, List<DiaryImageMeta> imageMeta) {
        List<VisionImageTask> tasks = new ArrayList<>();
        for (int i = 0; i < imageUrls.size(); i++) {
            String url = imageUrls.get(i);
            String channel = "legacy";
            if (imageMeta != null && i < imageMeta.size() && imageMeta.get(i) != null) {
                String raw = imageMeta.get(i).getChannel();
                if (raw != null && !raw.isBlank()) {
                    channel = raw.trim().toLowerCase();
                }
            }
            tasks.add(new VisionImageTask(i + 1, url, channel));
        }
        return tasks;
    }

    /**
     * OCR 专用分析：使用 qwen-vl-ocr 模型，极低温度，专注文字提取。
     * 失败时静默返回空字符串，由上层回退到常规模型。
     */
    private String analyzeWithOcr(String imageUrl, int index) {
        try {
            String finalUrl = fetchImageAsBase64Uri(imageUrl);
            String prompt = "请逐字提取并输出这张图片中所有可见的文字内容（包括手写文字、印刷文字、屏幕文字、海报标题、正文、按钮、日期、地点和联系方式）。" +
                    "请按照从上到下、从左到右的阅读顺序完整输出，保留原有换行和段落；禁止概括、改写、补全、合并或省略任何文字，尤其不要漏掉海报下方和角落里的小字。" +
                    "只输出识别到的原文，不要添加解释、评价或描述。如果图片中没有文字，请输出‘无文字’。";

            Map<String, Object> userMsg = Map.of("role", "user", "content", List.of(
                    Map.of("type", "text", "text", prompt),
                    Map.of("type", "image_url", "image_url", Map.of("url", finalUrl))));
            Map<String, Object> body = Map.of(
                    "model", ocrModel,
                    "messages", List.of(userMsg),
                    "max_tokens", ocrMaxTokens,
                    "temperature", 0.01);

            String response = restClient.post()
                    .uri(apiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank())
                return "";
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(response, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) parsed.get("choices");
            if (choices == null || choices.isEmpty())
                return "";
            Object finishReason = choices.get(0).get("finish_reason");
            if ("length".equals(finishReason)) {
                log.warn("VLM OCR 输出达到 max_tokens 限制 index={} maxTokens={}", index, ocrMaxTokens);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
            if (msg == null)
                return "";
            String content = (String) msg.get("content");
            if (content == null)
                return "";

            content = content.trim();
            // OCR 模型返回"无文字"视为空
            if (content.equals("无文字") || content.equals("无文字。") || content.isBlank()) {
                return "";
            }
            log.info("VLM OCR 提取成功 index={} textLen={}", index, content.length());
            return content;
        } catch (Exception e) {
            log.warn("VLM OCR 提取失败 index={}, 回退到常规模型: {}", index, e.getMessage());
            return "";
        }
    }

    /**
     * 通用 VLM 调用
     */
    private String callVisionModel(String modelName, String imageUrl, String prompt, int maxTokens, double temperature,
            String logTag) {
        try {
            String finalUrl = fetchImageAsBase64Uri(imageUrl);

            Map<String, Object> userMsg = Map.of("role", "user", "content", List.of(
                    Map.of("type", "text", "text", prompt),
                    Map.of("type", "image_url", "image_url", Map.of("url", finalUrl))));
            Map<String, Object> body = Map.of(
                    "model", modelName,
                    "messages", List.of(userMsg),
                    "max_tokens", maxTokens,
                    "temperature", temperature);

            String response = restClient.post()
                    .uri(apiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank())
                return "";
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(response, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) parsed.get("choices");
            if (choices == null || choices.isEmpty())
                return "";
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
            if (msg == null)
                return "";
            String content = (String) msg.get("content");
            return content != null ? content.trim() : "";
        } catch (Exception e) {
            log.warn("VLM {} 失败 url={}: {}", logTag, imageUrl, e.getMessage());
            return "";
        }
    }

    /**
     * 针对性提问分析多张图片，返回合并的文本。单张失败静默跳过。
     */
    public String analyzeImageDetails(List<String> imageUrls, String targetedPrompt) {
        if (imageUrls == null || imageUrls.isEmpty())
            return "该日记没有附带图片";
        if (!isConfigured()) {
            log.warn("VLM 未配置（VISION_API_KEY 为空），跳过 {} 张图片的深度分析", imageUrls.size());
            return "系统后台视觉服务未配置，无法分析图片";
        }
        log.info("VLM 开始深度分析 {} 张图片 model={} promptLength={}", imageUrls.size(), model,
                targetedPrompt != null ? targetedPrompt.length() : 0);
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < imageUrls.size(); i++) {
            String accessibleUrl = ossService != null ? ossService.getAccessibleUrl(imageUrls.get(i))
                    : imageUrls.get(i);
            String desc = callVisionModel(model, accessibleUrl, targetedPrompt, 300, 0.5, "深度分析");
            if (!desc.isBlank()) {
                parts.add("图片" + (i + 1) + ": " + desc);
            }
        }
        String result = parts.isEmpty() ? "未提取到有效信息" : String.join("; ", parts);
        if (!result.isBlank()) {
            log.info("VLM 图片深度分析完成 {} 张 → {} chars", parts.size(), result.length());
        }
        return result;
    }

    private String fetchImageAsBase64Uri(String imageUrl) {
        if (imageUrl == null || imageUrl.startsWith("data:"))
            return imageUrl;
        try {
            byte[] bytes = restClient.get()
                    .uri(java.net.URI.create(imageUrl))
                    .retrieve()
                    .body(byte[].class);
            if (bytes != null && bytes.length > 0) {
                String base64 = java.util.Base64.getEncoder().encodeToString(bytes);
                return "data:image/jpeg;base64," + base64;
            }
        } catch (Exception e) {
            log.warn("VLM 后台下载图片转 Base64 失败 url={}: {}", imageUrl, e.getMessage());
        }
        return imageUrl;
    }
}
