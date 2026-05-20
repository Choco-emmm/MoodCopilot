package com.moodcopilot.music;

import com.moodcopilot.common.ApiResponse;
import com.moodcopilot.entity.MusicMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/music")
public class MusicController {

    private static final Logger log = LoggerFactory.getLogger(MusicController.class);
    private final MusicParseService musicParseService;

    public MusicController(MusicParseService musicParseService) {
        this.musicParseService = musicParseService;
    }

    @PostMapping("/parse")
    public ApiResponse<MusicMeta> parse(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        String text = body.getOrDefault("text", "");

        if (url == null || url.isBlank()) {
            return ApiResponse.error(400, "缺少音乐链接");
        }
        if (!url.contains("music.163.com") && !url.contains("163cn.tv")) {
            return ApiResponse.error(400, "仅支持网易云音乐链接（music.163.com / 163cn.tv）");
        }
        log.info("解析音乐链接 url={} hasText={}", url, text != null && !text.isBlank());
        MusicMeta meta = musicParseService.parse(url, text);
        if (meta == null) {
            return ApiResponse.error(400, "解析失败，请确认链接有效");
        }
        return ApiResponse.ok(meta);
    }

    @PostMapping("/lyrics")
    public ApiResponse<java.util.List<String>> suggestLyrics(@RequestBody Map<String, String> body) {
        String title = body.get("title");
        String artist = body.get("artist");
        String url = body.get("url");
        if (url == null || url.isBlank()) {
            return ApiResponse.error(400, "缺少歌曲链接");
        }
        java.util.List<String> lyrics = musicParseService.suggestLyrics(title, artist, url);
        return ApiResponse.ok(lyrics);
    }

    @GetMapping("/proxy-image")
    public ApiResponse<String> proxyImage(@RequestParam String url) {
        return ApiResponse.ok(musicParseService.proxyImage(url));
    }
}
