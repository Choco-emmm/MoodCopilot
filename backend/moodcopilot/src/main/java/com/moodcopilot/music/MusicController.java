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
        if (url == null || url.isBlank()) {
            return ApiResponse.error(400, "缺少音乐链接");
        }
        if (!url.contains("music.163.com")) {
            return ApiResponse.error(400, "仅支持网易云音乐链接");
        }
        log.info("解析音乐链接: {}", url);
        MusicMeta meta = musicParseService.parse(url);
        if (meta == null) {
            return ApiResponse.error(400, "解析失败，请确认链接有效");
        }
        return ApiResponse.ok(meta);
    }

    @GetMapping("/proxy-image")
    public ApiResponse<String> proxyImage(@RequestParam String url) {
        return ApiResponse.ok(musicParseService.proxyImage(url));
    }
}
