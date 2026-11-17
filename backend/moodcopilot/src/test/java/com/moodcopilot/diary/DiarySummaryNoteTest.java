package com.moodcopilot.diary;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 摘要检索的说明文案：模型靠它判断「是不是还有更早的记录没拿到」。
 */
class DiarySummaryNoteTest {

    @Test
    void emptyMatchSaysNothingFound() {
        assertEquals("未找到符合条件的历史日记", DiaryService.summaryNote(0, 0));
    }

    @Test
    void truncationTellsTheModelThereIsMore() {
        String note = DiaryService.summaryNote(20, 30);

        assertTrue(note.contains("匹配 30 篇"), note);
        assertTrue(note.contains("最近 20 篇"), note);
        // 提示的是缩小时间范围，不是翻页：时间区间不会因为期间新增日记而错位
        assertTrue(note.contains("缩小时间范围"), note);
    }

    @Test
    void fullReturnSaysEverythingIsIncluded() {
        String note = DiaryService.summaryNote(9, 9);

        assertEquals("已返回全部 9 篇日记摘要，按时间倒序", note);
    }

    @Test
    void singlePageRetrievalIsAlsoConsideredFull() {
        assertTrue(DiaryService.summaryNote(1, 1).startsWith("已返回全部"));
    }
}
