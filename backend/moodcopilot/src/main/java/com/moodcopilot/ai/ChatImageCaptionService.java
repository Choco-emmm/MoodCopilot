package com.moodcopilot.ai;

import com.moodcopilot.entity.DiaryImageMeta;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.oss.OssService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 把聊天里用户附带的图片转成一段文字描述，供主模型理解图片内容。
 * <p>
 * 刻意不走工具调用：描述在请求装配阶段生成一次并注入上下文，三条 Agent 路径
 * （流式 / 非流式 / 推理）因此都自动拿到，模型也无须自己填写图片 URL。
 * <p>
 * 复用 {@link VisionService#describeImages}（带 30 天 Redis 缓存、张数上限、长度截断与并发），
 * 并且刻意**关掉 OCR**：OCR 一张文字密集的图可能要几十秒，而大多数图片不需要。
 * 需要图上文字时，模型自己调用 OCR 工具（{@code readImageTextFunction}）按需触发。
 */
@Service
public class ChatImageCaptionService {

    private static final Logger log = LoggerFactory.getLogger(ChatImageCaptionService.class);

    private static final int MAX_CHAT_CAPTION_IMAGES = 3;

    private final VisionService visionService;
    private final OssService ossService;

    public ChatImageCaptionService(VisionService visionService, @Lazy OssService ossService) {
        this.visionService = visionService;
        this.ossService = ossService;
    }

    /**
     * @return 描述文本；无可用图片、视觉服务未配置或调用失败时返回 "" —— 永不抛出。
     */
    public String describeForChat(UserEntity user, List<String> imageUrls) {
        List<String> accepted = acceptedUrls(imageUrls);
        if (accepted.isEmpty()) {
            return "";
        }
        Long userId = user == null ? null : user.getId();
        if (!visionService.isConfigured()) {
            log.warn("VLM 未配置，跳过聊天图片描述 userId={} count={}", userId, accepted.size());
            return "";
        }

        // 与 URL 一一对应：VisionService.buildImageTasks 按位置取 meta。
        // channel 用 "normal"（不是 "text"）—— 默认只跑视觉模型，快得多；
        // 需要图上的文字时由模型自己调用 readImageTextFunction 触发 OCR。
        List<DiaryImageMeta> metas = new ArrayList<>(accepted.size());
        for (String url : accepted) {
            DiaryImageMeta meta = new DiaryImageMeta();
            meta.setUrl(url);
            meta.setChannel("normal");
            metas.add(meta);
        }

        try {
            String caption = visionService.describeImages(accepted, metas, null);
            return caption == null ? "" : caption;
        } catch (Exception e) {
            log.warn("聊天图片描述失败 userId={} count={} reason={}", userId, accepted.size(), e.getMessage());
            return "";
        }
    }

    /**
     * 只接受本桶内的 http(s) 链接。
     * <p>
     * {@code VisionService} 会对传入 URL 发真实请求，而这些 URL 来自客户端，
     * 所以必须在这里挡住任意地址（SSRF 防线）。同时保序去重并限制张数。
     */
    private List<String> acceptedUrls(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty() || ossService == null) {
            return List.of();
        }
        LinkedHashSet<String> accepted = new LinkedHashSet<>();
        for (String raw : imageUrls) {
            if (raw == null) {
                continue;
            }
            String url = raw.trim();
            if (url.isEmpty() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
                continue;
            }
            if (ossService.extractObjectKey(url) == null) {
                log.warn("忽略不属于本存储桶的图片链接: {}", url);
                continue;
            }
            accepted.add(url);
            if (accepted.size() >= MAX_CHAT_CAPTION_IMAGES) {
                break;
            }
        }
        return new ArrayList<>(accepted);
    }
}
