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

import java.util.List;
import java.util.Map;

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

    public Flux<String> streamReasoner(List<Map<String, Object>> messages) {
        Map<String, Object> body = Map.of(
            "model", "deepseek-reasoner",
            "messages", messages,
            "stream", true
        );

        return Flux.defer(() -> {
            boolean[] thinkingStarted = {false};
            boolean[] thinkingEnded = {false};

            return webClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .mapNotNull(ServerSentEvent::data)
                .filter(data -> !"[DONE]".equals(data))
                .handle((data, sink) -> {
                    try {
                        JsonNode root = objectMapper.readTree(data);
                        JsonNode choices = root.path("choices");
                        if (choices.isArray() && !choices.isEmpty()) {
                            JsonNode delta = choices.get(0).path("delta");

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
                                sink.next(out.toString());
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse SSE data: {}", data, e);
                    }
                });
        });
    }
}
