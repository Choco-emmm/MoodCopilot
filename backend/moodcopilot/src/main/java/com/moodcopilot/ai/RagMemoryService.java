package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.UserProfileMemoryEntity;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
            byte[][] args = strs(INDEX_NAME,
                    "ON", "HASH", "PREFIX", "1", KEY_PREFIX,
                    "SCHEMA",
                    "user_id", "NUMERIC", "SORTABLE",
                    "content", "TEXT",
                    "embedding", "VECTOR", "HNSW", "6",
                    "DIM", String.valueOf(embeddingDimension),
                    "TYPE", "FLOAT32", "DISTANCE_METRIC", "COSINE",
                    "created_at", "NUMERIC", "SORTABLE");
            redis.execute((RedisCallback<Object>) conn -> {
                conn.execute("FT.CREATE", args);
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
    @Async
    public void indexDiary(long userId, long diaryId, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        float[] vec = embed(content);
        if (vec == null) {
            return;
        }
        storeEmbedding("diary:" + diaryId, userId, snippet(content, 350), vec);
    }

    /**
     * 将用户长期画像条目合并为文本后存入向量库（key: profile:{userId}）。
     */
    @Async
    public void indexUserProfile(long userId, List<UserProfileMemoryEntity> memories) {
        if (memories == null || memories.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder("用户长期画像：");
        for (UserProfileMemoryEntity m : memories) {
            sb.append(m.getAttributeKey()).append(" ").append(m.getAttributeValue()).append("，");
        }
        String text = sb.toString();
        float[] vec = embed(text);
        if (vec == null) {
            return;
        }
        // 覆盖写入，每个用户只保留最新一条画像记录
        storeEmbedding("profile:" + userId, userId, snippet(text, 500), vec);
    }

    /**
     * 异步：将聊天消息 embedding 后存入 Redis vector index。
     */
    @Async
    public void indexChatMessage(long userId, long conversationId, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        float[] vec = embed(content);
        if (vec == null) {
            return;
        }
        storeEmbedding("chat:" + conversationId, userId, snippet(content, 350), vec);
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
                byte[][] args = {
                        INDEX_NAME.getBytes(StandardCharsets.UTF_8),
                        q.getBytes(StandardCharsets.UTF_8),
                        "PARAMS".getBytes(StandardCharsets.UTF_8),
                        "2".getBytes(StandardCharsets.UTF_8),
                        "vec".getBytes(StandardCharsets.UTF_8),
                        queryVector,
                        "RETURN".getBytes(StandardCharsets.UTF_8),
                        "2".getBytes(StandardCharsets.UTF_8),
                        "content".getBytes(StandardCharsets.UTF_8),
                        "_score".getBytes(StandardCharsets.UTF_8),
                        "SORTBY".getBytes(StandardCharsets.UTF_8),
                        "_score".getBytes(StandardCharsets.UTF_8),
                        "DIALECT".getBytes(StandardCharsets.UTF_8),
                        "2".getBytes(StandardCharsets.UTF_8) };
                Object result = conn.execute("FT.SEARCH", args);
                if (result instanceof List<?> raw) {
                    parseResults(raw, hits);
                }
                return null;
            });
            return hits;
        } catch (Exception e) {
            log.debug("RAG 搜索失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 为 ChatService 推理路径生成格式化上下文。
     */
    public String buildRagContext(long userId, String query, int topK) {
        List<RagHit> hits = search(userId, query, topK);
        if (hits.isEmpty()) {
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

    private static byte[][] strs(String... strs) {
        byte[][] out = new byte[strs.length][];
        for (int i = 0; i < strs.length; i++) {
            out[i] = strs[i].getBytes(StandardCharsets.UTF_8);
        }
        return out;
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
                            snippet(item.content(), 350), vec);
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
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            StringBuilder sb = new StringBuilder("用户长期画像：");
            for (UserProfileMemoryEntity m : entry.getValue()) {
                sb.append(m.getAttributeKey()).append(" ").append(m.getAttributeValue()).append("，");
            }
            float[] vec = embed(sb.toString());
            if (vec != null) {
                storeEmbedding("profile:" + entry.getKey(), entry.getKey(), snippet(sb.toString(), 500), vec);
                count++;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("批量画像向量化完成：{}/{} 个用户", count, grouped.size());
        return count;
    }

    public record BatchIndexItem(long userId, long diaryId, String content) {
    }
}
