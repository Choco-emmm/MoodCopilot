package com.moodcopilot.oss;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
public class OssService {

    private static final Logger log = LoggerFactory.getLogger(OssService.class);

    private final String endpoint;
    private final String bucket;
    private final String accessKey;
    private final String secretKey;
    private final HttpClient httpClient;

    public OssService(
            @Value("${oss.endpoint}") String endpoint,
            @Value("${oss.bucket}") String bucket,
            @Value("${oss.access-key}") String accessKey,
            @Value("${oss.secret-key}") String secretKey) {
        this.endpoint = endpoint;
        this.bucket = bucket;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isConfigured() {
        return accessKey != null && !accessKey.isBlank() && !accessKey.startsWith("your-");
    }

    /**
     * 上传图片到 OSS，返回公开访问 URL。
     */
    public String uploadImage(MultipartFile file) {
        if (!isConfigured()) {
            throw new IllegalStateException("OSS 未配置，请设置 OSS_ACCESS_KEY 和 OSS_SECRET_KEY");
        }

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf('.'));
        }
        String objectKey = "images/" + UUID.randomUUID() + ext;
        String contentType = file.getContentType();
        if (contentType == null) contentType = "image/jpeg";

        try {
            byte[] data = file.getBytes();
            String date = DateTimeFormatter.RFC_1123_DATE_TIME
                    .withZone(ZoneId.of("GMT"))
                    .withLocale(Locale.US)
                    .format(Instant.now());

            String canonicalString = "PUT\n\n" + contentType + "\n" + date + "\n/" + bucket + "/" + objectKey;
            String signature = sign(canonicalString);
            String auth = "OSS " + accessKey + ":" + signature;

            String host = bucket + "." + endpoint;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://" + host + "/" + objectKey))
                    .header("Authorization", auth)
                    .header("Content-Type", contentType)
                    .header("Date", date)
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(data))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 200) {
                String url = "https://" + host + "/" + objectKey;
                log.info("OSS 上传成功: {} ({} bytes)", url, data.length);
                return url;
            } else {
                log.error("OSS 上传失败 status={} body={} objectKey={}", status, response.body(), objectKey);
                throw new RuntimeException("OSS 上传失败，状态码: " + status);
            }
        } catch (Exception e) {
            log.error("OSS 上传异常 objectKey={}: {}", objectKey, e.getMessage());
            throw new RuntimeException("图片上传失败: " + e.getMessage(), e);
        }
    }

    private String sign(String canonicalString) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(), "HmacSHA1");
        mac.init(keySpec);
        byte[] signed = mac.doFinal(canonicalString.getBytes());
        return Base64.getEncoder().encodeToString(signed);
    }

    /**
     * 从 OSS URL 提取 object key。例如 https://bucket.endpoint/images/uuid.jpg → images/uuid.jpg
     */
    public String extractObjectKey(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return null;
        String host = bucket + "." + endpoint;
        int idx = imageUrl.indexOf(host);
        if (idx < 0) return null;
        String path = imageUrl.substring(idx + host.length());
        return path.startsWith("/") ? path.substring(1) : path;
    }

    /**
     * 删除 OSS 上的单个图片。失败仅记日志，不抛异常（孤儿文件不应阻塞主流程）。
     */
    public void deleteImage(String imageUrl) {
        String objectKey = extractObjectKey(imageUrl);
        if (objectKey == null) return;
        try {
            String date = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
                    .withZone(java.time.ZoneId.of("GMT"))
                    .withLocale(java.util.Locale.US)
                    .format(java.time.Instant.now());
            String canonicalString = "DELETE\n\n\n" + date + "\n/" + bucket + "/" + objectKey;
            String auth = "OSS " + accessKey + ":" + sign(canonicalString);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://" + bucket + "." + endpoint + "/" + objectKey))
                    .header("Authorization", auth)
                    .header("Date", date)
                    .DELETE()
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 204 || response.statusCode() == 200) {
                log.info("OSS 删除成功: {}", objectKey);
            } else {
                log.warn("OSS 删除失败 status={} key={}", response.statusCode(), objectKey);
            }
        } catch (Exception e) {
            log.warn("OSS 删除异常 url={}: {}", imageUrl, e.getMessage());
        }
    }

    /** 批量删除 OSS 图片 */
    public void deleteImages(java.util.List<String> imageUrls) {
        if (imageUrls == null) return;
        for (String url : imageUrls) {
            deleteImage(url);
        }
    }
}
