package com.moodcopilot.diary;

import com.moodcopilot.entity.DiaryEntity;
import com.moodcopilot.entity.MusicMeta;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiarySearchSnippetBuilderTest {

    private static DiaryEntity diary(String content) {
        DiaryEntity diary = new DiaryEntity();
        diary.setId(1L);
        diary.setContent(content);
        return diary;
    }

    @Test
    void prefersTheAnalysisSummaryOverTheRawOpening() {
        // 节选往往是「今日食记 先确定一下规则…」这种开头，当索引很误导；
        // 有 AI 摘要就该用它。
        String content = "<p>今日食记</p><p>先确定一下规则，0无法下咽……</p>";

        String gist = DiarySearchSnippetBuilder.build(diary(content), "记录了六款面包的评分与感受", null);

        assertEquals("记录了六款面包的评分与感受", gist);
    }

    @Test
    void fallsBackToAnExcerptWhenThereIsNoSummary() {
        String gist = DiarySearchSnippetBuilder.build(diary("<p>今天去吃了拉面</p>"), null, null);

        assertEquals("今天去吃了拉面", gist);
    }

    @Test
    void excerptKeepsParagraphBreaksInsteadOfMashingItemsTogether() {
        // 这正是线上那次截断的现场：旧实现把空白压成一个空格，
        // 于是「3/5」和下一句黏成了「3/5这次烤着吃了」。
        String content = "<p>6️⃣初见烘焙 三重蓝莓乳酪贝果 3/5</p><p>这次烤着吃了，外表皮很脆</p>";

        String gist = DiarySearchSnippetBuilder.build(diary(content), null, null);

        assertTrue(gist.contains("3/5") && gist.contains("这次烤着吃了"),
                "两段内容都该出现：" + gist);
        assertFalse(gist.contains("3/5这次烤着吃了"), "段落不该被黏在一起：" + gist);
    }

    @Test
    void longContentIsTrimmedToTheExcerptBudget() {
        String gist = DiarySearchSnippetBuilder.build(diary("<p>" + "面".repeat(400) + "</p>"), null, null);

        assertTrue(gist.endsWith("…"), "超长内容该有省略号");
        assertTrue(gist.length() <= 130, "节选该被压到 120 字左右，实际 " + gist.length());
    }

    @Test
    void carriesTheMusicAndImageFlags() {
        DiaryEntity diary = diary("<p>好吃</p>");
        MusicMeta music = new MusicMeta();
        music.setTitle("痕迹器官");
        music.setArtist("KIMMUSEUM");
        diary.setMusicMeta(music);
        diary.setImages(List.of("https://bucket/a.jpg"));

        String gist = DiarySearchSnippetBuilder.build(diary, null, null);

        assertTrue(gist.startsWith("[分享音乐：痕迹器官 - KIMMUSEUM]"));
        assertTrue(gist.contains("[分享图片]"));
        assertTrue(gist.endsWith("好吃"));
    }

    @Test
    void aMatchedImageDescriptionBeatsTheGenericFlag() {
        DiaryEntity diary = diary("<p>拍了张照</p>");
        diary.setImages(List.of("https://bucket/a.jpg"));

        String gist = DiarySearchSnippetBuilder.build(diary, null, "【图片描述】紫色渐变网格背景的宣传海报");

        assertTrue(gist.contains("[图片描述：紫色渐变网格背景的宣传海报]"), gist);
        assertFalse(gist.contains("[分享图片]"), "有具体描述就不该再退成通用标记");
    }

    @Test
    void blankInputsProduceAnEmptyEntry() {
        assertEquals("", DiarySearchSnippetBuilder.build(null, null, null));
        assertEquals("", DiarySearchSnippetBuilder.build(diary(""), null, null));
    }
}
