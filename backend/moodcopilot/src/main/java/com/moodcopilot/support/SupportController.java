package com.moodcopilot.support;

import com.moodcopilot.common.ApiResponse;
import com.moodcopilot.entity.UserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class SupportController {

    private static final Path UPLOAD_DIR = Path.of("uploads", "support");
    private static final String[] TYPES = { "wechat" };

    @GetMapping("/api/support-images")
    public ApiResponse<Map<String, String>> listImages() {
        Map<String, String> images = new LinkedHashMap<>();
        for (String type : TYPES) {
            for (String ext : new String[] { "jpg", "jpeg", "png", "webp" }) {
                Path file = UPLOAD_DIR.resolve(type + "." + ext);
                if (Files.exists(file)) {
                    images.put(type, "/api/uploads/support/" + file.getFileName().toString());
                    break;
                }
            }
        }
        return ApiResponse.ok(images);
    }

    @PostMapping("/api/admin/support-images")
    public ApiResponse<Map<String, String>> upload(
            @AuthenticationPrincipal UserEntity user,
            @RequestParam("type") String type,
            @RequestParam("file") MultipartFile file) {
        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可上传");
        }

        boolean validType = false;
        for (String t : TYPES) {
            if (t.equals(type)) { validType = true; break; }
        }
        if (!validType) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效的图片类型，仅支持: wechat");
        }

        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg")
                && !contentType.equals("image/png") && !contentType.equals("image/webp"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 JPEG/PNG/WebP 格式");
        }
        if (file.getSize() > 2 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件过大，上限 2MB");
        }

        String ext = switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };

        try {
            Files.createDirectories(UPLOAD_DIR);
            // Remove old files of the same type with different extensions
            for (String oldExt : new String[] { "jpg", "jpeg", "png", "webp" }) {
                try { Files.deleteIfExists(UPLOAD_DIR.resolve(type + "." + oldExt)); } catch (IOException ignored) {}
            }
            Path target = UPLOAD_DIR.resolve(type + "." + ext);
            Files.write(target, file.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            Map<String, String> result = new LinkedHashMap<>();
            result.put("type", type);
            result.put("url", "/api/uploads/support/" + target.getFileName().toString());
            return ApiResponse.ok(result);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "图片保存失败");
        }
    }
}
