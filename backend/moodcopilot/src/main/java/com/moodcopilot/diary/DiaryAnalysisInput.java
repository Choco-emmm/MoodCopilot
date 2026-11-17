package com.moodcopilot.diary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.DiaryEntity;
import org.springframework.util.DigestUtils;

/** User-authored input version, excluding asynchronously derived music metadata. */
public final class DiaryAnalysisInput {
    private static final ObjectMapper JSON = new ObjectMapper();

    private DiaryAnalysisInput() {}

    public static String fingerprint(DiaryEntity diary) {
        var music = diary.getMusicMeta();
        try {
            return DigestUtils.md5DigestAsHex(JSON.writeValueAsBytes(java.util.Arrays.asList(
                    diary.getContent() == null ? "" : diary.getContent(), diary.getImages(), diary.getImageMeta(),
                    music == null ? null : java.util.Arrays.asList(music.getSongUrl(), music.getTitle(),
                            music.getArtist(), music.getUserLyric()))));
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Invalid analysis input", error);
        }
    }
}
