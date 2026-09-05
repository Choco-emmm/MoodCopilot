package com.moodcopilot.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SensitiveDataDetectorTest {
    @Test
    void detectsCredentialsContactsAndPrivateUrls() {
        assertTrue(SensitiveDataDetector.containsSensitiveData("api_key=sk-test_1234567890"));
        assertTrue(SensitiveDataDetector.containsSensitiveData("phone 13800138000"));
        assertTrue(SensitiveDataDetector.containsSensitiveData("http://192.168.1.20/internal"));
        assertTrue(SensitiveDataDetector.containsSensitiveData("user@example.com"));
    }

    @Test
    void redactionDoesNotExposeTheOriginalSecret() {
        String input = "password=super-secret and token=abc123";
        String redacted = SensitiveDataDetector.redact(input);

        assertFalse(redacted.contains("super-secret"));
        assertFalse(redacted.contains("abc123"));
        assertTrue(redacted.contains("已隐藏敏感信息"));
    }

    @Test
    void memoryAttributesMustNotContainSensitiveValues() {
        assertFalse(SensitiveDataDetector.allowedForMemory("联系方式", "13800138000", "我手机号是13800138000"));
        assertFalse(SensitiveDataDetector.allowedForMemory("开发偏好", "喜欢 Java", "api_key=sk-test_1234567890"));
        assertTrue(SensitiveDataDetector.allowedForMemory("沟通偏好", "喜欢简洁回答", "我喜欢简洁回答"));
    }

    @Test
    void detectsCredentialsEmbeddedInUrls() {
        assertTrue(SensitiveDataDetector.containsSensitiveData("https://user:secret@example.com/private"));
        assertTrue(SensitiveDataDetector.containsSensitiveData("ghp_1234567890abcdef"));
        assertTrue(SensitiveDataDetector.containsSensitiveData("这份是公司机密资料"));
    }
}
