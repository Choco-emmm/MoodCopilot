package com.moodcopilot.ai.tool;

import com.moodcopilot.ai.tool.impl.ReadDiaryTool;
import com.moodcopilot.ai.tool.impl.ReadDiaryTool.ReadDiaryRequest;
import com.moodcopilot.ai.tool.impl.ReadDiaryTool.ReadDiaryResult;
import com.moodcopilot.entity.DiaryEntity;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.mapper.DiaryMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadDiaryToolTest {

    private final DiaryMapper diaryMapper = mock(DiaryMapper.class);
    private final ReadDiaryTool tool = new ReadDiaryTool(diaryMapper);

    private static ToolExecutionContext contextFor(long userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        Authentication auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
        return new ToolExecutionContext(auth, null, List.of());
    }

    private static DiaryEntity diary(String html) {
        DiaryEntity diary = new DiaryEntity();
        diary.setId(3623L);
        diary.setCreatedAt(LocalDateTime.of(2026, 9, 13, 20, 0, 40));
        diary.setContent(html);
        return diary;
    }

    @Test
    void returnsPlainTextWithParagraphBreaksIntact() {
        when(diaryMapper.selectOne(any())).thenReturn(diary(
                "<p>1️⃣橙不秃 抹茶碱水球 2.8/5</p><p>吃着像橡皮泥</p>"));

        ReadDiaryResult result = (ReadDiaryResult) tool.execute(new ReadDiaryRequest(3623L), contextFor(7L));

        assertTrue(result.success());
        assertEquals("1️⃣橙不秃 抹茶碱水球 2.8/5\n吃着像橡皮泥", result.content());
        assertNull(result.note());
    }

    @Test
    void listItemsKeepTheirLineBreaks() {
        when(diaryMapper.selectOne(any())).thenReturn(diary("<ul><li>三明治</li><li>贝果</li></ul>"));

        ReadDiaryResult result = (ReadDiaryResult) tool.execute(new ReadDiaryRequest(3623L), contextFor(7L));

        assertEquals("- 三明治\n- 贝果", result.content());
    }

    @Test
    void aDiaryThatIsNotOwnedReadsAsNotFound() {
        // 归属校验写在查询条件里，所以「不属于我」和「不存在」得到同一种结果
        when(diaryMapper.selectOne(any())).thenReturn(null);

        ReadDiaryResult result = (ReadDiaryResult) tool.execute(new ReadDiaryRequest(999L), contextFor(7L));

        assertFalse(result.success());
        assertNull(result.content());
        assertTrue(result.note().contains("没有找到"));
    }

    @Test
    void missingIdIsRejectedWithoutTouchingTheDatabase() {
        ReadDiaryResult result = (ReadDiaryResult) tool.execute(new ReadDiaryRequest(null), contextFor(7L));

        assertFalse(result.success());
        assertTrue(result.note().contains("diaryId"));
        verify(diaryMapper, never()).selectOne(any());
    }

    @Test
    void blankContentIsReportedRatherThanReturnedAsText() {
        when(diaryMapper.selectOne(any())).thenReturn(diary("<p>   </p>"));

        ReadDiaryResult result = (ReadDiaryResult) tool.execute(new ReadDiaryRequest(3623L), contextFor(7L));

        assertFalse(result.success());
        assertNull(result.content());
        assertTrue(result.note().contains("没有正文"));
    }

    @Test
    void overlongContentIsTruncatedAndSaysSo() {
        when(diaryMapper.selectOne(any())).thenReturn(diary("<p>" + "面".repeat(5000) + "</p>"));

        ReadDiaryResult result = (ReadDiaryResult) tool.execute(new ReadDiaryRequest(3623L), contextFor(7L));

        assertTrue(result.success());
        assertTrue(result.content().endsWith("…"));
        assertTrue(result.note().contains("已截断"));
    }

    @Test
    void attachedImagesAreSurfacedSoTheModelKnowsTheyExist() {
        DiaryEntity withImages = diary("<p>拍了张照</p>");
        withImages.setImages(List.of("https://bucket/a.jpg", "https://bucket/b.jpg"));
        when(diaryMapper.selectOne(any())).thenReturn(withImages);

        ReadDiaryResult result = (ReadDiaryResult) tool.execute(new ReadDiaryRequest(3623L), contextFor(7L));

        assertTrue(result.note().contains("2 张图片"));
    }

    @Test
    void wireNameAndDisplayNameFollowTheConvention() {
        assertEquals("readDiaryFunction", tool.name());
        assertEquals("readDiary", tool.displayName());
    }
}
