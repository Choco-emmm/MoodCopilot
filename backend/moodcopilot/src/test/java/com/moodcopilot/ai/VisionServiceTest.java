package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.*;

class VisionServiceTest {
    private static final String IMAGE = "data:image/png;base64,aW1hZ2U=";
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConcurrentLinkedQueue<JsonNode> requests = new ConcurrentLinkedQueue<>();
    private HttpServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            try {
                JsonNode request = mapper.readTree(exchange.getRequestBody());
                requests.add(request);
                // Reproduce DeepSeek consuming the short output budget on default reasoning.
                boolean reasoningExhausted = request.path("model").asText().startsWith("deepseek-")
                        && !"disabled".equals(request.path("thinking").path("type").asText());
                byte[] response = mapper.writeValueAsBytes(java.util.Map.of("choices", List.of(
                        java.util.Map.of("finish_reason", reasoningExhausted ? "length" : "stop",
                                "message", java.util.Map.of("content", reasoningExhausted ? "" : "餐盒和米饭")))));
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    private VisionService service(String model) {
        return new VisionService("test-key", "http://127.0.0.1:" + server.getAddress().getPort()
                + "/chat/completions", model, model, 2048, true, 5, mapper, null, null);
    }

    private void assertRequest(String model, int maxTokens) {
        JsonNode request = requests.remove();
        assertEquals(model, request.path("model").asText());
        assertEquals(maxTokens, request.path("max_tokens").asInt());
        assertEquals(IMAGE, request.path("messages").get(0).path("content").get(1)
                .path("image_url").path("url").asText());
        if (model.startsWith("deepseek-")) {
            assertEquals("disabled", request.path("thinking").path("type").asText());
        } else {
            assertFalse(request.has("thinking"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"deepseek-flash", "qwen3-vl-plus"})
    void descriptionReturnsTextWithinItsShortBudget(String model) {
        assertEquals("餐盒和米饭", service(model).describeImages(List.of(IMAGE)));
        assertRequest(model, 80);
    }

    @ParameterizedTest
    @ValueSource(strings = {"deepseek-flash", "qwen3-vl-plus"})
    void ocrUsesTheConfiguredModelWithoutReasoning(String model) {
        assertEquals("餐盒和米饭", service(model).extractText(List.of(IMAGE), null));
        assertRequest(model, 2048);
    }

    @ParameterizedTest
    @ValueSource(strings = {"deepseek-flash", "qwen3-vl-plus"})
    void detailedImageAnalysisReturnsText(String model) {
        assertEquals("餐盒和米饭", service(model).analyzeImageDetails(List.of(IMAGE), null, null));
        assertRequest(model, 1024);
    }
}
