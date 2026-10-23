package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.config.AIConfiguration;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import static org.junit.jupiter.api.Assertions.*;

/** Captures actual HTTP bodies at a local provider; never calls a real AI or reads credentials. */
class ModelOutboundPrivacyTest {
    @Test void flashProAndSpringAiBoundariesSendOnlyDetachedSanitizedCopies() throws Exception {
        ObjectMapper json = new ObjectMapper();
        var received = new LinkedBlockingQueue<JsonNode>();
        var authorization = new LinkedBlockingQueue<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            JsonNode body = json.readTree(exchange.getRequestBody());
            received.add(body);
            authorization.add(exchange.getRequestHeaders().getFirst("Authorization"));
            boolean stream = body.path("stream").asBoolean();
            String response = stream ? "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}\n\ndata: [DONE]\n\n"
                    : "{\"choices\":[{\"message\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", stream ? "text/event-stream" : "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        try {
            var original = List.<Map<String,Object>>of(
                    Map.of("role", "user", "content", "配置信息：{\"密码\":\"synthetic-current\"}，我的口令是 'synthetic-quoted'"),
                    Map.of("role", "assistant", "content", "检查", "reasoning_content", "password=synthetic-history",
                            "tool_calls", List.of(Map.of("id", "call-a", "type", "function", "function", Map.of(
                                    "name", "example", "arguments", "{\"password\":\"synthetic-argument\",\"count\":2}")))),
                    Map.of("role", "tool", "tool_call_id", "call-a", "content", "{\"token\":\"synthetic-tool\",\"count\":2}"));
            String before = json.writeValueAsString(original);
            var options = new AgentLoopOptions("fixture-flash", 1024, null, null, 2, false, "CHAT_STREAM", "FLASH");
            new DeepSeekClient(WebClient.builder(), json, "fixture-auth", url).stream(original, List.of(), options)
                    .collectList().block(Duration.ofSeconds(10));
            JsonNode flash = received.remove();
            assertFalse(flash.toString().contains("synthetic-"));
            assertEquals("call-a", flash.path("messages").path(2).path("tool_call_id").asText());
            assertTrue(flash.path("messages").path(1).has("reasoning_content"));
            assertEquals(before, json.writeValueAsString(original));

            assertEquals("ok", new DeepSeekReasoningClient(url, "fixture-auth", "fixture-pro", 1024, json)
                    .generate("password=synthetic-system", "我的密钥是 ‘synthetic-diary’"));
            assertFalse(received.remove().toString().contains("synthetic-"));

            // This is the builder used by Spring AI diary Flash and other structured calls.
            var client = new AIConfiguration().webClientBuilder(json, 10).baseUrl(url)
                    .defaultHeader("Authorization", "Bearer fixture-auth").build();
            client.post().uri("/chat/completions").bodyValue(Map.of("messages", original))
                    .retrieve().bodyToMono(String.class).block(Duration.ofSeconds(10));
            assertFalse(received.remove().toString().contains("synthetic-"));
            assertEquals(List.of("Bearer fixture-auth", "Bearer fixture-auth", "Bearer fixture-auth"), new ArrayList<>(authorization));
            assertEquals(before, json.writeValueAsString(original));

            var malformed = List.of(Map.of("role", "assistant", "tool_calls", List.of(Map.of("function", Map.of("arguments", "{invalid")))));
            assertThrows(Exception.class, () -> client.post().uri("/chat/completions")
                    .bodyValue(Map.of("messages", malformed)).retrieve().bodyToMono(String.class).block(Duration.ofSeconds(10)));
            assertTrue(received.isEmpty(), "sanitizer failure must not send the original payload");
        } finally { server.stop(0); }
    }
}
