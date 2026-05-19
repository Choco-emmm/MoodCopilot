package com.moodcopilot.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class TurnstileService {

    private static final Logger log = LoggerFactory.getLogger(TurnstileService.class);
    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final String secretKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TurnstileService(@Value("${turnstile.secret-key}") String secretKey,
            ObjectMapper objectMapper) {
        this.secretKey = secretKey;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * 验证 Cloudflare Turnstile token。返回 true 表示验证通过。
     */
    public boolean verify(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            String body = "secret=" + secretKey + "&response=" + token;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(VERIFY_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());
            boolean success = json.path("success").asBoolean(false);
            if (!success) {
                log.warn("Turnstile 验证失败: {}", json.path("error-codes").toString());
            }
            return success;
        } catch (Exception e) {
            log.warn("Turnstile 验证异常: {}", e.getMessage());
            return false;
        }
    }
}
