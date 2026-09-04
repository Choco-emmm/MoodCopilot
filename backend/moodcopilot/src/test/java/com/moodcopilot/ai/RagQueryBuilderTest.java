package com.moodcopilot.ai;

import com.moodcopilot.entity.MusicMeta;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagQueryBuilderTest {

    @Test
    void diaryQueryUsesUserWrittenContentAndSelectedLyricsOnly() {
        MusicMeta music = new MusicMeta();
        music.setUserLyric("我想慢一点");

        String query = RagQueryBuilder.diaryQueryText("今天有点累", music);

        assertTrue(query.contains("今天有点累"));
        assertTrue(query.contains("我想慢一点"));
        assertTrue(query.contains("用户主动选择的歌词"));
        assertFalse(query.contains("摘要"));
    }

    @Test
    void blankOrPunctuationOnlyQueriesAreNotMeaningful() {
        assertFalse(RagQueryBuilder.meaningful(RagQueryBuilder.keyword("  !!!  ")));
        assertTrue(RagQueryBuilder.meaningful(RagQueryBuilder.keyword("今天")));
    }

    @Test
    void queryIsBoundedAfterNormalization() {
        String query = RagQueryBuilder.keyword("a".repeat(10000));
        assertTrue(query.length() <= 4200);
    }

    @Test
    void lexicalFallbackRemovesPromptLabelsAndBoundsFragments() {
        String query = "[用户日记正文] 今天很累，想早点休息 [用户主动选择的歌词] 慢一点";

        assertFalse(RagQueryBuilder.lexicalText(query).contains("用户日记正文"));
        assertFalse(RagQueryBuilder.lexicalText(query).contains("用户主动选择的歌词"));
        assertTrue(RagQueryBuilder.lexicalTerms(query).stream().allMatch(term -> term.length() <= 16));
        assertEquals(2, RagQueryBuilder.lexicalTerms("今天 很累").size());
    }
}
