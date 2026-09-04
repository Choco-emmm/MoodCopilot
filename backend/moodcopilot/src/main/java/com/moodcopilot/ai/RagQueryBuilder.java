package com.moodcopilot.ai;

import com.moodcopilot.entity.MusicMeta;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/** Builds deterministic, user-grounded embedding input. It never calls an LLM. */
public final class RagQueryBuilder {
    private static final int BODY_LIMIT = 3600;
    private static final int LYRIC_LIMIT = 900;
    private static final int TOTAL_LIMIT = 4200;

    private RagQueryBuilder() {
    }

    public static String diaryQueryText(String diaryContent, MusicMeta musicMeta) {
        String body = normalize(diaryContent);
        String lyric = normalize(musicMeta == null ? null : musicMeta.getUserLyric());
        body = truncate(body, BODY_LIMIT);
        lyric = truncate(lyric, LYRIC_LIMIT);

        StringBuilder query = new StringBuilder();
        if (meaningful(body)) query.append("[用户日记正文] ").append(body);
        if (meaningful(lyric)) {
            if (!query.isEmpty()) query.append('\n');
            query.append("[用户主动选择的歌词] ").append(lyric);
        }
        return truncate(query.toString(), TOTAL_LIMIT);
    }

    public static String keyword(String value) {
        return truncate(normalize(value), TOTAL_LIMIT);
    }

    /** Removes presentation labels before a database LIKE fallback. */
    public static String lexicalText(String value) {
        return normalize(value)
                .replace("[用户日记正文]", "")
                .replace("[用户主动选择的歌词]", "")
                .trim();
    }

    /** Returns a small bounded set of useful LIKE fragments, without building SQL text. */
    public static List<String> lexicalTerms(String value) {
        String normalized = lexicalText(value);
        if (!meaningful(normalized)) return List.of();

        List<String> terms = new ArrayList<>();
        for (String token : normalized.split("\\s+")) {
            if (!meaningful(token)) continue;
            if (token.length() <= 16) {
                terms.add(token);
            } else {
                for (int start = 0; start < token.length() && terms.size() < 8; start += 8) {
                    terms.add(token.substring(start, Math.min(token.length(), start + 8)));
                }
            }
            if (terms.size() >= 8) break;
        }
        if (terms.isEmpty()) terms.add(truncate(normalized, 16));
        return terms.stream().distinct().limit(8).toList();
    }

    public static boolean meaningful(String value) {
        return value != null && value.codePoints().anyMatch(Character::isLetterOrDigit);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("[\\p{Cntrl}&&[^\\n]]", "")
                .replaceAll("[ \\t\\r\\n]+", " ").trim();
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value == null ? "" : value;
        return value.substring(0, max);
    }
}
