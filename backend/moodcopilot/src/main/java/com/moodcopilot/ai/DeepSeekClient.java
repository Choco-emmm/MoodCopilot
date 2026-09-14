package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import java.util.concurrent.atomic.AtomicInteger;

import java.util.*;

@Component
public class DeepSeekClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public DeepSeekClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.base-url:https://api.deepseek.com}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    /**
     * 统一的流式入口：模型、预算、温度、reasoning_effort 全部来自 options。
     * temperature / reasoning_effort 为 null 时不下发该字段，模型沿用端点默认值
     * —— 这也是 flash 保持改造前行为（端点默认温度、不下发 reasoning_effort）的关键。
     */
    public Flux<DeepSeekStreamEvent> stream(List<Map<String, Object>> messages,
                                            List<Map<String, Object>> tools,
                                            AgentLoopOptions options) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", options.model());
        body.put("messages", messages);
        body.put("stream", true);
        if (options.reasoningEffort() != null && !options.reasoningEffort().isBlank()) {
            body.put("reasoning_effort", options.reasoningEffort());
        }
        if (options.temperature() != null) {
            body.put("temperature", options.temperature());
        }
        body.put("max_tokens", options.maxTokens());
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
        }

        return Flux.defer(() -> {
            long startedAt = AiCallTiming.start();
            AtomicInteger outputLength = new AtomicInteger();
            Map<Integer, ToolCallAccumulator> toolCallAccs = new LinkedHashMap<>();

            return webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                    .mapNotNull(ServerSentEvent::data)
                    .filter(data -> !"[DONE]".equals(data))
                    .<DeepSeekStreamEvent>handle((data, sink) -> {
                        try {
                            JsonNode root = objectMapper.readTree(data);
                            JsonNode choices = root.path("choices");
                            if (!choices.isArray() || choices.isEmpty()) return;
                            JsonNode delta = choices.get(0).path("delta");

                            JsonNode toolCalls = delta.path("tool_calls");
                            if (toolCalls.isArray() && !toolCalls.isEmpty()) {
                                for (JsonNode tc : toolCalls) {
                                    int index = tc.path("index").asInt();
                                    ToolCallAccumulator acc = toolCallAccs.computeIfAbsent(index,
                                            k -> new ToolCallAccumulator());
                                    String id = tc.path("id").asText(null);
                                    if (id != null && !id.isEmpty()) acc.id = id;
                                    JsonNode fn = tc.path("function");
                                    String name = fn.path("name").asText(null);
                                    if (name != null && !name.isEmpty()) acc.functionName = name;
                                    String args = fn.path("arguments").asText(null);
                                    if (args != null) acc.arguments.append(args);
                                }
                                return;
                            }

                            String reasoning = delta.path("reasoning_content").asText("");
                            String content = delta.path("content").asText("");

                            if (!reasoning.isEmpty()) {
                                outputLength.addAndGet(reasoning.length());
                                sink.next(new DeepSeekStreamEvent.TextChunk("[[REASONING]]" + reasoning));
                            }
                            if (!content.isEmpty()) {
                                outputLength.addAndGet(content.length());
                                sink.next(new DeepSeekStreamEvent.TextChunk(content));
                            }
                        } catch (Exception e) {
                            log.warn("Failed to parse SSE data: {}", data, e);
                        }
                    })
                    .concatWith(Flux.defer(() -> {
                        if (toolCallAccs.isEmpty()) return Flux.<DeepSeekStreamEvent>empty();
                        List<DeepSeekStreamEvent> events = new ArrayList<>();
                        for (ToolCallAccumulator acc : toolCallAccs.values()) {
                            if (acc.id != null && acc.functionName != null) {
                                events.add(new DeepSeekStreamEvent.ToolCallReady(
                                        acc.id, acc.functionName, acc.arguments.toString()));
                            }
                        }
                        if (!events.isEmpty()) {
                            log.info("reasoning model emitted {} tool call(s)", events.size());
                        }
                        return Flux.fromIterable(events);
                    }))
                    .doOnComplete(() -> AiCallTiming.completed(log, options.logType(), options.model(),
                            startedAt, "SUCCESS", estimateInputLength(messages), outputLength.get()))
                    .doOnError(error -> AiCallTiming.failed(log, options.logType(), options.model(),
                            startedAt, error, estimateInputLength(messages)));
        });
    }

    private static class ToolCallAccumulator {
        String id;
        String functionName;
        final StringBuilder arguments = new StringBuilder();
    }

    private int estimateInputLength(List<Map<String, Object>> messages) {
        if (messages == null) return 0;
        return messages.stream().mapToInt(message -> message == null ? 0 : String.valueOf(message).length()).sum();
    }
}
