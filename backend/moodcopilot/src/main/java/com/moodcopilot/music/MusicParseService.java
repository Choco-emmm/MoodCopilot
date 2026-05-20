package com.moodcopilot.music;

import com.moodcopilot.entity.MusicMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MusicParseService {

    private static final Logger log = LoggerFactory.getLogger(MusicParseService.class);
    private static final Pattern TITLE_PATTERN = Pattern.compile("<title>([^<]+)</title>");
    private static final Pattern OG_IMAGE_PATTERN = Pattern.compile(
            "<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)[\"']");
    private static final Pattern OG_IMAGE_PATTERN_REV = Pattern.compile(
            "<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:image[\"']");

    private final HttpClient httpClient;

    public MusicParseService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public MusicMeta parse(String url) {
        try {
            String html = fetch(url);
            if (html == null) return null;

            String title = extractTitle(html);
            String coverUrl = extractCoverUrl(html);

            if (title == null) return null;

            String[] parts = parseTitle(title);
            return new MusicMeta(parts[0], parts[1], coverUrl, null);
        } catch (Exception e) {
            log.warn("解析音乐链接失败 url={}: {}", url, e.getMessage());
            return null;
        }
    }

    public String proxyImage(String imageUrl) {
        // Simply return the URL — the frontend handles anti-hotlinking with referrerpolicy
        // This endpoint exists as a fallback for CDNs that need server-side proxying
        return imageUrl;
    }

    private String fetch(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return response.body();
            }
            log.warn("抓取音乐页面失败 status={} url={}", response.statusCode(), url);
            return null;
        } catch (Exception e) {
            log.warn("抓取音乐页面异常 url={}: {}", url, e.getMessage());
            return null;
        }
    }

    private String extractTitle(String html) {
        Matcher m = TITLE_PATTERN.matcher(html);
        return m.find() ? m.group(1).trim() : null;
    }

    private String extractCoverUrl(String html) {
        Matcher m = OG_IMAGE_PATTERN.matcher(html);
        if (m.find()) return m.group(1).trim();
        m = OG_IMAGE_PATTERN_REV.matcher(html);
        if (m.find()) return m.group(1).trim();
        return null;
    }

    /**
     * Parse "歌曲名 - 歌手 - 网易云音乐" into [songName, artist].
     * Handles edge cases like extra spaces, special chars, and " - Single" suffixes.
     */
    private String[] parseTitle(String rawTitle) {
        String title = rawTitle.replace(" - 网易云音乐", "")
                                .replace(" - NetEase Cloud Music", "")
                                .trim();

        String[] parts = title.split(" - ");
        if (parts.length >= 2) {
            String songName = parts[0].trim();
            StringBuilder artist = new StringBuilder(parts[1].trim());
            for (int i = 2; i < parts.length; i++) {
                if (!parts[i].contains("专辑") && !parts[i].contains("单曲")) {
                    artist.append(" - ").append(parts[i].trim());
                }
            }
            return new String[]{songName, artist.toString()};
        }

        return new String[]{title, "未知歌手"};
    }
}
