package com.moodcopilot.ai.tool;

import com.moodcopilot.ai.VisionService;
import com.moodcopilot.ai.tool.impl.ReadImageTextTool;
import com.moodcopilot.ai.tool.impl.ReadImageTextTool.ReadImageTextRequest;
import com.moodcopilot.ai.tool.impl.ReadImageTextTool.ReadImageTextResult;
import com.moodcopilot.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadImageTextToolTest {

    private final VisionService visionService = mock(VisionService.class);
    private final ReadImageTextTool tool = new ReadImageTextTool(visionService);

    @BeforeEach
    void visionConfigured() {
        when(visionService.isConfigured()).thenReturn(true);
    }

    private static ToolExecutionContext contextWith(List<String> imageUrls) {
        UserEntity user = new UserEntity();
        user.setId(7L);
        Authentication auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
        return new ToolExecutionContext(auth, null, imageUrls);
    }

    @Test
    void readsTextFromTheTurnsAttachedImages() {
        when(visionService.extractText(any(), any())).thenReturn("总计 ¥128.00");

        ReadImageTextResult result = (ReadImageTextResult) tool.execute(
                new ReadImageTextRequest(""), contextWith(List.of("https://bucket/x.png")));

        assertTrue(result.success());
        assertEquals("总计 ¥128.00", result.text());
        assertNull(result.note());
        // 模型不接触 URL：地址来自工具上下文
        verify(visionService).extractText(eq(List.of("https://bucket/x.png")), any());
    }

    @Test
    void passesTheFocusHintThrough() {
        when(visionService.extractText(any(), any())).thenReturn("2026-09-07");

        tool.execute(new ReadImageTextRequest("日期"), contextWith(List.of("https://bucket/x.png")));

        verify(visionService).extractText(any(), eq("日期"));
    }

    @Test
    void noAttachmentsMeansNoVisionCall() {
        ReadImageTextResult result = (ReadImageTextResult) tool.execute(
                new ReadImageTextRequest(""), contextWith(List.of()));

        assertFalse(result.success());
        assertTrue(result.note().contains("没有附带图片"));
        verify(visionService, never()).extractText(any(), any());
    }

    @Test
    void blankTextIsReportedRatherThanReturnedAsSuccess() {
        // OCR 对「图里没有文字」返回空串，这时要说清楚，而不是给模型一段空文本
        when(visionService.extractText(any(), any())).thenReturn("");

        ReadImageTextResult result = (ReadImageTextResult) tool.execute(
                new ReadImageTextRequest(""), contextWith(List.of("https://bucket/x.png")));

        assertFalse(result.success());
        assertNull(result.text());
        assertTrue(result.note().contains("没有可读的文字"));
    }

    @Test
    void anUnconfiguredVisionServiceIsReportedAsAFailureNotAsNoText() {
        // 「图里没文字」和「视觉服务挂了」必须分开说 —— 混成一句，
        // 模型会转述出一句用户看不懂的话。
        when(visionService.isConfigured()).thenReturn(false);

        ReadImageTextResult result = (ReadImageTextResult) tool.execute(
                new ReadImageTextRequest(""), contextWith(List.of("https://bucket/x.png")));

        assertFalse(result.success());
        assertNull(result.text());
        assertTrue(result.note().contains("视觉服务暂时不可用"));
        verify(visionService, never()).extractText(any(), any());
    }

    @Test
    void wireNameAndDisplayNameFollowTheConvention() {
        assertEquals("readImageTextFunction", tool.name());
        assertEquals("readImageText", tool.displayName());
    }
}
