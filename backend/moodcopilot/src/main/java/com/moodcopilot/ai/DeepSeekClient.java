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
    private final String reasoningModel;
    private final int reasoningMaxTokens;

    public DeepSeekClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${DEEPSEEK_REASONING_MODEL:deepseek-v4-pro}") String reasoningModel,
            @Value("${spring.ai.reasoning.max-tokens:32768}") int reasoningMaxTokens) {
        this.objectMapper = objectMapper;
        this.reasoningModel = reasoningModel == null || reasoningModel.isBlank() ? "deepseek-v4-pro" : reasoningModel.trim();
        this.reasoningMaxTokens = Math.max(1024, reasoningMaxTokens);
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    public Flux<DeepSeekStreamEvent> streamReasoner(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", this.reasoningModel);
        body.put("messages", messages);
        body.put("stream", true);
        body.put("reasoning_effort", "high");
        body.put("max_tokens", reasoningMaxTokens);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
        }

        return Flux.defer(() -> {
            long startedAt = AiCallTiming.start();
            AtomicInteger outputLength = new AtomicInteger();
            boolean[] thinkingStarted = {false};
            boolean[] thinkingEnded = {false};
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

                            StringBuilder out = new StringBuilder();
                            if (!reasoning.isEmpty()) {
                                if (!thinkingStarted[0]) {
                                    thinkingStarted[0] = true;
                                    out.append("<think>\n");
                                }
                                out.append(reasoning);
                            }
                            if (!content.isEmpty()) {
                                if (thinkingStarted[0] && !thinkingEnded[0]) {
                                    thinkingEnded[0] = true;
                                    out.append("\n</think>\n\n");
                                }
                                out.append(content);
                            }
                            if (out.length() > 0) {
                                String emitted = out.toString();
                                outputLength.addAndGet(emitted.length());
                                sink.next(new DeepSeekStreamEvent.TextChunk(emitted));
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
                    .doOnComplete(() -> AiCallTiming.completed(log, "CHAT_AGENT_STREAM", reasoningModel,
                            startedAt, "SUCCESS", estimateInputLength(messages), outputLength.get()))
                    .doOnError(error -> AiCallTiming.failed(log, "CHAT_AGENT_STREAM", reasoningModel,
                            startedAt, error, estimateInputLength(messages)));
        });
    }

    /** Overload for backward compat with callers that don't pass tools. */
    public Flux<DeepSeekStreamEvent> streamReasoner(List<Map<String, Object>> messages) {
        return streamReasoner(messages, Collections.emptyList());
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
