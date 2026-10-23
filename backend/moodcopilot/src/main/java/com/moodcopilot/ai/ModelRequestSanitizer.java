package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;

/** Sanitizes detached outbound message copies, never chat history, tool inputs or HTTP credentials. */
public final class ModelRequestSanitizer {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ObjectReader JSON_DOCUMENT = JSON.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private ModelRequestSanitizer() {}

    public static List<Map<String,Object>> messages(List<Map<String,Object>> original) {
        ArrayNode copy = JSON.valueToTree(original);
        for (JsonNode message : copy) sanitizeMessage((ObjectNode) message);
        return JSON.convertValue(copy, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    public static JsonNode payload(JsonNode original) {
        JsonNode copy = original.deepCopy();
        if (copy.path("messages").isArray()) for (JsonNode message : copy.path("messages")) sanitizeMessage((ObjectNode) message);
        return copy;
    }

    private static void sanitizeMessage(ObjectNode message) {
        for (String name : List.of("content", "reasoning_content")) {
            JsonNode value = message.get(name);
            if (value != null && value.isTextual()) message.put(name, text(value.asText()));
            else if (value != null && value.isArray()) for (JsonNode part : value) {
                if (part.isObject() && part.path("text").isTextual()) ((ObjectNode) part).put("text", text(part.path("text").asText()));
            }
        }
        if (message.path("tool_calls").isArray()) for (JsonNode call : message.path("tool_calls")) {
            JsonNode function = call.path("function");
            if (function.isObject() && function.path("arguments").isTextual()) {
                try { ((ObjectNode) function).put("arguments", JSON.writeValueAsString(jsonData(JSON_DOCUMENT.readTree(function.path("arguments").asText())))); }
                catch (Exception e) { throw new IllegalArgumentException("模型工具参数无法安全处理"); }
            }
        }
    }

    public static String text(String value) {
        if (value == null) return "";
        String trimmed = value.strip();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try { return JSON.writeValueAsString(jsonData(JSON_DOCUMENT.readTree(value))); }
            catch (com.fasterxml.jackson.core.JsonProcessingException ignored) { /* prose may start with a bracket */ }
        }
        return SensitiveDataDetector.redactCredentials(value);
    }

    private static JsonNode jsonData(JsonNode value) {
        if (value == null) return NullNode.instance;
        if (value.isTextual()) return TextNode.valueOf(SensitiveDataDetector.redactCredentials(value.asText()));
        if (value.isArray()) {
            ArrayNode next = JSON.createArrayNode(); value.forEach(item -> next.add(jsonData(item))); return next;
        }
        if (value.isObject()) {
            ObjectNode next = JSON.createObjectNode();
            value.fields().forEachRemaining(entry -> next.set(entry.getKey(), credentialField(entry.getKey())
                    && !entry.getValue().isNull() ? TextNode.valueOf("[已隐藏敏感信息]") : jsonData(entry.getValue())));
            return next;
        }
        return value.deepCopy();
    }

    private static boolean credentialField(String key) {
        return key.matches("(?i)(api[_ -]?key|access[_ -]?key|private[_ -]?key|client[_ -]?secret|access[_ -]?token|refresh[_ -]?token|secret|password|passwd|token|session[_ -]?token|authorization|bearer|密码|口令|密钥|私钥|令牌)");
    }
}
