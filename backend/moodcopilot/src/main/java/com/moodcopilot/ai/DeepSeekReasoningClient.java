package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class DeepSeekReasoningClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekReasoningClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public DeepSeekReasoningClient(
            @Value("${spring.ai.openai.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${DEEPSEEK_REASONING_MODEL:deepseek-v4-pro}") String model,
            ObjectMapper objectMapper) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        // 这里不走 Spring AI 的 ChatClient，直接用原生 HTTP 请求，是为了绕开 thinking/reasoning_content
        // 兼容问题。设置 90s 超时，低于 Cloudflare 的 100s 限制，避免推理模型长响应被 Cloudflare 截断后前端无感知等待。
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(90));
        this.restClient = RestClient.builder()
                .baseUrl(this.baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? "deepseek-v4-pro" : model.trim();
        this.objectMapper = objectMapper;
    }

    public String generate(String systemPrompt, String userPrompt) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("DeepSeek API key is empty");
        }

        long startedAt = AiCallTiming.start();
        int inputLength = (systemPrompt == null ? 0 : systemPrompt.length())
                + (userPrompt == null ? 0 : userPrompt.length());
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

            ResponseEntity<String> responseEntity = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(request)
                    .retrieve()
                    .toEntity(String.class);

            String response = responseEntity.getBody();
            logResponseShape(responseEntity.getStatusCode().value(), response);

            if (response == null || response.isBlank()) {
                throw new IllegalStateException("DeepSeek reasoning response is empty");
            }

            AiCallTiming.completed(log, "CHAT", model, startedAt, "SUCCESS", inputLength, response.length());
            return extractContent(response);
        } catch (RestClientResponseException e) {
            log.warn("Pro模型HTTP请求失败，model={}，status={}，responseLength={}，errorType={}",
                    model, e.getStatusCode().value(),
                    e.getResponseBodyAsString().length(), e.getClass().getSimpleName());
            AiCallTiming.failed(log, "CHAT", model, startedAt, e, inputLength);
            throw new IllegalStateException("DeepSeek reasoning request failed", e);
        } catch (Exception e) {
            AiCallTiming.failed(log, "CHAT", model, startedAt, e, inputLength);
            throw new IllegalStateException("DeepSeek reasoning request failed", e);
        }
    }

    /** Logs response structure only; never logs response content or prompt data. */
    private void logResponseShape(int status, String response) {
        if (response == null || response.isBlank()) {
            log.warn("Pro模型响应为空，model={}，status={}，responseLength=0", model, status);
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode choices = root.path("choices");
            int choiceCount = choices.isArray() ? choices.size() : 0;
            JsonNode choice = choiceCount == 0 ? null : choices.get(0);
            JsonNode message = choice == null ? null : choice.path("message");
            String content = message == null ? null : message.path("content").asText(null);
            String reasoning = message == null ? null : message.path("reasoning_content").asText(null);
            String finishReason = choice == null ? null : choice.path("finish_reason").asText(null);
            JsonNode error = root.path("error");
            String errorType = error.isObject() ? error.path("type").asText(null) : null;
            log.info("Pro模型响应结构，model={}，status={}，responseLength={}，choiceCount={}，contentLength={}，reasoningLength={}，finishReason={}，errorType={}",
                    model, status, response.length(), choiceCount,
                    content == null ? 0 : content.length(),
                    reasoning == null ? 0 : reasoning.length(),
                    finishReason, errorType);
        } catch (Exception parseError) {
            log.warn("Pro模型响应不是可解析JSON，model={}，status={}，responseLength={}，parseErrorType={}",
                    model, status, response.length(), parseError.getClass().getSimpleName());
        }
    }

    /**
     * 流式调用 DeepSeek API（stream=true），通过 Flux 实时推送每个 delta chunk。
     * 使用独立线程读取 InputStream，确保思考模型的 &lt;think&gt; 长过程不会触发网关超时。
     */
    public Flux<String> generateStream(String systemPrompt, String userPrompt) {
        if (apiKey.isBlank()) {
            return Flux.error(new IllegalStateException("DeepSeek API key is empty"));
        }

        return Flux.create(sink -> {
            long startedAt = AiCallTiming.start();
            AtomicBoolean logged = new AtomicBoolean();
            int inputLength = (systemPrompt == null ? 0 : systemPrompt.length())
                    + (userPrompt == null ? 0 : userPrompt.length());
            Runnable success = () -> {
                if (logged.compareAndSet(false, true)) {
                    AiCallTiming.completed(log, "CHAT_STREAM", model, startedAt, "SUCCESS", inputLength, 0);
                }
            };
            java.util.function.Consumer<Throwable> failure = error -> {
                if (logged.compareAndSet(false, true)) {
                    AiCallTiming.failed(log, "CHAT_STREAM", model, startedAt, error, inputLength);
                }
            };
            Thread streamThread = new Thread(() -> {
                try {
                    Map<String, Object> requestBody = Map.of(
                            "model", model,
                            "messages", List.of(
                                    Map.of("role", "system", "content", systemPrompt),
                                    Map.of("role", "user", "content", userPrompt)),
                            "temperature", 0.6,
                            "max_tokens", 2048,
                            "stream", true);

                    String json = objectMapper.writeValueAsString(requestBody);

                    HttpClient streamClient = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(10))
                            .build();

                    HttpRequest httpReq = HttpRequest.newBuilder()
                            .uri(java.net.URI.create(baseUrl + "/chat/completions"))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + apiKey)
                            .timeout(Duration.ofSeconds(90))
                            .POST(HttpRequest.BodyPublishers.ofString(json))
                            .build();

                    HttpResponse<java.io.InputStream> response = streamClient.send(httpReq,
                            HttpResponse.BodyHandlers.ofInputStream());

                    int statusCode = response.statusCode();
                    if (statusCode >= 400) {
                        String errorBody;
                        try (java.util.Scanner s = new java.util.Scanner(response.body()).useDelimiter("\\A")) {
                            errorBody = s.hasNext() ? s.next() : "";
                        }
                        log.warn("DeepSeek 推理模型流式请求失败 HTTP {}: {}", statusCode, errorBody);
                        sink.error(new IllegalStateException(
                                "DeepSeek reasoning API error " + statusCode));
                        failure.accept(new IllegalStateException("HTTP " + statusCode));
                        return;
                    }

                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.body()))) {
                        String line;
                        while ((line = reader.readLine()) != null && !sink.isCancelled()) {
                            if (line.startsWith("data: ") && !"data: [DONE]".equals(line)) {
                                String data = line.substring(6);
                                try {
                                    JsonNode root = objectMapper.readTree(data);
                                    JsonNode delta = root.path("choices").path(0).path("delta");
                                    String content = delta.path("content").asText(null);
                                    if (content != null && !content.isEmpty()) {
                                        sink.next(content);
                                    }
                                } catch (Exception e) {
                                    // 跳过无法解析的 SSE 行（如 reasoning_content 专用帧）
                                }
                            }
                        }
                    }
                    sink.complete();
                    success.run();
                } catch (Exception e) {
                    failure.accept(e);
                    if (!sink.isCancelled()) {
                        sink.error(e);
                    }
                }
            }, "deepseek-stream");
            streamThread.setDaemon(true);

            sink.onCancel(() -> {
                failure.accept(new IllegalStateException("cancelled"));
                streamThread.interrupt();
            });
            streamThread.start();
        }, FluxSink.OverflowStrategy.BUFFER);
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
