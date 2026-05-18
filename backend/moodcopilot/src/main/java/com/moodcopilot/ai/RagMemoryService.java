package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.UserProfileMemoryEntity;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.output.StatusOutput;
import io.lettuce.core.protocol.CommandArgs;
import io.lettuce.core.protocol.CommandType;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceRedisConnection;
import org.springframework.data.redis.connection.RedisConnection;
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
    private static final String INDEX_NAME = "idx:rag_embeddings";
    private static final String KEY_PREFIX = "rag:";

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
        try {
            redis.execute((RedisCallback<Object>) conn -> {
                CommandArgs<byte[], byte[]> cargs = new CommandArgs<>(ByteArrayCodec.INSTANCE)
                        .add(INDEX_NAME.getBytes(StandardCharsets.UTF_8))
                        .add("ON").add("HASH").add("PREFIX").add("1").add(KEY_PREFIX)
                        .add("SCHEMA")
                        .add("user_id").add("NUMERIC").add("SORTABLE")
                        .add("content").add("TEXT")
                        .add("embedding").add("VECTOR").add("HNSW").add("6")
                        .add("DIM").add(String.valueOf(embeddingDimension))
                        .add("TYPE").add("FLOAT32").add("DISTANCE_METRIC").add("COSINE")
                        .add("created_at").add("NUMERIC").add("SORTABLE");
                io.lettuce.core.api.sync.RedisCommands<byte[], byte[]> cmds =
                        ((LettuceRedisConnection) conn).getNativeConnection();
                cmds.dispatch(CommandType.valueOf("FT.CREATE"),
                        new StatusOutput<>(ByteArrayCodec.INSTANCE), cargs);
                return null;
            });
            log.info("RAG 向量索引已创建，dimension={}", embeddingDimension);
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
    @Async("aiExecutor")
    public void indexDiary(long userId, long diaryId, String content) {
        if (content == null || content.isBlank()) {
            log.debug("RAG 索引跳过：日记内容为空 diaryId={}", diaryId);
            return;
        }
        float[] vec = embed(content);
        if (vec == null) {
            log.warn("RAG 索引失败：embedding 生成失败 diaryId={}", diaryId);
            return;
        }
        storeEmbedding("diary:" + diaryId, userId, snippet(content, 800), vec);
        log.info("RAG 已索引日记 diaryId={} userId={} dim={}", diaryId, userId, vec.length);
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
                storeEmbedding("profile:" + userId + ":" + attrKey, userId, text, vec);
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
                userId, snippet(content, 350), vec);
        log.info("RAG 已索引聊天消息 userId={} convId={}", userId, conversationId);
    }

    private void storeEmbedding(String id, long userId, String content, float[] embedding) {
        String key = KEY_PREFIX + id;
        byte[] rawKey = key.getBytes(StandardCharsets.UTF_8);
        byte[] vecBytes = floatsToBytes(embedding);

        redis.execute((RedisCallback<Object>) conn -> {
            byte[] uid = "user_id".getBytes(StandardCharsets.UTF_8);
            byte[] cnt = "content".getBytes(StandardCharsets.UTF_8);
            byte[] emb = "embedding".getBytes(StandardCharsets.UTF_8);
            byte[] cat = "created_at".getBytes(StandardCharsets.UTF_8);

            conn.hashCommands().hSet(rawKey, uid, String.valueOf(userId).getBytes(StandardCharsets.UTF_8));
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
    public List<RagHit> search(long userId, String query, int topK) {
        float[] queryVec = embed(query);
        if (queryVec == null || queryVec.length == 0) {
            return List.of();
        }
        byte[] queryVector = floatsToBytes(queryVec);
        String filter = "@user_id:[" + userId + " " + userId + "]";
        String knn = "=> [KNN " + topK + " @embedding $vec AS _score]";
        String q = filter + " " + knn;

        try {
            List<RagHit> hits = new ArrayList<>();
            redis.execute((RedisCallback<Object>) conn -> {
                io.lettuce.core.api.sync.RedisCommands<byte[], byte[]> cmds =
                        ((LettuceRedisConnection) conn).getNativeConnection();
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
                        .add("SORTBY".getBytes(StandardCharsets.UTF_8))
                        .add("_score".getBytes(StandardCharsets.UTF_8))
                        .add("DIALECT".getBytes(StandardCharsets.UTF_8))
                        .add("2".getBytes(StandardCharsets.UTF_8));
                List<Object> raw = cmds.dispatch(
                        CommandType.valueOf("FT.SEARCH"),
                        new io.lettuce.core.output.NestedMultiOutput<>(ByteArrayCodec.INSTANCE),
                        cargs);
                if (raw != null) {
                    parseResults(raw, hits);
                }
                return null;
            });
            log.info("RAG 搜索完成 userId={} queryLen={} topK={} hits={}", userId,
                    query.length(), topK, hits.size());
            return hits;
        } catch (Exception e) {
            log.warn("RAG 搜索失败 userId={}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    public String buildRagContext(long userId, String query, int topK) {
        List<RagHit> hits = search(userId, query, topK);
        if (hits.isEmpty()) {
            log.debug("RAG 上下文为空 userId={} queryLen={}", userId, query.length());
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\n<rag_retrieved_context>\n");
        sb.append("以下是与用户当前问题语义相关的历史记录（由向量检索自动获取）：\n");
        for (int i = 0; i < hits.size(); i++) {
            RagHit hit = hits.get(i);
            sb.append("[").append(i + 1).append("] ").append(hit.content());
            if (hit.score() != null) {
                sb.append(" (相关度: ").append(String.format("%.2f", hit.score())).append(")");
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

    private String snippet(String content, int maxLen) {
        if (content.length() <= maxLen) {
            return content;
        }
        return content.substring(0, maxLen) + "...";
    }

    @SuppressWarnings("unchecked")
    private void parseResults(List<?> raw, List<RagHit> out) {
        if (raw.size() <= 1) {
            return;
        }
        for (int i = 1; i < raw.size(); i++) {
            Object item = raw.get(i);
            if (item instanceof String) {
                continue;
            }
            if (item instanceof List<?> fields) {
                String content = null;
                Double score = null;
                for (int j = 0; j + 1 < fields.size(); j += 2) {
                    String fname = String.valueOf(fields.get(j));
                    Object fval = fields.get(j + 1);
                    if ("content".equals(fname)) {
                        content = fval instanceof byte[] b ? new String(b, StandardCharsets.UTF_8) : String.valueOf(fval);
                    } else if ("_score".equals(fname)) {
                        try {
                            score = Double.parseDouble(String.valueOf(fval));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                if (content != null && !content.isBlank()) {
                    out.add(new RagHit(content, score));
                }
            }
        }
    }

    public record RagHit(String content, Double score) {
    }

    /**
     * 批量回填已有日记的向量索引（管理员触发）。
     * @param items 待索引的 (userId, diaryId, content) 列表
     * @return 成功索引的数量
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
                float[] vec = embed(item.content());
                if (vec != null) {
                    storeEmbedding("diary:" + item.diaryId(), item.userId(),
                            snippet(item.content(), 800), vec);
                    count++;
                }
                // 控制频率，避免 SiliconFlow 限流
                Thread.sleep(50);
            } catch (Exception e) {
                log.warn("批量向量化失败 diaryId={}: {}", item.diaryId(), e.getMessage());
            }
        }
        log.info("批量向量化完成：{}/{} 条", count, items.size());
        return count;
    }

    /**
     * 同步批量索引用户画像（供 admin reindex 使用）。
     */
    public int batchIndexProfiles(Map<Long, List<UserProfileMemoryEntity>> grouped) {
        if (embeddingApiKey.isBlank()) {
            return 0;
        }
        int count = 0;
        for (var entry : grouped.entrySet()) {
            // 清理旧格式单 blob key
            redis.delete(KEY_PREFIX + "profile:" + entry.getKey());
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            for (UserProfileMemoryEntity m : entry.getValue()) {
                String text = "用户长期画像 - " + m.getAttributeKey() + ": " + m.getAttributeValue();
                float[] vec = embed(text);
                if (vec != null) {
                    String attrKey = sanitizeKey(m.getAttributeKey());
                    storeEmbedding("profile:" + entry.getKey() + ":" + attrKey,
                            entry.getKey(), text, vec);
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
     */
    public void deleteDiaryEmbedding(long diaryId) {
        String key = KEY_PREFIX + "diary:" + diaryId;
        redis.delete(key);
        log.info("RAG 已删除日记向量 diaryId={}", diaryId);
    }

    public record BatchIndexItem(long userId, long diaryId, String content) {
    }
}
