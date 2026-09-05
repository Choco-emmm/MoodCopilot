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
        music.setTitle("歌曲名不会进入查询");
        music.setMoodTags("AI 摘要不应成为查询依据");
        music.setThemeSummary("AI 反馈不应成为查询依据");
        music.setUserLyric("我想慢一点");

        String query = RagQueryBuilder.diaryQueryText("今天有点累", music);

        assertTrue(query.contains("今天有点累"));
        assertTrue(query.contains("我想慢一点"));
        assertTrue(query.contains("用户主动选择的歌词"));
        assertFalse(query.contains("歌曲名不会进入查询"));
        assertFalse(query.contains("AI 摘要不应成为查询依据"));
        assertFalse(query.contains("AI 反馈不应成为查询依据"));
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
    void embeddingTextKeepsTheCompleteNormalizedQuery() {
        String query = "今天先处理项目，晚上再学习 Redis 和数据库，记录一下这段较长的正文";

        String normalized = RagQueryBuilder.embeddingText(query);
        assertTrue(normalized.contains("今天先处理项目"));
        assertTrue(normalized.contains("较长的正文"));
        assertTrue(normalized.length() >= query.length() - 2);
        assertTrue(RagQueryBuilder.embeddingText("a".repeat(10000)).length() <= 4200);
    }

    @Test
    void queryRemovesSensitiveValuesBeforeEmbeddingOrLexicalFallback() {
        String query = RagQueryBuilder.keyword("api_key=sk-test_1234567890，今天工作很累");

        assertFalse(query.contains("sk-test_1234567890"));
        assertTrue(query.contains("已隐藏敏感信息"));
        assertTrue(RagQueryBuilder.lexicalTerms(query).stream()
                .noneMatch(term -> term.contains("sk-test_1234567890")));
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
