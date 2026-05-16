package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class DeepSeekReasoningClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekReasoningClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public DeepSeekReasoningClient(
            @Value("${spring.ai.openai.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${DEEPSEEK_REASONING_MODEL:deepseek-reasoner}") String model,
            ObjectMapper objectMapper) {
        // 这里不走 Spring AI 的 ChatClient，直接用原生 HTTP 请求，是为了绕开 thinking/reasoning_content
        // 兼容问题。设置 90s 超时，低于 Cloudflare 的 100s 限制，避免推理模型长响应被 Cloudflare 截断后前端无感知等待。
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(90));
        this.restClient = RestClient.builder()
                .baseUrl(normalizeBaseUrl(baseUrl))
                .requestFactory(requestFactory)
                .build();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? "deepseek-reasoner" : model.trim();
        this.objectMapper = objectMapper;
    }

    public String generate(String systemPrompt, String userPrompt) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("DeepSeek API key is empty");
        }

        try {
            // 请求体尽量保持和 OpenAI 兼容接口一致，便于后续替换/调试。
            Map<String, Object> request = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)),
                    "temperature", 0.6,
                    "max_tokens", 2048,
                    "stream", false);

            log.info("调用思考模型，model={}，systemLength={}，userLength={}", model,
                    systemPrompt == null ? 0 : systemPrompt.length(),
                    userPrompt == null ? 0 : userPrompt.length());

            String response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(request)
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                throw new IllegalStateException("DeepSeek reasoning response is empty");
            }

            log.info("思考模型返回成功，rawResponseLength={}", response.length());
            return extractContent(response);
        } catch (Exception e) {
            log.warn("DeepSeek reasoning request failed: {}", e.getMessage());
            throw new IllegalStateException("DeepSeek reasoning request failed", e);
        }
    }

    private String extractContent(String response) throws Exception {
        // 兼容不同返回结构：标准 OpenAI 格式放在 choices[0].message.content。
        JsonNode root = objectMapper.readTree(response);
        JsonNode choice = root.path("choices").path(0);
        String content = choice.path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            content = choice.path("content").asText(null);
        }
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("DeepSeek reasoning response missing content");
        }
        return content.trim();
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://api.deepseek.com";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}