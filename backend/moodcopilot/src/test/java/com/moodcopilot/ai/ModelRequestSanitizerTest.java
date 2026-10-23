package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ModelRequestSanitizerTest {
    @Test void recognizesConcreteCredentialsAndSignedLinksWithoutEchoingSecrets() {
        for (String input : List.of("配置：{\"password\":\"fixture-secret\"}", "Bearer fixture-secret",
                "Authorization: Bearer fixture-secret", "Authorization: Basic fixture-secret",
                "{\"access_token\":\"fixture-secret\"}", "refresh_token=fixture-secret", "私钥是fixture-secret",
                "https://example.com/object?X-Amz-Signature=fixture-secret", "我的令牌为fixture-secret")) {
            assertTrue(SensitiveDataDetector.containsSensitiveData(input));
            assertFalse(ModelRequestSanitizer.text(input).contains("fixture-secret"));
        }
    }
    @Test void outboundCopyPreservesProtocolAndDoesNotMutateHistoryOrAttachments() throws Exception {
        var messages = List.<Map<String,Object>>of(
                Map.of("role", "user", "content", "我的密码是 synthetic-current"),
                Map.of("role", "assistant", "content", "先检查", "reasoning_content", "检查 password=synthetic-history", "tool_calls",
                        List.of(Map.of("id", "call-1", "type", "function", "function", Map.of("name", "example", "arguments", "{\"password\":\"synthetic-argument\",\"count\":2}")))),
                Map.of("role", "tool", "tool_call_id", "call-1", "content", "{\"token\":\"synthetic-tool\",\"count\":2}"),
                Map.of("role", "user", "content", List.of(Map.of("type", "image_url", "image_url", Map.of("url", "https://example.com/image.png")))));
        String before = new ObjectMapper().writeValueAsString(messages);
        var sanitized = ModelRequestSanitizer.messages(messages);
        String sent = new ObjectMapper().writeValueAsString(sanitized);
        assertFalse(sent.contains("synthetic-"));
        assertEquals(before, new ObjectMapper().writeValueAsString(messages));
        assertEquals("call-1", sanitized.get(2).get("tool_call_id"));
        assertEquals("先检查", sanitized.get(1).get("content"));
        assertTrue(sent.contains("image.png"));
        assertTrue(sent.contains("reasoning_content"));
        assertEquals(2, new ObjectMapper().readTree((String)sanitized.get(2).get("content")).path("count").asInt());
    }

    @Test void ordinaryDiscussionAndContactsKeepTheirOutboundSemantics() {
        assertEquals("我忘记密码了，想学习 Token 原理", ModelRequestSanitizer.text("我忘记密码了，想学习 Token 原理"));
        assertEquals("someone@example.com", ModelRequestSanitizer.text("someone@example.com"));
        assertTrue(SensitiveDataDetector.containsSensitiveData("someone@example.com"));
        assertFalse(SensitiveDataDetector.containsSensitiveData("学习 local 变量与 Token 原理"));
        String escaped = ModelRequestSanitizer.text("<text>https://example.com/object?a=1&amp;X-Amz-Signature=fixture-secret</text>");
        assertFalse(escaped.contains("fixture-secret"));
        assertTrue(escaped.endsWith("</text>"));
    }

    @Test void jsonFollowedByUserInstructionsIsPreservedAsProse() {
        for (String input : List.of("{\"name\":\"Alice\"}\nPlease write a TypeScript interface for this JSON.",
                "[1,2,3]\n请解释这些数值", "{\"first\":1} {\"second\":2}")) {
            assertEquals(input, ModelRequestSanitizer.text(input));
            var sent = ModelRequestSanitizer.messages(List.of(Map.of("role", "user", "content", input)));
            assertEquals(input, sent.get(0).get("content"));
        }
        String protectedProse = ModelRequestSanitizer.text("{\"密码\":\"fixture-secret\"}\n请解释配置，不要省略这个要求");
        assertFalse(protectedProse.contains("fixture-secret"));
        assertTrue(protectedProse.endsWith("请解释配置，不要省略这个要求"));
        assertThrows(IllegalArgumentException.class, () -> ModelRequestSanitizer.messages(List.of(
                Map.of("role", "assistant", "tool_calls", List.of(Map.of("function", Map.of("arguments", "{} trailing data")))))));
    }
}
