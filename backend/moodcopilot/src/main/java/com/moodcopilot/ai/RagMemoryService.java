package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.DiaryEntity;
import com.moodcopilot.entity.MusicMeta;
import com.moodcopilot.entity.UserProfileMemoryEntity;
import com.moodcopilot.mapper.DiaryMapper;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.output.NestedMultiOutput;
import io.lettuce.core.output.StatusOutput;
import io.lettuce.core.protocol.CommandArgs;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

// TODO: 跨用户共鸣检索（RESONANCE）必须强制添加过滤条件，仅检索 Visibility=PUBLIC 的日记，严禁越权搜索他人私密日记。
@Service
public class RagMemoryService {

    private static final Logger log = LoggerFactory.getLogger(RagMemoryService.class);
    private static final String INDEX_NAME = "idx:rag_v2";
    private static final String KEY_PREFIX = "rag:";
    public static final String SOURCE_DIARY = "diary";
    public static final String SOURCE_PROFILE = "profile";
    public static final String SOURCE_MUSIC = "music";
    public static final String SOURCE_IMAGE = "image";
    public static final String SOURCE_GRAPH = "graph";

    private final String embeddingApiUrl;
    private final String embeddingApiKey;
    private final String embeddingModel;
    private final int embeddingDimension;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final DiaryMapper diaryMapper;

    public RagMemoryService(
            @Value("${spring.ai.rag.embedding.api-url}") String embeddingApiUrl,
            @Value("${spring.ai.rag.embedding.api-key:}") String embeddingApiKey,
            @Value("${spring.ai.rag.embedding.model:BAAI/bge-m3}") String embeddingModel,
            @Value("${spring.ai.rag.embedding.dimension:1024}") int embeddingDimension,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            DiaryMapper diaryMapper) {
        this.embeddingApiUrl = embeddingApiUrl;
        this.embeddingApiKey = embeddingApiKey == null ? "" : embeddingApiKey.trim();
        this.embeddingModel = embeddingModel == null || embeddingModel.isBlank() ? "BAAI/bge-m3" : embeddingModel.trim();
        this.embeddingDimension = embeddingDimension;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
        this.diaryMapper = diaryMapper;
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    void cleanupShortLegacyEmbeddings() {
        try {
            int cleaned = cleanupShortDiaryEmbeddings();
            if (cleaned > 0) {
                log.info("RAG 启动时清理存量短日记向量完成，移除 {} 条", cleaned);
            }
        } catch (Exception e) {
            log.warn("RAG 启动时清理存量短日记向量失败: {}", e.getMessage());
        }
    }

    @PostConstruct
    void initIndex() {
        // 仅在索引不存在时创建，避免每次重启用 DD 清除所有持久化向量数据。
        try {
            redis.execute((RedisCallback<Object>) conn -> {
                var cmds = getSyncCommands(conn);
                CommandArgs<byte[], byte[]> cargs = new CommandArgs<>(ByteArrayCodec.INSTANCE)
                        .add(INDEX_NAME.getBytes(StandardCharsets.UTF_8))
                        .add("ON").add("HASH").add("PREFIX").add("1").add(KEY_PREFIX)
                        .add("SCHEMA")
                        .add("user_id").add("NUMERIC").add("SORTABLE")
                        .add("source_type").add("TAG")
                        .add("content").add("TEXT")
                        .add("embedding").add("VECTOR").add("HNSW").add("6")
                        .add("DIM").add(String.valueOf(embeddingDimension))
                        .add("TYPE").add("FLOAT32").add("DISTANCE_METRIC").add("COSINE")
                        .add("created_at").add("NUMERIC").add("SORTABLE");
                cmds.dispatch(RediSearchCommand.FT_CREATE,
                        new StatusOutput<>(ByteArrayCodec.INSTANCE), cargs);
                return null;
            });
            log.info("RAG 向量索引已创建（v2，含 source_type），dimension={}", embeddingDimension);
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("Index already exists")) {
                log.info("RAG 向量索引已存在，跳过创建");
            } else {
                log.warn("RAG 向量索引初始化失败（请确认 Redis Stack 已部署）: {}", msg);
            }
        }
    }

    /**
     * 调用 SiliconFlow BAAI/bge-m3 API 生成 embedding（1024 维）。
     * API 兼容 OpenAI embeddings 格式。
     */
    @SuppressWarnings("unchecked")
    public float[] embed(String text) {
        if (embeddingApiKey.isBlank()) {
            return null;
        }
        if (text == null || text.isBlank()) {
            return null;
        }

        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String response = restClient.post()
                        .uri(embeddingApiUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + embeddingApiKey)
                        .body(Map.of(
                                "model", embeddingModel,
                                "input", text,
                                "encoding_format", "float"))
                        .retrieve()
                        .body(String.class);

                if (response == null || response.isBlank()) {
                    return null;
                }
                Map<String, Object> parsed = objectMapper.readValue(response, Map.class);
                List<Map<String, Object>> data = (List<Map<String, Object>>) parsed.get("data");
                if (data == null || data.isEmpty()) {
                    log.warn("Embedding 响应 data 为空，response 前 200 字符: {}", response.length() > 200 ? response.substring(0, 200) : response);
                    return null;
                }
                List<Number> raw = (List<Number>) data.get(0).get("embedding");
                if (raw == null) {
                    return null;
                }
                float[] embedding = new float[raw.size()];
                for (int i = 0; i < raw.size(); i++) {
                    embedding[i] = raw.get(i).floatValue();
                }
                log.info("Embedding 生成成功，dimension={}", embedding.length);
                return embedding;
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                // 4xx: 客户端错误（认证失败、模型不存在等），重试无意义
                log.error("Embedding API 客户端错误 ({} {})，不再重试: {}",
                        e.getStatusCode().value(), e.getStatusText(), e.getResponseBodyAsString());
                return null;
            } catch (org.springframework.web.client.HttpServerErrorException e) {
                // 5xx: 服务端错误，可能是临时故障，指数退避后重试
                String body = e.getResponseBodyAsString();
                if (attempt < maxRetries) {
                    long delayMs = (long) (Math.pow(2, attempt) * 1000 + Math.random() * 1000);
                    log.warn("Embedding API 服务端错误 ({} {})，{}ms 后重试 (尝试 {}/{}): {}",
                            e.getStatusCode().value(), e.getStatusText(), delayMs, attempt, maxRetries, body);
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    log.error("Embedding API 服务端错误，已重试 {} 次仍失败 ({} {}): {}",
                            maxRetries, e.getStatusCode().value(), e.getStatusText(), body);
                }
            } catch (Exception e) {
                // 网络/IO 异常，可能是临时网络问题
                if (attempt < maxRetries) {
                    long delayMs = (long) (Math.pow(2, attempt) * 1000 + Math.random() * 1000);
                    log.warn("Embedding 网络异常，{}ms 后重试 (尝试 {}/{}): {}", delayMs, attempt, maxRetries, e.getMessage());
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    log.error("Embedding 网络异常，已重试 {} 次仍失败: {}", maxRetries, e.getMessage());
                }
            }
        }
        return null;
    }

    @Async
    public void indexKnowledgeGraph(long userId, long diaryId, long graphId, String head, String relation, String tail) {
        String content = "记忆图谱：" + head + " " + relation + " " + tail;
        float[] vector = embed(content);
        if (vector == null) {
            return;
        }
        storeEmbedding("graph:" + graphId, userId, SOURCE_GRAPH, content, vector);
        log.info("RAG 知识图谱三元组已索引, userId={}, graphId={}", userId, graphId);
    }

    public void deleteKnowledgeGraph(long graphId) {
        String key = KEY_PREFIX + "graph:" + graphId;
        redis.delete(key);
    }

    /**
     * 异步：将日记内容 embedding 后存入 Redis vector index。
     */
    private static final int CHUNK_SIZE = 400;
    private static final int CHUNK_OVERLAP = 50;

    @Async("aiExecutor")
    public void indexDiary(long userId, long diaryId, String content) {
        indexDiary(userId, diaryId, content, null);
    }

    /** 索引日记内容 + 可选的独立音乐元数据向量条目，让音乐查询不被日记正文稀释。 */
    @Async("aiExecutor")
    public void indexDiary(long userId, long diaryId, String content, MusicMeta musicMeta) {
        if (content == null || content.isBlank()) {
            log.debug("RAG 索引跳过：日记内容为空 diaryId={}", diaryId);
            return;
        }
        // 纯文本长度不足的不进向量索引（如 "1"、"好想哭" 等无上下文价值的超短日记）
        String plainText = content.replaceAll("<[^>]+>", "").replaceAll("&nbsp;", " ").trim();
        if (plainText.length() < 8) {
            log.debug("RAG 索引跳过：日记纯文本过短 ({} chars) diaryId={}", plainText.length(), diaryId);
            return;
        }
        // 独立索引音乐元数据，避免被长篇日记稀释语义
        if (musicMeta != null && musicMeta.getTitle() != null && !musicMeta.getTitle().isBlank()) {
            String musicText = buildMusicIndexText(musicMeta);
            float[] musicVec = embed(musicText);
            if (musicVec != null) {
                storeEmbedding("diary:" + diaryId + ":music", userId, SOURCE_MUSIC, musicText, musicVec);
                log.info("RAG 已索引音乐元数据 diaryId={} ", diaryId);
            }
        }
        if (content.length() <= 500) {
            float[] vec = embed(content);
            if (vec == null) {
                log.warn("RAG 索引失败：embedding 生成失败 diaryId={}", diaryId);
                return;
            }
            storeEmbedding("diary:" + diaryId, userId, SOURCE_DIARY, content, vec);
            log.info("RAG 已索引日记 diaryId={} userId={} dim={}", diaryId, userId, vec.length);
        } else {
            // 长日记分块索引：400字/块，50字重叠
            int chunks = 0;
            int start = 0;
            while (start < content.length()) {
                int end = Math.min(start + CHUNK_SIZE, content.length());
                String chunk = content.substring(start, end);
                float[] vec = embed(chunk);
                if (vec != null) {
                    storeEmbedding("diary:" + diaryId + ":" + chunks, userId, SOURCE_DIARY, chunk, vec);
                    chunks++;
                }
                start += CHUNK_SIZE - CHUNK_OVERLAP;
            }
            log.info("RAG 已索引日记（分块） diaryId={} userId={} chunks={}", diaryId, userId, chunks);
        }
    }

    /**
     * 将用户长期画像按属性逐个索引到向量库（key: profile:{userId}:{attrKey}）。
     * 每个属性独立存储，便于语义检索时精准匹配。
     */
    @Async("aiExecutor")
    public void indexUserProfile(long userId, List<UserProfileMemoryEntity> memories) {
        redis.delete(KEY_PREFIX + "profile:" + userId);
        if (memories == null || memories.isEmpty()) {
            List<String> keys = listProfileKeys(userId);
            if (!keys.isEmpty()) {
                redis.delete(keys);
                log.info("RAG 画像已清空 userId={} deletedKeys={}", userId, keys.size());
            }
            return;
        }
        List<String> existingKeys = listProfileKeys(userId);
        Set<String> newKeys = new java.util.HashSet<>();
        int indexed = 0;
        for (UserProfileMemoryEntity m : memories) {
            String attrKey = sanitizeKey(m.getAttributeKey());
            String text = "用户长期画像 - " + m.getAttributeKey() + ": " + m.getAttributeValue();
            float[] vec = embed(text);
            if (vec != null) {
                storeEmbedding("profile:" + userId + ":" + attrKey, userId, SOURCE_PROFILE, text, vec);
                newKeys.add("profile:" + userId + ":" + attrKey);
                indexed++;
            }
        }
        int deleted = 0;
        for (String oldKey : existingKeys) {
            if (!newKeys.contains(oldKey)) {
                redis.delete(oldKey);
                deleted++;
            }
        }
        log.info("RAG 画像已更新 userId={} indexed={} deleted={}", userId, indexed, deleted);
    }

    private List<String> listProfileKeys(long userId) {
        String pattern = KEY_PREFIX + "profile:" + userId + ":*";
        try {
            var keys = redis.keys(pattern);
            return keys != null ? new ArrayList<>(keys) : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    private String sanitizeKey(String raw) {
        return raw.replaceAll("[^a-zA-Z0-9_\\-\\u4e00-\\u9fff]", "_");
    }

    /**
     * 独立索引图片描述向量，与日记正文分开，让图片查询不被稀释。
     * 由 runAiAnalysis 在 VLM 描述就绪后调用。
     */
    public void indexDiaryImages(long userId, long diaryId, String imageDescriptions) {
        if (imageDescriptions == null || imageDescriptions.isBlank()) return;
        String labeled = "【图片描述】" + imageDescriptions;
        float[] vec = embed(labeled);
        if (vec != null) {
            storeEmbedding("diary:" + diaryId + ":images", userId, SOURCE_IMAGE, labeled, vec);
            log.info("RAG 已索引图片描述 diaryId={} len={}", diaryId, labeled.length());
        }
    }

    /** 将音乐元数据组装为独立的检索文本，与日记正文分开索引以提高音乐查询命中率。 */
    static String buildMusicIndexText(MusicMeta musicMeta) {
        StringBuilder sb = new StringBuilder();
        sb.append("歌曲：").append(musicMeta.getTitle())
          .append(" 歌手：").append(musicMeta.getArtist());
        if (musicMeta.getUserLyric() != null && !musicMeta.getUserLyric().isBlank()) {
            sb.append(" 歌词：").append(musicMeta.getUserLyric());
        }
        return sb.toString();
    }

    private void storeEmbedding(String id, long userId, String sourceType, String content, float[] embedding) {
        String key = KEY_PREFIX + id;
        byte[] rawKey = key.getBytes(StandardCharsets.UTF_8);
        byte[] vecBytes = floatsToBytes(embedding);

        redis.execute((RedisCallback<Object>) conn -> {
            byte[] uid = "user_id".getBytes(StandardCharsets.UTF_8);
            byte[] st = "source_type".getBytes(StandardCharsets.UTF_8);
            byte[] cnt = "content".getBytes(StandardCharsets.UTF_8);
            byte[] emb = "embedding".getBytes(StandardCharsets.UTF_8);
            byte[] cat = "created_at".getBytes(StandardCharsets.UTF_8);

            conn.hashCommands().hSet(rawKey, uid, String.valueOf(userId).getBytes(StandardCharsets.UTF_8));
            conn.hashCommands().hSet(rawKey, st, sourceType.getBytes(StandardCharsets.UTF_8));
            conn.hashCommands().hSet(rawKey, cnt, content.getBytes(StandardCharsets.UTF_8));
            conn.hashCommands().hSet(rawKey, emb, vecBytes);
            conn.hashCommands().hSet(rawKey, cat,
                    String.valueOf(System.currentTimeMillis() / 1000).getBytes(StandardCharsets.UTF_8));
            conn.expire(rawKey, 90 * 86400);
            return null;
        });
    }

    /**
     * 向量相似搜索（无时间过滤）。向后兼容。
     */
    public List<RagHit> search(long userId, String query, int topK, String... sourceTypes) {
        return search(userId, query, topK, null, sourceTypes);
    }

    /**
     * 向量相似搜索（可选时间过滤）。
     * 当 timeRange 不为 null 时，RediSearch 查询追加 @created_at:[from to] 标量过滤。
     */
    @SuppressWarnings("unchecked")
    public List<RagHit> search(long userId, String query, int topK,
            TimeExpressionParser.TimeRange timeRange, String... sourceTypes) {
        float[] queryVec = embed(query);
        if (queryVec == null || queryVec.length == 0) {
            return List.of();
        }
        byte[] queryVector = floatsToBytes(queryVec);

        log.info("RAG 开始搜索 userId={} queryLen={} topK={} timeRange=[from={} to={}] sourceTypes={}",
                userId, query.length(), topK,
                timeRange != null ? formatEpoch(timeRange.fromTimestamp()) : "无",
                timeRange != null ? formatEpoch(timeRange.toTimestamp()) : "无",
                Arrays.toString(sourceTypes));

        // 严格遵循 RediSearch Hybrid Query 语法：所有过滤条件必须在同一对括号内，且 => 前无空格
        StringBuilder fb = new StringBuilder("(@user_id:[").append(userId).append(" ").append(userId).append("]");
        if (sourceTypes.length > 0) {
            fb.append(" @source_type:{");
            for (int i = 0; i < sourceTypes.length; i++) {
                if (i > 0) fb.append("|");
                fb.append(sourceTypes[i]);
            }
            fb.append("}");
        }
        if (timeRange != null) {
            fb.append(" @created_at:[").append(timeRange.fromTimestamp())
                    .append(" ").append(timeRange.toTimestamp()).append("]");
        }
        fb.append(")");
        String filter = fb.toString();

        String knn = "=>[KNN " + topK + " @embedding $vec AS _score]";
        String q = filter + knn;

        log.info("RAG 搜索 query string: {}", q);

        try {
            return redis.execute((RedisCallback<List<RagHit>>) conn -> {
                var cmds = getSyncCommands(conn);
                List<RagHit> hits = new ArrayList<>();
                CommandArgs<byte[], byte[]> cargs = new CommandArgs<>(ByteArrayCodec.INSTANCE)
                        .add(INDEX_NAME.getBytes(StandardCharsets.UTF_8))
                        .add(q.getBytes(StandardCharsets.UTF_8))
                        .add("PARAMS".getBytes(StandardCharsets.UTF_8))
                        .add("2".getBytes(StandardCharsets.UTF_8))
                        .add("vec".getBytes(StandardCharsets.UTF_8))
                        .add(queryVector)
                        .add("RETURN".getBytes(StandardCharsets.UTF_8))
                        .add("2".getBytes(StandardCharsets.UTF_8))
                        .add("content".getBytes(StandardCharsets.UTF_8))
                        .add("_score".getBytes(StandardCharsets.UTF_8))
                        .add("DIALECT".getBytes(StandardCharsets.UTF_8))
                        .add("2".getBytes(StandardCharsets.UTF_8));
                List<Object> raw = cmds.dispatch(
                        RediSearchCommand.FT_SEARCH,
                        new NestedMultiOutput<>(ByteArrayCodec.INSTANCE),
                        cargs);
                if (raw != null && !raw.isEmpty()) {
                    Object rawCount = raw.get(0);
                    log.info("RAG Redis底层原始命中数: {}", rawCount);
                    parseResults(raw, hits);
                }
                hits.sort(java.util.Comparator.comparing(RagHit::score, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
                int rawHits = hits.size();
                List<RagHit> validHits = hits.stream()
                        .filter(h -> h.score() != null && h.score() > 0.001 && h.score() < 0.55)
                        .toList();
                validHits = new ArrayList<>(validHits);
                validHits.sort(java.util.Comparator.comparing(RagHit::score, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
                
                // 动态阈值截断：用相对比例排除与 Top 1 差距过大的噪音，Top 1 本身太弱时直接丢弃
                List<RagHit> qualityHits = new ArrayList<>();
                if (!validHits.isEmpty()) {
                    double topScore = validHits.get(0).score();
                    // Top 1 质量过低说明检索无意义，直接返回空
                    if (topScore < 0.03) {
                        log.info("RAG 检索跳过：topScore={} 过低，无有效匹配", String.format("%.3f", topScore));
                        return List.of();
                    }
                    // 相对阈值：排除超过 Top 1 3 倍的噪音，同时硬上限 0.55
                    double threshold = Math.min(0.55, Math.max(topScore * 3.0, topScore + 0.10));
                    qualityHits = validHits.stream().filter(h -> h.score() <= threshold).toList();
                }
                qualityHits = new ArrayList<>(qualityHits);
                qualityHits.sort(java.util.Comparator.comparing(RagHit::score, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
                log.info("RAG 搜索完成 userId={} totalHits={} qualityHits={}",
                        userId, rawHits, qualityHits.size());

                if (!qualityHits.isEmpty()) {
                    double topScore = qualityHits.get(0).score() != null ? qualityHits.get(0).score() : -1;
                    double avgScore = qualityHits.stream().filter(h -> h.score() != null)
                            .mapToDouble(RagHit::score).average().orElse(-1);
                    log.info("RAG qualityHits topScore={} avgScore={}",
                            String.format("%.3f", topScore), String.format("%.3f", avgScore));
                }
                return qualityHits;
            });
        } catch (Exception e) {
            log.warn("RAG 搜索失败 userId={}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    public String buildRagContext(long userId, String query, int topK, String... sourceTypes) {
        return buildRagContext(userId, query, topK, null, sourceTypes);
    }

    public String buildRagContext(long userId, String query, int topK,
            TimeExpressionParser.TimeRange timeRange, String... sourceTypes) {
        List<RagHit> hits = search(userId, query, topK, timeRange, sourceTypes);
        if (hits.isEmpty()) {
            log.info("RAG 上下文为空 userId={} queryLen={} timeRange={}", userId, query.length(),
                    timeRange != null ? "[" + formatEpoch(timeRange.fromTimestamp()) + " ~ " + formatEpoch(timeRange.toTimestamp()) + "]" : "无");
            return "";
        }
        return buildHydratedRagContext(hits);
    }

    public com.moodcopilot.diary.DiarySearchResult searchForTool(long userId, com.moodcopilot.diary.DiarySearchRequest request) {
        String keyword = request != null && request.keyword() != null ? request.keyword().trim() : "";
        java.time.LocalDate startDate = request != null ? request.startDate() : null;
        java.time.LocalDate endDate = request != null ? request.endDate() : null;

        if (keyword.isBlank()) {
            return null; // fallback to DiaryService
        }

        TimeExpressionParser.TimeRange timeRange = null;
        if (startDate != null || endDate != null) {
            long fromTs = startDate != null ? startDate.atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond() : 0;
            long toTs = endDate != null ? endDate.atTime(java.time.LocalTime.MAX).atZone(java.time.ZoneId.systemDefault()).toEpochSecond() : Long.MAX_VALUE;
            timeRange = new TimeExpressionParser.TimeRange(fromTs, toTs);
        }

        List<RagHit> hits = search(userId, keyword, 20, timeRange, SOURCE_DIARY, SOURCE_MUSIC, SOURCE_IMAGE);
        
        java.util.Set<Long> diaryIds = new java.util.LinkedHashSet<>();
        for (RagHit hit : hits) {
            if (hit.diaryId() != null) diaryIds.add(hit.diaryId());
        }

        List<com.moodcopilot.diary.DiarySearchResult.DiarySummary> summaries = new ArrayList<>();
        if (!diaryIds.isEmpty()) {
            try {
                var entities = diaryMapper.selectBatchIds(new ArrayList<>(diaryIds));
                java.util.Map<Long, DiaryEntity> diaryMap = entities.stream()
                        .collect(java.util.stream.Collectors.toMap(DiaryEntity::getId, e -> e));
                
                // Group hits by diaryId to find matched image/music hits
                java.util.Map<Long, java.util.List<RagHit>> hitsByDiaryId = new java.util.HashMap<>();
                for (RagHit hit : hits) {
                    if (hit.diaryId() != null) {
                        hitsByDiaryId.computeIfAbsent(hit.diaryId(), k -> new ArrayList<>()).add(hit);
                    }
                }

                // Keep the order of semantic relevance from hits
                for (Long id : diaryIds) {
                    DiaryEntity d = diaryMap.get(id);
                    if (d != null && d.getCreatedAt() != null) {
                        StringBuilder prefixSb = new StringBuilder();
                        
                        // Check if there is music meta
                        if (d.getMusicMeta() != null && d.getMusicMeta().getTitle() != null && !d.getMusicMeta().getTitle().isBlank()) {
                            prefixSb.append("[分享音乐：").append(d.getMusicMeta().getTitle());
                            if (d.getMusicMeta().getArtist() != null && !d.getMusicMeta().getArtist().isBlank()) {
                                prefixSb.append(" - ").append(d.getMusicMeta().getArtist());
                            }
                            prefixSb.append("] ");
                        }
                        
                        // Check if there is matched image description in RAG hits
                        List<RagHit> diaryHits = hitsByDiaryId.getOrDefault(id, List.of());
                        String matchedImageDesc = null;
                        for (RagHit h : diaryHits) {
                            if (SOURCE_IMAGE.equals(h.sourceType()) && h.content() != null && !h.content().isBlank()) {
                                matchedImageDesc = h.content();
                                if (matchedImageDesc.startsWith("【图片描述】")) {
                                    matchedImageDesc = matchedImageDesc.substring("【图片描述】".length());
                                }
                                break;
                            }
                        }
                        
                        if (matchedImageDesc != null) {
                            prefixSb.append("[图片描述：").append(matchedImageDesc).append("] ");
                        } else if (d.getImages() != null && !d.getImages().isEmpty()) {
                            prefixSb.append("[分享图片] ");
                        }

                        String snippet = d.getContent();
                        if (snippet != null && snippet.length() > 500) {
                            snippet = snippet.substring(0, 500) + "...";
                        }
                        
                        String finalSnippet;
                        if (snippet == null || snippet.isBlank()) {
                            finalSnippet = prefixSb.toString().trim();
                        } else {
                            finalSnippet = snippet.trim() + (prefixSb.length() > 0 ? " " + prefixSb.toString().trim() : "");
                        }
                        summaries.add(new com.moodcopilot.diary.DiarySearchResult.DiarySummary(
                            d.getId(), d.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), finalSnippet));
                    }
                }
            } catch (Exception e) {
                log.error("RAG 回表查询日记失败: {}", e.getMessage());
            }
        }

        String note = summaries.isEmpty()
                ? "未找到符合条件的语义检索结果，请尝试其他关键词或时间范围。"
                : "已返回向量语义检索命中的历史记录（包含日记、图片描述、音乐元数据等）。";

        return new com.moodcopilot.diary.DiarySearchResult(keyword, startDate, endDate, summaries.size(), summaries, note);
    }

    /**
     * 将向量检索命中的 Top-K 结果，按 diaryId 去重后回表 MySQL 查询完整日记实体，
     * 再按命中源类型（文本/音乐/图片）组装为结构化 XML 上下文供大模型推理。
     */
    private String buildHydratedRagContext(List<RagHit> hits) {
        // 提取去重 diaryId
        java.util.Set<Long> diaryIds = new java.util.LinkedHashSet<>();
        for (RagHit hit : hits) {
            if (hit.diaryId() != null) {
                diaryIds.add(hit.diaryId());
            }
        }

        // 回表批量查询完整日记
        java.util.Map<Long, DiaryEntity> diaryMap = java.util.Map.of();
        if (!diaryIds.isEmpty()) {
            try {
                var entities = diaryMapper.selectBatchIds(new ArrayList<>(diaryIds));
                diaryMap = entities.stream()
                        .collect(java.util.stream.Collectors.toMap(DiaryEntity::getId, e -> e, (a, b) -> a));
            } catch (Exception e) {
                log.error("RAG 回表查询日记失败: {}", e.getMessage());
            }
        }

        final java.util.Map<Long, DiaryEntity> finalDiaryMap = diaryMap;
        // Sort hits: profile items first, then by diary.createdAt ASC
        List<RagHit> sortedHits = new ArrayList<>(hits);
        sortedHits.sort((h1, h2) -> {
            boolean isProfile1 = h1.diaryId() == null;
            boolean isProfile2 = h2.diaryId() == null;
            if (isProfile1 && !isProfile2) return -1;
            if (!isProfile1 && isProfile2) return 1;
            if (isProfile1 && isProfile2) return 0;

            DiaryEntity d1 = finalDiaryMap.get(h1.diaryId());
            DiaryEntity d2 = finalDiaryMap.get(h2.diaryId());
            if (d1 == null && d2 == null) return 0;
            if (d1 == null) return 1;
            if (d2 == null) return -1;
            if (d1.getCreatedAt() == null && d2.getCreatedAt() == null) return 0;
            if (d1.getCreatedAt() == null) return 1;
            if (d2.getCreatedAt() == null) return -1;
            return d1.getCreatedAt().compareTo(d2.getCreatedAt());
        });

        StringBuilder sb = new StringBuilder("\n\n<rag_retrieved_context>\n");
        sb.append("以下是与用户当前问题语义相关的历史记录（由向量检索自动获取，已回表关联完整日记数据，按时间先后顺序排列）。\n");
        sb.append("注意：这些记录中可能包含用户上传图片的视觉描述文本（由视觉模型生成），以及用户分享音乐的文字元数据（歌名/歌手/歌词）。如果你检索到了这些内容，请直接当作你亲眼看到或亲耳听到的信息来理解和引用，不要对用户强调「这是文字描述」或「我无法查看图片/播放音乐」。\n");

        java.util.Set<String> rendered = new java.util.HashSet<>();
        int itemIndex = 0;
        for (RagHit hit : sortedHits) {
            if (hit.diaryId() == null || hit.sourceType() == null) {
                // 非日记来源（如 profile），直接输出原始内容
                String snippet = hit.content().length() > 500
                        ? hit.content().substring(0, 500) + "…"
                        : hit.content();
                sb.append("<context_item type=\"profile_memory\">\n");
                sb.append("  <profile_content>").append(escapeXml(snippet)).append("</profile_content>\n");
                sb.append("</context_item>\n");
                continue;
            }

            DiaryEntity diary = diaryMap.get(hit.diaryId());
            if (diary == null) continue;

            String dateStr = diary.getCreatedAt() != null
                    ? diary.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    : "";

            // 同一日记在同一类型下只渲染一次
            String dedupKey = hit.diaryId() + ":" + hit.sourceType();
            if (!rendered.add(dedupKey)) continue;

            itemIndex++;
            switch (hit.sourceType()) {
                case SOURCE_MUSIC -> {
                    sb.append("<context_item type=\"music_resonance\" diary_id=\"").append(hit.diaryId())
                      .append("\" date=\"").append(dateStr).append("\">\n");
                    if (diary.getContent() != null) {
                        sb.append("  <diary_content>").append(escapeXml(truncate(diary.getContent(), 500)))
                          .append("</diary_content>\n");
                    }
                    MusicMeta music = diary.getMusicMeta();
                    if (music != null) {
                        sb.append("  <music_meta>歌曲: ").append(escapeXml(music.getTitle()))
                          .append(", 歌手: ").append(escapeXml(music.getArtist()));
                        if (music.getUserLyric() != null && !music.getUserLyric().isBlank()) {
                            sb.append(", 歌词: ").append(escapeXml(music.getUserLyric()));
                        }
                        sb.append("</music_meta>\n");
                    }
                    sb.append("</context_item>\n");
                }
                case SOURCE_IMAGE -> {
                    sb.append("<context_item type=\"image_memory\" diary_id=\"").append(hit.diaryId())
                      .append("\" date=\"").append(dateStr).append("\">\n");
                    if (diary.getContent() != null) {
                        sb.append("  <diary_content>").append(escapeXml(truncate(diary.getContent(), 500)))
                          .append("</diary_content>\n");
                    }
                    sb.append("  <image_description>").append(escapeXml(truncate(hit.content(), 500)))
                      .append("</image_description>\n");
                    sb.append("</context_item>\n");
                }
                default -> {
                    sb.append("<context_item type=\"text_memory\" diary_id=\"").append(hit.diaryId())
                      .append("\" date=\"").append(dateStr).append("\">\n");
                    if (diary.getContent() != null) {
                        sb.append("  <diary_content>").append(escapeXml(truncate(diary.getContent(), 500)))
                          .append("</diary_content>\n");
                    }
                    MusicMeta music = diary.getMusicMeta();
                    if (music != null) {
                        sb.append("  <music_meta>歌曲: ").append(escapeXml(music.getTitle()))
                          .append(", 歌手: ").append(escapeXml(music.getArtist()));
                        if (music.getUserLyric() != null && !music.getUserLyric().isBlank()) {
                            sb.append(", 歌词: ").append(escapeXml(music.getUserLyric()));
                        }
                        sb.append("</music_meta>\n");
                    }
                    sb.append("</context_item>\n");
                }
            }
        }

        if (itemIndex == 0 && rendered.isEmpty()) {
            log.info("RAG 回表后无有效日记上下文");
            return "";
        }

        sb.append("请结合以上检索到的历史信息进行分析。不要在回复中提及'向量检索'或暴露相关度分数。");
        sb.append("\n</rag_retrieved_context>");
        log.info("RAG 已组装回表上下文，命中日记数={} 渲染条目数={}", diaryIds.size(), itemIndex);
        return sb.toString();
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
    }

    // ── 内部工具 ──

    private static String formatEpoch(long epochSecond) {
        return TimeExpressionParser.formatDateTime(epochSecond);
    }

    private static byte[] floatsToBytes(float[] floats) {
        ByteBuffer buf = ByteBuffer.allocate(floats.length * Float.BYTES);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        for (float f : floats) {
            buf.putFloat(f);
        }
        return buf.array();
    }

    @SuppressWarnings("unchecked")
    private static io.lettuce.core.api.sync.RedisCommands<byte[], byte[]> getSyncCommands(
            org.springframework.data.redis.connection.RedisConnection conn) {
        Object nativeConn = conn.getNativeConnection();
        if (nativeConn instanceof io.lettuce.core.api.async.RedisAsyncCommands<?, ?> async) {
            return (io.lettuce.core.api.sync.RedisCommands<byte[], byte[]>) async.getStatefulConnection().sync();
        } else if (nativeConn instanceof io.lettuce.core.api.StatefulRedisConnection<?, ?> stateful) {
            return (io.lettuce.core.api.sync.RedisCommands<byte[], byte[]>) stateful.sync();
        }
        throw new IllegalStateException("无法获取 Lettuce 同步连接，native: " + nativeConn.getClass().getName());
    }

    private String snippet(String content, int maxLen) {
        if (content.length() <= maxLen) {
            return content;
        }
        return content.substring(0, maxLen) + "...";
    }

    @SuppressWarnings("unchecked")
    private void parseResults(List<?> raw, List<RagHit> out) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        // 智能提取：兼容 RESP2 (数组) 和 RESP3 (Map) 格式
        extractHitsHeuristically(raw, out);
    }

    private void extractHitsHeuristically(List<?> list, List<RagHit> out) {
        String currentKey = null;
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);

            // 识别 RESP2 的 key
            if ((item instanceof byte[] || item instanceof String) && i + 1 < list.size() && list.get(i + 1) instanceof List<?>) {
                currentKey = asString(item);
            }

            if (item instanceof List<?> subList) {
                String content = null;
                Double score = null;
                String resp3Id = null;

                // 尝试提取 content 和 _score
                for (int j = 0; j + 1 < subList.size(); j += 2) {
                    String k = asString(subList.get(j));
                    if ("id".equals(k)) { // RESP3 的 key
                        resp3Id = asString(subList.get(j + 1));
                    } else if ("extra_attributes".equals(k) && subList.get(j + 1) instanceof List<?> extra) {
                        // RESP3 的字段嵌套在 extra_attributes 中
                        for (int k2 = 0; k2 + 1 < extra.size(); k2 += 2) {
                            String ek = asString(extra.get(k2));
                            if ("content".equals(ek)) {
                                content = asString(extra.get(k2 + 1));
                            } else if ("_score".equals(ek)) {
                                score = parseScore(extra.get(k2 + 1));
                            }
                        }
                    } else if ("content".equals(k)) { // RESP2 的字段
                        content = asString(subList.get(j + 1));
                    } else if ("_score".equals(k)) {
                        score = parseScore(subList.get(j + 1));
                    }
                }

                String finalKey = resp3Id != null ? resp3Id : currentKey;
                if (content != null && finalKey != null) {
                    RagKeyInfo info = parseRagKey(finalKey);
                    out.add(new RagHit(content, score, info.sourceId, info.diaryId, info.sourceType));
                } else {
                    // 如果当前子列表不是文档，继续向下递归寻找
                    extractHitsHeuristically(subList, out);
                }
            }
        }
    }

    private Double parseScore(Object obj) {
        try {
            return Double.parseDouble(asString(obj));
        } catch (Exception e) {
            return null;
        }
    }

    private static String asString(Object obj) {
        if (obj instanceof byte[] b) {
            return new String(b, StandardCharsets.UTF_8);
        }
        return String.valueOf(obj);
    }

    private record RagKeyInfo(String sourceId, Long diaryId, String sourceType) {}

    private RagKeyInfo parseRagKey(String key) {
        if (key == null) return new RagKeyInfo(null, null, null);
        String id = key.substring(KEY_PREFIX.length());
        String[] parts = id.split(":", 3);
        if (parts.length >= 2 && "diary".equals(parts[0])) {
            try {
                Long diaryId = Long.parseLong(parts[1]);
                String suffix = parts.length > 2 ? parts[2] : null;
                String sourceType;
                if ("music".equals(suffix)) {
                    sourceType = SOURCE_MUSIC;
                } else if ("images".equals(suffix)) {
                    sourceType = SOURCE_IMAGE;
                } else {
                    sourceType = SOURCE_DIARY;
                }
                return new RagKeyInfo(id, diaryId, sourceType);
            } catch (NumberFormatException e) {
                log.warn("RAG key 解析失败，无法提取 diaryId: {}", key);
            }
        }
        return new RagKeyInfo(id, null, parts.length > 0 ? parts[0] : null);
    }

    private enum RediSearchCommand implements io.lettuce.core.protocol.ProtocolKeyword {
        FT_CREATE("FT.CREATE"),
        FT_SEARCH("FT.SEARCH"),
        FT_DROPINDEX("FT.DROPINDEX");

        private final byte[] bytes;

        RediSearchCommand(String name) {
            this.bytes = name.getBytes(StandardCharsets.US_ASCII);
        }

        @Override
        public byte[] getBytes() {
            return bytes;
        }
    }

    public record RagHit(String content, Double score, String sourceId, Long diaryId, String sourceType) {
    }

    /**
     * 批量回填已有日记的向量索引（管理员触发）。
     * 分块策略与日常增量 indexDiary 保持一致：≤500 字单块，>500 字按 400 字/块 + 50 字重叠切分。
     * @param items 待索引的 (userId, diaryId, content) 列表
     * @return 成功索引的日记数量
     */
    public int batchIndexDiaries(List<BatchIndexItem> items) {
        if (embeddingApiKey.isBlank()) {
            log.warn("SILICONFLOW_API_KEY 未配置，跳过批量向量化");
            return 0;
        }
        int count = 0;
        for (BatchIndexItem item : items) {
            if (item.content() == null || item.content().isBlank()) {
                continue;
            }
            try {
                // 幂等：先清理可能存在的旧索引，防止重复或脏数据
                deleteDiaryEmbedding(item.diaryId());

                String content = item.content();
                if (content.length() <= 500) {
                    float[] vec = embed(content);
                    if (vec != null) {
                        storeEmbedding("diary:" + item.diaryId(), item.userId(),
                                SOURCE_DIARY, content, vec);
                        count++;
                    }
                } else {
                    boolean stored = false;
                    int start = 0;
                    int ci = 0;
                    while (start < content.length()) {
                        int end = Math.min(start + CHUNK_SIZE, content.length());
                        String chunk = content.substring(start, end);
                        float[] vec = embed(chunk);
                        if (vec != null) {
                            storeEmbedding("diary:" + item.diaryId() + ":" + ci, item.userId(),
                                    SOURCE_DIARY, chunk, vec);
                            ci++;
                            stored = true;
                        }
                        start += CHUNK_SIZE - CHUNK_OVERLAP;
                    }
                    if (stored) {
                        count++;
                    }
                }
                // 控制频率，避免 SiliconFlow 限流
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("批量向量化失败 diaryId={}: {}", item.diaryId(), e.getMessage());
            }
        }
        log.info("批量向量化完成：{}/{} 条", count, items.size());
        return count;
    }

    /**
     * 同步批量索引用户画像（供 admin reindex 使用）。
     * 回填前彻底清理该用户所有新旧格式的索引 key，确保向量库与传入数据完全同步，不留孤儿数据。
     */
    public int batchIndexProfiles(Map<Long, List<UserProfileMemoryEntity>> grouped) {
        if (embeddingApiKey.isBlank()) {
            return 0;
        }
        int count = 0;
        for (var entry : grouped.entrySet()) {
            long userId = entry.getKey();
            // 彻底清理该用户所有旧索引（新格式属性 key + 旧格式单 blob key）
            List<String> existingKeys = listProfileKeys(userId);
            if (!existingKeys.isEmpty()) {
                redis.delete(existingKeys);
            }
            redis.delete(KEY_PREFIX + "profile:" + userId);

            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            for (UserProfileMemoryEntity m : entry.getValue()) {
                String text = "用户长期画像 - " + m.getAttributeKey() + ": " + m.getAttributeValue();
                float[] vec = embed(text);
                if (vec != null) {
                    String attrKey = sanitizeKey(m.getAttributeKey());
                    storeEmbedding("profile:" + userId + ":" + attrKey,
                            userId, SOURCE_PROFILE, text, vec);
                    count++;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return count;
                }
            }
        }
        log.info("批量画像向量化完成：{} 条属性", count);
        return count;
    }

    /**
     * 删除指定日记的向量（用户删除日记时调用）。
     * 同时清理未分块的 key（短日记）和分块 key（长日记）。
     */
    public void deleteDiaryEmbedding(long diaryId) {
        String baseKey = KEY_PREFIX + "diary:" + diaryId;
        String pattern = KEY_PREFIX + "diary:" + diaryId + ":*";
        try {
            redis.delete(baseKey);
            var keys = redis.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
                log.info("RAG 已删除日记向量 diaryId={} totalKeys={}", diaryId, keys.size() + 1);
            } else {
                log.info("RAG 已删除日记向量 diaryId={}", diaryId);
            }
        } catch (Exception e) {
            log.warn("RAG 删除日记向量失败 diaryId={}: {}", diaryId, e.getMessage());
        }
    }

    /**
     * 清理存量的超短日记正文向量索引（纯文本 < 8 字符），治理历史遗留噪音。
     * 仅删除正文向量，保留音乐和图片的独立向量——短日记「1 + 一首歌」仍有音乐语义价值。
     */
    public int cleanupShortDiaryEmbeddings() {
        var allDiaries = diaryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.moodcopilot.entity.DiaryEntity>()
                        .eq(com.moodcopilot.entity.DiaryEntity::getIsDeleted, false)
        );
        int cleaned = 0;
        for (var diary : allDiaries) {
            String plainText = (diary.getContent() != null ? diary.getContent() : "")
                    .replaceAll("<[^>]+>", "").replaceAll("&nbsp;", " ").trim();
            if (plainText.length() < 8) {
                // 仅删正文向量，保留音乐和图片
                redis.delete(KEY_PREFIX + "diary:" + diary.getId());
                var chunkKeys = redis.keys(KEY_PREFIX + "diary:" + diary.getId() + ":*");
                if (chunkKeys != null && !chunkKeys.isEmpty()) {
                    // 过滤掉音乐和图片 key，只删分块
                    var textChunks = new java.util.ArrayList<String>();
                    for (var key : chunkKeys) {
                        if (!key.contains(":music") && !key.contains(":images")) {
                            textChunks.add(key);
                        }
                    }
                    if (!textChunks.isEmpty()) {
                        redis.delete(textChunks);
                    }
                }
                cleaned++;
            }
        }
        log.info("RAG 存量清理完成：检查 {} 篇日记，清理 {} 篇超短日记的正文向量（保留音乐/图片）", allDiaries.size(), cleaned);
        return cleaned;
    }


    public record BatchIndexItem(long userId, long diaryId, String content) {
    }
}
