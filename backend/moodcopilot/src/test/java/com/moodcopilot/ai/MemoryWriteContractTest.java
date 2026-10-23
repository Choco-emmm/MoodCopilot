package com.moodcopilot.ai;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.*;
import com.moodcopilot.mapper.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MemoryWriteContractTest {
    @BeforeAll static void metadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "memory-write-contract");
        for (var type : List.of(UserProfileMemoryEntity.class, UserMemoryCandidateEntity.class, UserMemoryEvidenceEntity.class))
            TableInfoHelper.initTableInfo(assistant, type);
    }

    final UserProfileMemoryMapper memories = mock(UserProfileMemoryMapper.class);
    final UserMemoryCandidateMapper candidates = mock(UserMemoryCandidateMapper.class);
    final UserMemoryEvidenceMapper evidence = mock(UserMemoryEvidenceMapper.class);
    final UserMemoryRejectionMapper rejections = mock(UserMemoryRejectionMapper.class);
    final RagMemoryService rag = mock(RagMemoryService.class);
    final MemoryOrchestrator service = new MemoryOrchestrator(memories, candidates, evidence, rejections, rag, new ObjectMapper());

    UserProfileMemoryEntity old() {
        var old = new UserProfileMemoryEntity();
        old.setId(11L); old.setUserId(7L); old.setAttributeKey("饮食偏好"); old.setAttributeValue("喜欢苹果");
        old.setMemoryType("preference"); old.setStatus("active"); old.setIsCore(false); old.setConfidence(1D);
        when(memories.selectById(11L)).thenReturn(old);
        when(memories.selectOne(any())).thenReturn(old);
        when(memories.selectList(any())).thenReturn(List.of(old));
        return old;
    }

    @Test void manualEditRejectsRestrictedContentBeforeAnyWrite() {
        old();
        assertThrows(ResponseStatusException.class, () -> service.updateFormal(7L, 11L, "password=synthetic-only", false));
        verify(memories, never()).insert(any(UserProfileMemoryEntity.class));
        verify(memories, never()).updateById(any(UserProfileMemoryEntity.class));
        verifyNoInteractions(evidence, rag);
    }

    @Test void candidateEditsCannotBypassTheSamePolicy() {
        var candidate = new UserMemoryCandidateEntity();
        candidate.setId(22L); candidate.setUserId(7L); candidate.setStatus("PENDING");
        candidate.setAttributeKey("饮食偏好"); candidate.setAttributeValue("喜欢苹果"); candidate.setMemoryType("preference");
        when(candidates.selectById(22L)).thenReturn(candidate);
        assertThrows(ResponseStatusException.class, () -> service.approveCandidate(7L, 22L, "someone@example.com"));
        assertEquals("PENDING", candidate.getStatus());
        verify(memories, never()).insert(any(UserProfileMemoryEntity.class));
        verifyNoInteractions(evidence, rag);
    }

    @Test void explicitModelAssertionCannotOverwriteExistingMemory() {
        var old = old();
        service.processExtractedMemories(7L, List.of(attribute("饮食偏好", "喜欢香蕉", "explicit", "我喜欢香蕉")),
                "chat_candidate", null, 32L, "我喜欢香蕉", null);
        assertEquals("active", old.getStatus());
        assertEquals("喜欢苹果", old.getAttributeValue());
        verify(memories, never()).insert(any(UserProfileMemoryEntity.class));
        verify(candidates).insert(any(UserMemoryCandidateEntity.class));
    }

    @Test void automaticNegationCannotInvalidateFormalMemory() {
        var old = old();
        service.processExtractedMemories(7L, List.of(attribute("饮食偏好", "喜欢苹果", "negated", "我不再喜欢苹果")),
                "chat_candidate", null, 32L, "我不再喜欢苹果", null);
        assertEquals("active", old.getStatus());
        verify(memories, never()).updateById(any(UserProfileMemoryEntity.class));
        verify(memories, never()).insert(any(UserProfileMemoryEntity.class));
    }

    @Test void aNewEnglishKeyCannotEnterThroughTheExplicitPath() {
        service.processExtractedMemories(7L, List.of(attribute("favorite_food", "苹果", "explicit", "我喜欢苹果")),
                "explicit", null, 32L, "我喜欢苹果", null);
        verify(memories, never()).insert(any(UserProfileMemoryEntity.class));
    }

    @Test void distinctValuesSurviveExtractionSanitization() {
        var extractor = mock(MemoryExtractionService.class, CALLS_REAL_METHODS);
        var result = extractor.sanitizeAttributes(List.of(attribute("饮食偏好", "喜欢苹果", "inferred", "我喜欢苹果"),
                attribute("饮食偏好", "喜欢香蕉", "inferred", "我喜欢香蕉")));
        assertEquals(2, result.size());
    }

    @Test void evidenceMustRemainShortAndCannotBeARedactionPlaceholder() {
        var source = "今天的天气一般。".repeat(65) + "我喜欢跑步。";
        assertFalse(MemoryExtractionService.isUserEvidenceGrounded(source, source));
        assertFalse(MemoryExtractionService.isUserEvidenceGrounded("[已隐藏敏感信息]", "我的信息是[已隐藏敏感信息]"));
        assertTrue(MemoryExtractionService.isUserEvidenceGrounded("我喜欢跑步", source));
    }

    @Test void chineseCredentialsAreDetectedWithoutBlockingDiscussion() {
        assertTrue(SensitiveDataDetector.containsSensitiveData("我的密码是 synthetic-only"));
        assertFalse(SensitiveDataDetector.containsSensitiveData("我忘记密码了，想学习 Token 原理"));
    }

    @Test void quotedChineseCredentialsAreRejectedByEditsAndRemovedFromOutboundCopies() {
        old();
        for (String text : List.of("我的密码是 'fixture-quoted'", "密码：\"fixture-quoted\"",
                "配置信息：{\"密码\":\"fixture-quoted\"}，请检查",
                "```json\n{\"访问凭证\": \"fixture-quoted\"}\n```",
                "我的口令为“fixture-quoted with spaces”", "密钥 = `fixture-quoted`")) {
            assertTrue(SensitiveDataDetector.containsSensitiveData(text), "quoted credential not classified");
            assertFalse(ModelRequestSanitizer.text(text).contains("fixture-quoted"), "outbound copy leaked credential");
            assertThrows(ResponseStatusException.class, () -> service.updateFormal(7L, 11L, text, false));
        }
        for (String discussion : List.of("我忘记密码了", "学习 Token 原理", "请解释 JSON 中的\"密码\"字段", "密码不能为空"))
            assertFalse(SensitiveDataDetector.containsSensitiveData(discussion), "ordinary discussion was blocked");
        verify(memories, never()).insert(any(UserProfileMemoryEntity.class));
        verify(memories, never()).updateById(any(UserProfileMemoryEntity.class));
        verifyNoInteractions(evidence, rag);
    }

    @Test void allSixTypesShareStrictUnicodeLengthKeyAndPrivacyRules() {
        for (String type : MemorySafetyPolicy.MEMORY_TYPES) assertNull(MemoryWritePolicy.violation("生活偏好", "苹果", type, null));
        for (String type : List.of("goal", "trait", "relation", "other"))
            assertEquals("INVALID_MEMORY_TYPE", MemoryWritePolicy.violation("生活偏好", "苹果", type, null));
        assertNull(MemoryWritePolicy.violation("偏".repeat(64), "😀".repeat(500), "preference", null));
        assertEquals("CONTENT_TOO_LONG", MemoryWritePolicy.violation("偏".repeat(65), "苹果", "preference", null));
        assertEquals("CONTENT_TOO_LONG", MemoryWritePolicy.violation("生活偏好", "😀".repeat(501), "preference", null));
        for (String restricted : List.of("13800138000", "fixture@example.com", "我的密码是fixture-secret", "公司机密测试"))
            assertEquals("RESTRICTED_CONTENT", MemoryWritePolicy.violation("生活偏好", restricted, "preference", null));
        var legacy = old(); legacy.setAttributeKey("old_key"); legacy.setMemoryType("other");
        assertNull(MemoryWritePolicy.violation("old_key", "苹果", "other", legacy));
        assertEquals("INVALID_ATTRIBUTE_KEY", MemoryWritePolicy.violation("another_key", "苹果", "preference", legacy));
        for (String key : List.of("饮食偏好\u200b", "饮食_偏好", "饮食🙂", "饮食é"))
            assertEquals("INVALID_ATTRIBUTE_KEY", MemoryWritePolicy.violation(key, "苹果", "preference", null));
        assertTrue(SensitiveDataDetector.containsSensitiveData("138-0013-8000"));
        assertTrue(SensitiveDataDetector.containsSensitiveData("用户@例子.公司"));
    }

    @Test void restrictedMergeAndOversizedExtractionHaveNoWriteEffects() {
        assertThrows(ResponseStatusException.class, () -> service.mergeMemoriesByIds(7L, List.of(), "生活偏好", "fixture@example.com", "preference", false, "确认", 32L));
        var extractor = mock(MemoryExtractionService.class, CALLS_REAL_METHODS);
        assertTrue(extractor.sanitizeAttributes(List.of(attribute("生活偏好", "字".repeat(501), "explicit", "原文"))).isEmpty());
        verify(memories, never()).insert(any(UserProfileMemoryEntity.class));
        verify(memories, never()).updateById(any(UserProfileMemoryEntity.class));
        verifyNoInteractions(evidence, rag);
    }

    @Test void rejectingRestrictedSignalsLogsOnlyMetadata() {
        var logger = (ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(MemoryOrchestrator.class);
        var captured = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        captured.start(); logger.addAppender(captured);
        try {
            service.processExtractedMemories(7L, List.of(attribute("生活偏好", "password=fixture-secret", "explicit", "fixture-evidence")),
                    "chat_candidate", null, 32L, "fixture-evidence", null);
            assertTrue(captured.list.stream().anyMatch(event -> event.getFormattedMessage().contains("RESTRICTED_CONTENT")));
            assertTrue(captured.list.stream().noneMatch(event -> event.getFormattedMessage().contains("fixture-secret")
                    || event.getFormattedMessage().contains("fixture-evidence")));
        } finally { logger.detachAppender(captured); captured.stop(); }
    }

    static MemoryExtractionService.MemoryAttribute attribute(String key, String value, String assertion, String quote) {
        return new MemoryExtractionService.MemoryAttribute(key, value, false, "preference", assertion, .99, quote, null, null);
    }
}
