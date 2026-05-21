package com.moodcopilot.oss;

import com.moodcopilot.common.ApiResponse;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.security.RateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/images")
public class OssController {

    private static final Logger log = LoggerFactory.getLogger(OssController.class);
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/heic", "image/heif");
    private static final long MAX_SIZE = 10 * 1024 * 1024; // 10MB

    private final OssService ossService;
    private final RateLimitService rateLimitService;

    public OssController(OssService ossService, RateLimitService rateLimitService) {
        this.ossService = ossService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/upload")
    public ApiResponse<Map<String, String>> upload(
            @AuthenticationPrincipal UserEntity user,
            @RequestParam("file") MultipartFile file) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片不能为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片不能超过 10MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 JPEG / PNG / WebP / GIF / HEIC 格式");
        }

        rateLimitService.tryAcquire(user, RateLimitService.AiApiType.IMAGE_UPLOAD);

        if (!ossService.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "图片服务暂未配置");
        }

        String url = ossService.uploadImage(file);
        log.info("用户上传图片 userId={} size={} url={}", user.getId(), file.getSize(), url);
        return ApiResponse.ok(Map.of("url", url));
    }

    /**
     * 浏览器直传 OSS 的签名策略（文件不经服务器）。
     * 前端传 ext（如 .jpg）生成对应的 object key。
     */
    @PostMapping("/upload-policy")
    public ApiResponse<Map<String, Object>> uploadPolicy(
            @AuthenticationPrincipal UserEntity user,
            @RequestParam(defaultValue = ".jpg") String ext) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        if (!ossService.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "图片服务暂未配置");
        }
        rateLimitService.tryAcquire(user, RateLimitService.AiApiType.IMAGE_UPLOAD);

        if (!ext.startsWith(".")) ext = "." + ext;
        String objectKey = "images/" + java.util.UUID.randomUUID() + ext;
        Map<String, Object> policy = ossService.postSign(objectKey, MAX_SIZE);
        log.info("用户获取上传策略 userId={} key={}", user.getId(), policy.get("key"));
        return ApiResponse.ok(policy);
    }
}
