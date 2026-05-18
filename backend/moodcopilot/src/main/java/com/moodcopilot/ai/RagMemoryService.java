package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.UserProfileMemoryEntity;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RagMemoryService {

    private static final Logger log = LoggerFactory.getLogger(RagMemoryService.class);
    private static final String INDEX_NAME = "idx:rag_v2";
    private static final String KEY_PREFIX = "rag:";
    public static final String SOURCE_DIARY = "diary";
    public static final String SOURCE_CHAT = "chat";
    public static final String SOURCE_PROFILE = "profile";

    private final String embeddingApiUrl;
    private final String embeddingApiKey;
    private final String embeddingModel;
    private final int embeddingDimension;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public RagMemoryService(
            @Value("${spring.ai.rag.embedding.api-url}") String embeddingApiUrl,
            @Value("${spring.ai.rag.embedding.api-key:}") String embeddingApiKey,
            @Value("${spring.ai.rag.embedding.model:BAAI/bge-m3}") String embeddingModel,
            @Value("${spring.ai.rag.embedding.dimension:1024}") int embeddingDimension,
            StringRedisTemplate redis,
            ObjectMapper objectMapper) {
        this.embeddingApiUrl = embeddingApiUrl;
        this.embeddingApiKey = embeddingApiKey == null ? "" : embeddingApiKey.trim();
        this.embeddingModel = embeddingModel == null || embeddingModel.isBlank() ? "BAAI/bge-m3" : embeddingModel.trim();
        this.embeddingDimension = embeddingDimension;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
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
            return embedding;
        } catch (Exception e) {
            log.warn("Embedding 生成失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 异步：将日记内容 embedding 后存入 Redis vector index。
     */
    private static final int CHUNK_SIZE = 400;
    private static final int CHUNK_OVERLAP = 50;

    @Async("aiExecutor")
    public void indexDiary(long userId, long diaryId, String content) {
        if (content == null || content.isBlank()) {
            log.debug("RAG 索引跳过：日记内容为空 diaryId={}", diaryId);
            return;
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
     * 异步：将聊天消息 embedding 后存入 Redis vector index。
     */
    @Async("aiExecutor")
    public void indexChatMessage(long userId, long conversationId, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        float[] vec = embed(content);
        if (vec == null) {
            return;
        }
        storeEmbedding("chat:" + conversationId + ":" + System.currentTimeMillis(),
                userId, SOURCE_CHAT, snippet(content, 350), vec);
        log.info("RAG 已索引聊天消息 userId={} convId={}", userId, conversationId);
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
     * 向量相似搜索：用 query embedding 在用户的历史内容中检索 topK 最相关片段。
     */
    @SuppressWarnings("unchecked")
    public List<RagHit> search(long userId, String query, int topK, String... sourceTypes) {
        float[] queryVec = embed(query);
        if (queryVec == null || queryVec.length == 0) {
            return List.of();
        }
        byte[] queryVector = floatsToBytes(queryVec);
        // 严格遵循 RediSearch Hybrid Query 语法：所有过滤条件必须在同一对括号内，且 => 前无空格
        String filter;
        if (sourceTypes.length > 0) {
            StringBuilder sb = new StringBuilder("(@user_id:[").append(userId).append(" ").append(userId).append("]");
            sb.append(" @source_type:{");
            for (int i = 0; i < sourceTypes.length; i++) {
                if (i > 0) sb.append("|");
                sb.append(sourceTypes[i]);
            }
            sb.append("})");
            filter = sb.toString();
        } else {
            filter = "(@user_id:[" + userId + " " + userId + "])";
        }

        String knn = "=>[KNN " + topK + " @embedding $vec AS _score]";
        String q = filter + knn; // 绝对不能有空格

        log.info("RAG 执行 FT.SEARCH, query: {}", q);

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
                // 无 SORTBY 下 Redis 返回顺序不一定严格升序，Java 侧按余弦距离升序保证 topScore 准确
                hits.sort(java.util.Comparator.comparing(RagHit::score, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
                if (!hits.isEmpty()) {
                    double topScore = hits.get(0).score() != null ? hits.get(0).score() : -1;
                    double avgScore = hits.stream().filter(h -> h.score() != null)
                            .mapToDouble(RagHit::score).average().orElse(-1);
                    log.info("RAG 搜索完成 userId={} queryLen={} hits={} topScore={} avgScore={}",
                            userId, query.length(), hits.size(),
                            String.format("%.3f", topScore), String.format("%.3f", avgScore));
                } else {
                    log.info("RAG 搜索完成 userId={} queryLen={} hits=0", userId, query.length());
                }
                return hits;
            });
        } catch (Exception e) {
            log.warn("RAG 搜索失败 userId={}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    public String buildRagContext(long userId, String query, int topK, String... sourceTypes) {
        List<RagHit> hits = search(userId, query, topK, sourceTypes);
        if (hits.isEmpty()) {
            log.debug("RAG 上下文为空 userId={} queryLen={}", userId, query.length());
            return "";
        }
        // 调试日志：打印所有召回文档的真实余弦距离，用于观测 BGE-M3 距离分布和后续阈值调优
        if (log.isDebugEnabled()) {
            StringBuilder scoreLog = new StringBuilder("RAG 召回距离明细 userId=").append(userId).append(" [");
            for (int i = 0; i < hits.size(); i++) {
                RagHit h = hits.get(i);
                if (i > 0) scoreLog.append(", ");
                scoreLog.append(h.sourceId() != null ? h.sourceId() : "?")
                        .append("=")
                        .append(h.score() != null ? String.format("%.4f", h.score()) : "null");
            }
            scoreLog.append("]");
            log.debug(scoreLog.toString());
        }
        // 过滤低分噪音 + 余弦距离→相似度转换（0=完全相同, 1=正交, 2=完全相反）
        List<RagHit> qualityHits = hits.stream()
                .filter(h -> h.score() != null && h.score() < 1.0)
                .toList();
        if (qualityHits.isEmpty()) {
            log.debug("RAG 命中全部低于阈值，已过滤 userId={}", userId);
            return "";
        }

        StringBuilder sb = new StringBuilder("\n\n<rag_retrieved_context>\n");
        sb.append("以下是与用户当前问题语义相关的历史记录（由向量检索自动获取）：\n");
        for (int i = 0; i < qualityHits.size(); i++) {
            RagHit hit = qualityHits.get(i);
            sb.append("[").append(i + 1).append("] ").append(hit.content());
            if (hit.score() != null) {
                double similarity = 1.0 - hit.score() / 2.0; // 余弦距离→相似度 0~1
                sb.append(" (相关度: ").append(String.format("%.2f", similarity)).append(")");
            }
            sb.append("\n");
        }
        sb.append("请结合以上检索到的历史信息进行分析。不要在回复中提及'向量检索'或暴露相关度分数。");
        sb.append("\n</rag_retrieved_context>");
        return sb.toString();
    }

    // ── 内部工具 ──

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
        // 打印出 Redis 真实的返回结构，方便排查
        log.info("RAG Redis 原始返回结构: {}", dump(raw));

        // 智能提取：兼容 RESP2 (数组) 和 RESP3 (Map) 格式
        extractHitsHeuristically(raw, out);
    }

    private String dump(Object obj) {
        if (obj instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                sb.append(dump(list.get(i)));
                if (i < list.size() - 1) sb.append(", ");
            }
            sb.append("]");
            return sb.toString();
        } else if (obj instanceof byte[] b) {
            return new String(b, StandardCharsets.UTF_8);
        } else {
            return String.valueOf(obj);
        }
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
                    out.add(new RagHit(content, score, extractSourceId(finalKey)));
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

    private String extractSourceId(String key) {
        if (key == null) return null;
        // rag:diary:123 → "diary:123" ； rag:chat:101:123456 → "chat:101" ； rag:profile:1113:性格 → "profile:1113"
        return key.substring(KEY_PREFIX.length());
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

    public record RagHit(String content, Double score, String sourceId) {
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

    public void deleteChatEmbeddings(long conversationId) {
        String pattern = KEY_PREFIX + "chat:" + conversationId + ":*";
        try {
            var keys = redis.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
                log.info("RAG 已删除聊天向量 convId={} count={}", conversationId, keys.size());
            }
        } catch (Exception e) {
            log.warn("RAG 删除聊天向量失败 convId={}: {}", conversationId, e.getMessage());
        }
    }

    public record BatchIndexItem(long userId, long diaryId, String content) {
    }
}
