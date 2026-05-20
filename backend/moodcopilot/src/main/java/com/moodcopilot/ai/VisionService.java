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

/**
 * 调用视觉模型描述图片画面与情感氛围。
 * 支持阿里云百炼 / SiliconFlow 等 OpenAI 兼容接口，通过配置切换。
 * VLM 失败不抛异常，返回空字符串，确保文本分析不受影响。
 */
@Service
public class VisionService {

    private static final Logger log = LoggerFactory.getLogger(VisionService.class);

    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public VisionService(
            @Value("${moodcopilot.vision.api-key:}") String apiKey,
            @Value("${moodcopilot.vision.api-url:https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions}") String apiUrl,
            @Value("${moodcopilot.vision.model:qwen3-vl-flash}") String model,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.apiUrl = apiUrl;
        this.model = model;
        this.restClient = RestClient.builder().build();
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    /**
     * 描述多张图片，返回合并的文本。单张失败静默跳过。
     */
    public String describeImages(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) return "";
        if (!isConfigured()) {
            log.warn("VLM 未配置（VISION_API_KEY 为空），跳过 {} 张图片的描述", imageUrls.size());
            return "";
        }
        log.info("VLM 开始描述 {} 张图片 model={}", imageUrls.size(), model);
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < imageUrls.size(); i++) {
            String desc = describe(imageUrls.get(i));
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

    private String describe(String imageUrl) {
        try {
            Map<String, Object> userMsg = Map.of("role", "user", "content", List.of(
                    Map.of("type", "text", "text", "请用一句话描述这张图片的画面内容与情感氛围（30字以内）。不要评价图片质量，只描述你看到的场景和感受。"),
                    Map.of("type", "image_url", "image_url", Map.of("url", imageUrl))
            ));
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(userMsg),
                    "max_tokens", 80,
                    "temperature", 0.3
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
            log.warn("VLM 图片描述失败 url={}: {}", imageUrl, e.getMessage());
            return "";
        }
    }
}
