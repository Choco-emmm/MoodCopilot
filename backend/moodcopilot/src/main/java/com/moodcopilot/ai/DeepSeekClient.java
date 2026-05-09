package com.moodcopilot.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class DeepSeekClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public DeepSeekClient(@Value("${ai.deepseek.api-key}") String apiKey,
                          @Value("${ai.deepseek.base-url}") String baseUrl,
                          @Value("${ai.deepseek.model}") String model,
                          ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl + "/v1")
                .build();
    }

    public String chat(String systemPrompt, String userMessage) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("DeepSeek API key not configured");
        }

        var requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userMessage)
                ),
                "temperature", 0.3,
                "max_tokens", 500
        );

        String response = restClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);

        try {
            var node = objectMapper.readTree(response);
            return node.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            log.warn("Failed to parse DeepSeek response: {}", response, e);
            throw new IllegalStateException("Failed to parse AI response");
        }
    }
}
