package com.moodcopilot.music;

import com.moodcopilot.entity.MusicMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MusicParseService {

    private static final Logger log = LoggerFactory.getLogger(MusicParseService.class);

    // 分享{artist}的单曲《{title}》: url (来自@网易云音乐)
    private static final Pattern SHARE_TEXT_PATTERN = Pattern.compile(
            "分享(.+?)的单曲[《「](.+?)[》」]");
    // 也支持专辑格式：分享{artist}的专辑《{title}》
    private static final Pattern SHARE_ALBUM_PATTERN = Pattern.compile(
            "分享(.+?)的专辑[《「](.+?)[》」]");

    // Page scraping patterns (fallback)
    private static final Pattern TITLE_PATTERN = Pattern.compile("<title>([^<]+)</title>");
    private static final Pattern OG_IMAGE_PATTERN = Pattern.compile(
            "<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OG_IMAGE_PATTERN_REV = Pattern.compile(
            "<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:image[\"']",
            Pattern.CASE_INSENSITIVE);

    private final HttpClient httpClient;

    public MusicParseService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    /**
     * Parse music info from share text or URL.
     * Priority: parse share text format → fall back to page scraping.
     */
    public MusicMeta parse(String url, String shareText) {
        // Step 1: Try to parse artist/title from the share text
        if (shareText != null && !shareText.isBlank()) {
            MusicMeta fromText = parseFromShareText(shareText);
            if (fromText != null) {
                // Try to fetch cover from page, but don't fail if we can't
                String coverUrl = fetchCover(url);
                return new MusicMeta(fromText.getTitle(), fromText.getArtist(), coverUrl, null);
            }
        }

        // Step 2: Fall back to page scraping
        return parseFromPage(url);
    }

    private MusicMeta parseFromShareText(String text) {
        Matcher m = SHARE_TEXT_PATTERN.matcher(text);
        if (!m.find()) {
            m = SHARE_ALBUM_PATTERN.matcher(text);
        }
        if (m.find()) {
            String artist = m.group(1).trim();
            String title = m.group(2).trim();
            log.info("从分享文本解析到: artist={}, title={}", artist, title);
            return new MusicMeta(title, artist, null, null);
        }
        return null;
    }

    private MusicMeta parseFromPage(String url) {
        try {
            String html = fetch(url);
            if (html == null) return null;

            String title = extractTitle(html);
            String coverUrl = extractCoverUrl(html);

            if (title == null) return null;

            String[] parts = parseTitle(title);
            return new MusicMeta(parts[0], parts[1], coverUrl, null);
        } catch (Exception e) {
            log.warn("页面解析失败 url={}: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * Try to fetch just the cover image URL. Returns null on any failure.
     */
    private String fetchCover(String url) {
        try {
            String html = fetch(url);
            if (html != null) {
                return extractCoverUrl(html);
            }
        } catch (Exception e) {
            log.debug("封面抓取失败 url={}: {}", url, e.getMessage());
        }
        return null;
    }

    public String proxyImage(String imageUrl) {
        return imageUrl;
    }

    private String fetch(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return response.body();
            }
            log.warn("抓取页面失败 status={} url={}", response.statusCode(), url);
            return null;
        } catch (Exception e) {
            log.warn("抓取页面异常 url={}: {}", url, e.getMessage());
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
