package com.moodcopilot.ai;

import com.moodcopilot.entity.DiaryImageMeta;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.oss.OssService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatImageCaptionServiceTest {

    private static final String BUCKET = "https://bucket.example.com/images/temp/";
    private static final String GOOD_A = BUCKET + "a.jpg";
    private static final String GOOD_B = BUCKET + "b.png";

    private final VisionService visionService = mock(VisionService.class);
    private final OssService ossService = mock(OssService.class);
    private final ChatImageCaptionService service = new ChatImageCaptionService(visionService, ossService);

    private static UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(7L);
        return user;
    }

    private void acceptBucketUrlsOnly() {
        when(ossService.extractObjectKey(anyString()))
                .thenAnswer(invocation -> {
                    String url = invocation.getArgument(0);
                    return url != null && url.startsWith(BUCKET) ? url.substring(BUCKET.length()) : null;
                });
        when(visionService.isConfigured()).thenReturn(true);
    }

    @Test
    void buildsOneMetaPerUrlAndLeavesOcrToTheTool() {
        acceptBucketUrlsOnly();
        when(visionService.describeImages(anyList(), anyList(), any())).thenReturn("[视觉] 一张截图");

        String caption = service.describeForChat(user(), List.of(GOOD_A, GOOD_B));

        assertEquals("[视觉] 一张截图", caption);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> urlCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DiaryImageMeta>> metaCaptor = ArgumentCaptor.forClass(List.class);
        verify(visionService).describeImages(urlCaptor.capture(), metaCaptor.capture(), eq(null));

        List<String> urls = urlCaptor.getValue();
        List<DiaryImageMeta> metas = metaCaptor.getValue();
        assertEquals(List.of(GOOD_A, GOOD_B), urls);
        // 必须一一对应：buildImageTasks 按位置取 meta，错位会让 channel 静默降级
        assertEquals(urls.size(), metas.size());
        for (int i = 0; i < metas.size(); i++) {
            assertEquals(urls.get(i), metas.get(i).getUrl());
            // channel=normal 让默认路径只跑视觉（快）；OCR 由 readImageTextFunction 按需触发
            assertEquals("normal", metas.get(i).getChannel());
        }
    }

    @Test
    void rejectsUrlsOutsideTheBucketBeforeAnyVisionCall() {
        acceptBucketUrlsOnly();
        when(visionService.describeImages(anyList(), anyList(), any())).thenReturn("描述");

        // 客户端可控的 URL 不能进视觉服务 —— 那里会对传入地址发真实请求
        service.describeForChat(user(),
                List.of("http://127.0.0.1:8080/internal", "https://evil.example.com/x.jpg", GOOD_A));
        // 只有本桶那个应当被透传
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> urlCaptor = ArgumentCaptor.forClass(List.class);
        verify(visionService).describeImages(urlCaptor.capture(), anyList(), any());
        assertEquals(List.of(GOOD_A), urlCaptor.getValue());
    }

    @Test
    void dropsEverythingWhenNothingIsInTheBucket() {
        acceptBucketUrlsOnly();

        assertEquals("", service.describeForChat(user(), List.of("https://evil.example.com/x.jpg")));
        verify(visionService, never()).describeImages(anyList(), anyList(), any());
    }

    @Test
    void deduplicatesWhilePreservingOrderAndCapsAtThree() {
        acceptBucketUrlsOnly();
        when(visionService.describeImages(anyList(), anyList(), any())).thenReturn("描述");

        String fourth = BUCKET + "d.jpg";
        List<String> input = new ArrayList<>(List.of(GOOD_A, GOOD_B, GOOD_A, BUCKET + "c.jpg", fourth));
        service.describeForChat(user(), input);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> urlCaptor = ArgumentCaptor.forClass(List.class);
        verify(visionService).describeImages(urlCaptor.capture(), anyList(), any());
        assertEquals(List.of(GOOD_A, GOOD_B, BUCKET + "c.jpg"), urlCaptor.getValue());
    }

    @Test
    void returnsEmptyWhenVisionIsNotConfigured() {
        acceptBucketUrlsOnly();
        when(visionService.isConfigured()).thenReturn(false);

        assertEquals("", service.describeForChat(user(), List.of(GOOD_A)));
        verify(visionService, never()).describeImages(anyList(), anyList(), any());
    }

    @Test
    void aVisionFailureDegradesToNoCaptionInsteadOfBreakingTheTurn() {
        acceptBucketUrlsOnly();
        when(visionService.describeImages(anyList(), anyList(), any()))
                .thenThrow(new IllegalStateException("VLM 挂了"));

        assertEquals("", service.describeForChat(user(), List.of(GOOD_A)));
    }

    @Test
    void blankAndNullEntriesAreIgnored() {
        acceptBucketUrlsOnly();

        List<String> input = new ArrayList<>();
        input.add(null);
        input.add("   ");
        input.add(GOOD_A);

        service.describeForChat(user(), input);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> urlCaptor = ArgumentCaptor.forClass(List.class);
        verify(visionService).describeImages(urlCaptor.capture(), anyList(), any());
        assertEquals(List.of(GOOD_A), urlCaptor.getValue());
    }

    @Test
    void emptyInputNeverTouchesTheVisionService() {
        assertEquals("", service.describeForChat(user(), List.of()));
        assertEquals("", service.describeForChat(user(), null));
        verify(visionService, never()).describeImages(anyList(), anyList(), any());
        assertTrue(true);
    }
}
