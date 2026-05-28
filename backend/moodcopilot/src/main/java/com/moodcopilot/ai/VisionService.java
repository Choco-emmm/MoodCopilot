package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private static final Logger log = LoggerFactory.getLogger(VisionService.class);

    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final String ocrModel;
    private final boolean enableOcr;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final OssService ossService;

    public VisionService(
            @Value("${moodcopilot.vision.api-key:}") String apiKey,
            @Value("${moodcopilot.vision.api-url:https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions}") String apiUrl,
            @Value("${moodcopilot.vision.model:qwen3-vl-flash}") String model,
            @Value("${moodcopilot.vision.ocr-model:qwen-vl-ocr}") String ocrModel,
            @Value("${moodcopilot.vision.enable-ocr-for-text-images:true}") boolean enableOcr,
            ObjectMapper objectMapper,
            @Lazy OssService ossService) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.apiUrl = apiUrl;
        this.model = model;
        this.ocrModel = ocrModel;
        this.enableOcr = enableOcr;
        this.restClient = RestClient.builder().build();
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
        if (imageUrls == null || imageUrls.isEmpty()) return "";
        if (!isConfigured()) {
            log.warn("VLM 未配置（VISION_API_KEY 为空），跳过 {} 张图片的描述", imageUrls.size());
            return "";
        }
        log.info("VLM 开始描述 {} 张图片 model={} ocrModel={} enableOcr={}", imageUrls.size(), model, ocrModel, enableOcr);
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < imageUrls.size(); i++) {
            String accessibleUrl = ossService != null ? ossService.getAccessibleUrl(imageUrls.get(i)) : imageUrls.get(i);
            String desc = describeWithOcrRouting(accessibleUrl, i + 1);
            if (!desc.isBlank()) {
                parts.add("图片" + (i + 1) + ": " + desc);
            }
        }
        String result = parts.isEmpty() ? "" : String.join("; ", parts);
        if (!result.isBlank()) {
            log.info("VLM 图片描述完成 {} 张 → {} chars", parts.size(), result.length());
        }
        return result;
    }

    /**
     * 单张图片描述：OCR 路由 → 常规模型。
     * OCR 阶段专注提取文字，常规模型阶段结合文字做情感氛围分析。
     */
    private String describeWithOcrRouting(String imageUrl, int index) {
        // 尝试 OCR 提取文字
        String extractedText = "";
        if (enableOcr) {
            extractedText = analyzeWithOcr(imageUrl, index);
        }

        // 构建 prompt，如有 OCR 文字则注入
        String prompt;
        if (!extractedText.isBlank()) {
            prompt = String.format(
                "请用一句话描述这张图片的画面内容、拍摄类型（如自拍/风景/美食/截图/手写等）与情感氛围（40字以内）。" +
                "图片中已识别到以下文字内容：「%s」" +
                "请结合这些文字和画面综合描述情感。不要评价图片质量。",
                extractedText
            );
            log.info("VLM OCR 文字已注入 prompt index={} textLen={}", index, extractedText.length());
        } else {
            prompt = "请用一句话描述这张图片的画面内容、拍摄类型（如自拍/风景/美食/截图/手写等）与情感氛围（40字以内）。" +
                     "如果图片包含文字，请结合文字内容描述情感。不要评价图片质量。";
        }

        return callVisionModel(model, imageUrl, prompt, 80, 0.3, "图片描述");
    }

    /**
     * OCR 专用分析：使用 qwen-vl-ocr 模型，极低温度，专注文字提取。
     * 失败时静默返回空字符串，由上层回退到常规模型。
     */
    private String analyzeWithOcr(String imageUrl, int index) {
        try {
            String finalUrl = fetchImageAsBase64Uri(imageUrl);
            String prompt = "请直接提取并输出这张图片中所有可见的文字内容（包括手写文字、印刷文字、屏幕文字）。" +
                            "只输出原文，不要添加任何解释、评价或描述。如果图片中没有文字，请输出「无文字」。";

            Map<String, Object> userMsg = Map.of("role", "user", "content", List.of(
                    Map.of("type", "text", "text", prompt),
                    Map.of("type", "image_url", "image_url", Map.of("url", finalUrl))
            ));
            Map<String, Object> body = Map.of(
                    "model", ocrModel,
                    "messages", List.of(userMsg),
                    "max_tokens", 200,
                    "temperature", 0.01
            );

            String response = restClient.post()
                    .uri(apiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) return "";
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(response, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) parsed.get("choices");
            if (choices == null || choices.isEmpty()) return "";
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
            if (msg == null) return "";
            String content = (String) msg.get("content");
            if (content == null) return "";

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
    private String callVisionModel(String modelName, String imageUrl, String prompt, int maxTokens, double temperature, String logTag) {
        try {
            String finalUrl = fetchImageAsBase64Uri(imageUrl);

            Map<String, Object> userMsg = Map.of("role", "user", "content", List.of(
                    Map.of("type", "text", "text", prompt),
                    Map.of("type", "image_url", "image_url", Map.of("url", finalUrl))
            ));
            Map<String, Object> body = Map.of(
                    "model", modelName,
                    "messages", List.of(userMsg),
                    "max_tokens", maxTokens,
                    "temperature", temperature
            );

            String response = restClient.post()
                    .uri(apiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) return "";
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(response, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) parsed.get("choices");
            if (choices == null || choices.isEmpty()) return "";
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
            if (msg == null) return "";
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
        if (imageUrls == null || imageUrls.isEmpty()) return "该日记没有附带图片";
        if (!isConfigured()) {
            log.warn("VLM 未配置（VISION_API_KEY 为空），跳过 {} 张图片的深度分析", imageUrls.size());
            return "系统后台视觉服务未配置，无法分析图片";
        }
        log.info("VLM 开始深度分析 {} 张图片 model={} prompt={}", imageUrls.size(), model, targetedPrompt);
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < imageUrls.size(); i++) {
            String accessibleUrl = ossService != null ? ossService.getAccessibleUrl(imageUrls.get(i)) : imageUrls.get(i);
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
        if (imageUrl == null || imageUrl.startsWith("data:")) return imageUrl;
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
