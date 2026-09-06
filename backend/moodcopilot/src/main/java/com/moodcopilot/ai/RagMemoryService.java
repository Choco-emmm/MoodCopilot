package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.DiaryEntity;
import com.moodcopilot.entity.DiaryKnowledgeGraphEntity;
import com.moodcopilot.entity.MusicMeta;
import com.moodcopilot.entity.UserProfileMemoryEntity;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.mapper.DiaryKnowledgeGraphMapper;
import com.moodcopilot.mapper.UserProfileMemoryMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.output.NestedMultiOutput;
import io.lettuce.core.output.StatusOutput;
import io.lettuce.core.protocol.CommandArgs;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.util.DigestUtils;

// TODO: 跨用户共鸣检索（RESONANCE）必须强制添加过滤条件，仅检索 Visibility=PUBLIC 的日记，严禁越权搜索他人私密日记。
@Service
public class RagMemoryService {

    private static final Logger log = LoggerFactory.getLogger(RagMemoryService.class);
    private static final String INDEX_NAME = "idx:rag_v2";
    private static final String KEY_PREFIX = "rag:";
    private static final String PROFILE_KEY_PREFIX = KEY_PREFIX + "profile:";
    private static final String PROFILE_LOCK_PREFIX = KEY_PREFIX + "profile-lock:";
    private static final String PROFILE_SCHEMA_KEY = KEY_PREFIX + "profile:index-schema-version";
    private static final String PROFILE_SCHEMA_VERSION = "2";
    private static final Duration PROFILE_LOCK_TTL = Duration.ofMinutes(5);
    private static final long PROFILE_LOCK_RENEW_INTERVAL_SECONDS = 30L;
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);
    private static final DefaultRedisScript<Long> RENEW_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end", Long.class);
    private static final DefaultRedisScript<Long> WRITE_SNAPSHOT_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) ~= ARGV[1] then return 0 end "
                    + "local current = redis.call('get', KEYS[2]) "
                    + "if current and tonumber(current) > tonumber(ARGV[2]) then return 2 end "
                    + "redis.call('set', KEYS[2], ARGV[2], 'EX', ARGV[3]) return 1", Long.class);
    private static final ScheduledExecutorService PROFILE_LOCK_RENEWER =
            Executors.newScheduledThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "rag-profile-lock-renewer");
                thread.setDaemon(true);
                return thread;
            });
    private static final int IMAGE_CONTEXT_MAX_CHARS = 4096;
    public static final String SOURCE_DIARY = "diary";
    public static final String SOURCE_PROFILE = "profile";
    public static final String SOURCE_MUSIC = "music";
    public static final String SOURCE_IMAGE = "image";
    public static final String SOURCE_GRAPH = "graph";
    public static final String SOURCE_CHAPTER = "chapter";

    private final String embeddingApiUrl;
    private final String embeddingApiKey;
    private final String embeddingModel;
    private final int embeddingDimension;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final DiaryMapper diaryMapper;
    private final UserProfileMemoryMapper profileMemoryMapper;
    private final DiaryKnowledgeGraphMapper graphMapper;
    private final ZoneId businessTimeZone;
    private final int embeddingConnectTimeoutMs;
    private final int embeddingReadTimeoutMs;
    private final int embeddingMaxRetries;
    private final int circuitFailureThreshold;
    private final long circuitOpenMillis;
    private final Cache<String, float[]> queryEmbeddingCache;
    private final ConcurrentHashMap<String, CompletableFuture<float[]>> embeddingInFlight = new ConcurrentHashMap<>();
    private final AtomicInteger transientEmbeddingFailures = new AtomicInteger();
    private volatile long circuitOpenedAt;
    private volatile boolean circuitProbeInFlight;
    public RagMemoryService(
            String embeddingApiUrl,
            String embeddingApiKey,
            String embeddingModel,
            int embeddingDimension,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            DiaryMapper diaryMapper) {
        this(embeddingApiUrl, embeddingApiKey, embeddingModel, embeddingDimension, redis, objectMapper, diaryMapper,
                null, null, "Asia/Shanghai", 3000, 15000, 2, 5, 30000, 1000, 600, true);
    }

    public RagMemoryService(
            String embeddingApiUrl,
            String embeddingApiKey,
            String embeddingModel,
            int embeddingDimension,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            DiaryMapper diaryMapper,
            String timeZoneId) {
        this(embeddingApiUrl, embeddingApiKey, embeddingModel, embeddingDimension, redis, objectMapper, diaryMapper,
                null, null, timeZoneId, 3000, 15000, 2, 5, 30000, 1000, 600, true);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RagMemoryService(
            @Value("${spring.ai.rag.embedding.api-url}") String embeddingApiUrl,
            @Value("${spring.ai.rag.embedding.api-key:}") String embeddingApiKey,
            @Value("${spring.ai.rag.embedding.model:BAAI/bge-m3}") String embeddingModel,
            @Value("${spring.ai.rag.embedding.dimension:1024}") int embeddingDimension,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            DiaryMapper diaryMapper,
            UserProfileMemoryMapper profileMemoryMapper,
            DiaryKnowledgeGraphMapper graphMapper,
            @Value("${moodcopilot.time-zone:Asia/Shanghai}") String timeZoneId,
            @Value("${moodcopilot.rag.embedding.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${moodcopilot.rag.embedding.read-timeout-ms:15000}") int readTimeoutMs,
            @Value("${moodcopilot.rag.embedding.max-retries:2}") int maxRetries,
            @Value("${moodcopilot.rag.embedding.circuit-failure-threshold:5}") int failureThreshold,
            @Value("${moodcopilot.rag.embedding.circuit-open-seconds:30}") long circuitOpenSeconds,
            @Value("${moodcopilot.rag.embedding.query-cache-max-size:1000}") long cacheMaxSize,
            @Value("${moodcopilot.rag.embedding.query-cache-ttl-seconds:600}") long cacheTtlSeconds) {
        this(embeddingApiUrl, embeddingApiKey, embeddingModel, embeddingDimension, redis, objectMapper, diaryMapper,
                profileMemoryMapper, graphMapper, timeZoneId, connectTimeoutMs, readTimeoutMs, maxRetries, failureThreshold,
                circuitOpenSeconds * 1000L, cacheMaxSize, cacheTtlSeconds, true);
    }

    private RagMemoryService(
            String embeddingApiUrl,
            String embeddingApiKey,
            String embeddingModel,
            int embeddingDimension,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            DiaryMapper diaryMapper,
            UserProfileMemoryMapper profileMemoryMapper,
            DiaryKnowledgeGraphMapper graphMapper,
            String timeZoneId,
            int connectTimeoutMs,
            int readTimeoutMs,
            int maxRetries,
            int failureThreshold,
            long circuitOpenMillis,
            long cacheMaxSize,
            long cacheTtlSeconds,
            boolean initializationMarker) {
        this.embeddingApiUrl = embeddingApiUrl;
        this.embeddingApiKey = embeddingApiKey == null ? "" : embeddingApiKey.trim();
        this.embeddingModel = embeddingModel == null || embeddingModel.isBlank() ? "BAAI/bge-m3" : embeddingModel.trim();
        this.embeddingDimension = embeddingDimension;
        this.redis = redis;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.max(100, connectTimeoutMs));
        requestFactory.setReadTimeout(Math.max(100, readTimeoutMs));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.diaryMapper = diaryMapper;
        this.profileMemoryMapper = profileMemoryMapper;
        this.graphMapper = graphMapper;
        this.businessTimeZone = parseZoneId(timeZoneId);
        this.embeddingConnectTimeoutMs = Math.max(100, connectTimeoutMs);
        this.embeddingReadTimeoutMs = Math.max(100, readTimeoutMs);
        this.embeddingMaxRetries = Math.max(0, Math.min(maxRetries, 5));
        this.circuitFailureThreshold = Math.max(1, failureThreshold);
        this.circuitOpenMillis = Math.max(1000L, circuitOpenMillis);
        this.queryEmbeddingCache = Caffeine.newBuilder()
                .maximumSize(Math.max(1, cacheMaxSize))
                .expireAfterWrite(Duration.ofSeconds(Math.max(1, cacheTtlSeconds)))
                .build();
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
    public float[] embed(String text) {
        if (embeddingApiKey.isBlank()) {
            log.warn("Embedding 未配置 API Key，跳过向量生成");
            return null;
        }
        String normalized = RagQueryBuilder.embeddingText(text);
        if (!RagQueryBuilder.meaningful(normalized)) {
            return null;
        }

        String cacheKey = embeddingModel + ":" + DigestUtils.md5DigestAsHex(
                normalized.getBytes(StandardCharsets.UTF_8));
        float[] cached = queryEmbeddingCache.getIfPresent(cacheKey);
        if (cached != null) return cached.clone();

        CompletableFuture<float[]> created = new CompletableFuture<>();
        CompletableFuture<float[]> inFlight = embeddingInFlight.putIfAbsent(cacheKey, created);
        if (inFlight != null) {
            try {
                float[] result = inFlight.join();
                return result == null ? null : result.clone();
            } catch (CompletionException e) {
                return null;
            }
        }

        try {
            float[] result = embedUncached(normalized, cacheKey);
            created.complete(result);
            return result == null ? null : result.clone();
        } catch (RuntimeException e) {
            created.complete(null);
            throw e;
        } finally {
            embeddingInFlight.remove(cacheKey, created);
        }
    }

    @SuppressWarnings("unchecked")
    private float[] embedUncached(String normalized, String cacheKey) {
        if (!tryEnterEmbeddingCircuit()) return null;

        for (int attempt = 1; attempt <= embeddingMaxRetries + 1; attempt++) {
            long requestStartedAt = AiCallTiming.start();
            try {
                String response = restClient.post()
                        .uri(embeddingApiUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + embeddingApiKey)
                        .body(Map.of("model", embeddingModel, "input", normalized, "encoding_format", "float"))
                        .retrieve()
                        .body(String.class);
                AiCallTiming.completed(log, "EMBEDDING", embeddingModel, requestStartedAt, "HTTP_SUCCESS",
                        normalized.length(), response == null ? 0 : response.length());

                if (response == null || response.isBlank()) {
                    recordTransientEmbeddingFailure();
                    log.warn("Embedding 响应为空，attempt={}", attempt);
                    return null;
                }
                Map<String, Object> parsed = objectMapper.readValue(response, Map.class);
                List<Map<String, Object>> data = (List<Map<String, Object>>) parsed.get("data");
                if (data == null || data.isEmpty()) {
                    recordTransientEmbeddingFailure();
                    log.warn("Embedding 响应 data 为空，attempt={}", attempt);
                    return null;
                }
                List<Number> raw = (List<Number>) data.get(0).get("embedding");
                if (raw == null) {
                    recordTransientEmbeddingFailure();
                    log.warn("Embedding 响应缺少向量，attempt={}", attempt);
                    return null;
                }
                float[] embedding = new float[raw.size()];
                for (int i = 0; i < raw.size(); i++) embedding[i] = raw.get(i).floatValue();
                boolean hasInvalidValue = false;
                for (float value : embedding) {
                    if (!Float.isFinite(value)) {
                        hasInvalidValue = true;
                        break;
                    }
                }
                if (embedding.length != embeddingDimension || hasInvalidValue) {
                    recordTransientEmbeddingFailure();
                    log.warn("Embedding 响应向量无效，expectedDimension={} actualDimension={}",
                            embeddingDimension, embedding.length);
                    return null;
                }
                recordEmbeddingSuccess();
                queryEmbeddingCache.put(cacheKey, embedding.clone());
                log.info("Embedding 生成成功，dimension={}", embedding.length);
                return embedding;
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                AiCallTiming.failed(log, "EMBEDDING", embeddingModel, requestStartedAt, e, normalized.length());
                int status = e.getStatusCode().value();
                if ((status == 408 || status == 429) && attempt <= embeddingMaxRetries) {
                    recordTransientEmbeddingFailure();
                    sleepBeforeRetry(attempt);
                    continue;
                }
                synchronized (this) { circuitProbeInFlight = false; }
                log.error("Embedding API 客户端错误 status={}，不再重试", status);
                return null;
            } catch (org.springframework.web.client.HttpServerErrorException e) {
                AiCallTiming.failed(log, "EMBEDDING", embeddingModel, requestStartedAt, e, normalized.length());
                recordTransientEmbeddingFailure();
                if (attempt <= embeddingMaxRetries) {
                    sleepBeforeRetry(attempt);
                    continue;
                }
                log.error("Embedding API 服务端错误，重试耗尽 status={}", e.getStatusCode().value());
                return null;
            } catch (Exception e) {
                AiCallTiming.failed(log, "EMBEDDING", embeddingModel, requestStartedAt, e, normalized.length());
                recordTransientEmbeddingFailure();
                if (attempt <= embeddingMaxRetries) {
                    sleepBeforeRetry(attempt);
                    continue;
                }
                log.error("Embedding 网络异常，重试耗尽 errorType={}", e.getClass().getSimpleName());
                return null;
            }
        }
        return null;
    }

    private boolean tryEnterEmbeddingCircuit() {
        long openedAt = circuitOpenedAt;
        if (openedAt == 0L) return true;
        if (System.currentTimeMillis() - openedAt < circuitOpenMillis) return false;
        synchronized (this) {
            if (circuitOpenedAt == 0L) return true;
            if (System.currentTimeMillis() - circuitOpenedAt < circuitOpenMillis) return false;
            if (circuitProbeInFlight) return false;
            circuitProbeInFlight = true;
            return true;
        }
    }

    private void recordEmbeddingSuccess() {
        transientEmbeddingFailures.set(0);
        synchronized (this) {
            circuitOpenedAt = 0L;
            circuitProbeInFlight = false;
        }
    }

    private void recordTransientEmbeddingFailure() {
        int failures = transientEmbeddingFailures.incrementAndGet();
        synchronized (this) {
            circuitProbeInFlight = false;
            if (failures >= circuitFailureThreshold) {
                circuitOpenedAt = System.currentTimeMillis();
                log.warn("Embedding 熔断器打开，cooldownMs={} failures={}", circuitOpenMillis, failures);
            }
        }
    }

    private void sleepBeforeRetry(int attempt) {
        long delayMs = Math.min(4000L, (1L << Math.min(attempt, 4)) * 250L
                + (long) (Math.random() * 250L));
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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

    public void deleteKeys(List<String> keys) {
        if (keys == null || keys.isEmpty()) return;
        try {
            redis.delete(keys);
        } catch (Exception e) {
            log.debug("Redis cache invalidation failed: {}", e.getMessage());
        }
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
     * 将用户长期画像按 memoryId 逐个索引到向量库（key: profile:{userId}:{memoryId}）。
     * 每个属性独立存储，便于语义检索时精准匹配。
     */
    public void indexUserProfile(long userId, List<UserProfileMemoryEntity> memories) {
        indexUserProfile(userId, memories, System.currentTimeMillis());
    }

    public void indexLifeChapter(long userId, long chapterId, String content, String updatedAt) {
        if (content == null || content.isBlank()) return;
        String key = "chapter:" + userId + ":" + chapterId;
        String fingerprint = DigestUtils.md5DigestAsHex((chapterId + "|" + content).getBytes(StandardCharsets.UTF_8));
        if (fingerprint.equals(readHashValue(KEY_PREFIX + key, "content_hash"))) {
            log.info("RAG 阶段摘要未变化，跳过向量化 userId={} chapterId={}", userId, chapterId);
            return;
        }
        float[] vector = embed(content);
        if (vector == null || vector.length == 0) throw new IllegalStateException("阶段摘要 embedding 为空");
        storeEmbedding(key, userId, SOURCE_CHAPTER, content, vector,
                Map.of("chapter_id", String.valueOf(chapterId), "content_hash", fingerprint,
                        "chapter_updated_at", updatedAt == null ? "" : updatedAt));
        log.info("RAG 阶段摘要已索引 userId={} chapterId={}", userId, chapterId);
    }

    public void deleteLifeChapter(long userId, long chapterId) {
        redis.delete(KEY_PREFIX + "chapter:" + userId + ":" + chapterId);
    }

    public void indexUserProfile(long userId, List<UserProfileMemoryEntity> memories, long snapshotAt) {
        if (memories == null) {
            // A missing snapshot is not an empty profile. Failing closed prevents a
            // transient database read from deleting the user's existing vector index.
            throw new IllegalArgumentException("画像 RAG 索引缺少正式记忆快照");
        }
        String lockKey = PROFILE_LOCK_PREFIX + userId;
        String lockToken = UUID.randomUUID().toString();
        if (!acquireLock(lockKey, lockToken)) {
            log.warn("RAG 画像增量索引失败：获取用户锁超时 userId={}，等待任务重试", userId);
            throw new IllegalStateException("画像 RAG 锁竞争超时，等待任务重试");
        }
        AtomicBoolean ownershipLost = new AtomicBoolean(false);
        ScheduledFuture<?> renewal = startLockRenewal(lockKey, lockToken, ownershipLost);
        try {
            if (!isLockOwned(lockKey, lockToken)) {
                ownershipLost.set(true);
                throw new IllegalStateException("画像 RAG 锁已失效，等待任务重试");
            }
            String storedSnapshot = redis.opsForValue().get(PROFILE_KEY_PREFIX + userId + ":snapshot");
            if (storedSnapshot != null && parseLong(storedSnapshot) > snapshotAt) {
                log.info("RAG 画像增量索引跳过：已有更新快照 userId={} incomingSnapshot={} storedSnapshot={}",
                        userId, snapshotAt, storedSnapshot);
                return;
            }
            if (!indexUserProfileLocked(userId, memories, snapshotAt, lockKey, lockToken, ownershipLost)) {
                throw new IllegalStateException("画像 RAG 向量生成失败，等待任务重试");
            }
        } finally {
            renewal.cancel(false);
            releaseLock(lockKey, lockToken);
        }
    }

    /**
     * 用户主动编辑画像时使用异步包装；RabbitMQ 任务必须调用同步的 indexUserProfile，
     * 让 embedding 和 Redis 写入完成后再确认消息。
     */
    @Async("aiExecutor")
    public void indexUserProfileAsync(long userId, List<UserProfileMemoryEntity> memories) {
        indexUserProfile(userId, memories);
    }

    private boolean indexUserProfileLocked(long userId, List<UserProfileMemoryEntity> memories, long snapshotAt,
                                           String lockKey, String lockToken, AtomicBoolean ownershipLost) {
        List<String> existingKeys = listProfileKeys(userId);
        Set<String> desiredKeys = new java.util.HashSet<>();
        java.time.LocalDate today = java.time.LocalDate.now(businessTimeZone);
        int indexed = 0;
        int skipped = 0;
        boolean complete = true;
        if (memories != null) {
            for (UserProfileMemoryEntity memory : memories) {
                if (memory == null || memory.getId() == null) {
                    continue;
                }
                if (!isEligibleProfileMemory(memory, userId, today)) {
                    log.debug("RAG 画像索引跳过无效或非正式记忆 userId={} memoryId={}", userId, memory.getId());
                    continue;
                }
                if (!ensureLockOwnership(lockKey, lockToken, ownershipLost)) {
                    complete = false;
                    break;
                }
                String key = profileKey(userId, memory.getId());
                desiredKeys.add(key);
                String text = "用户长期画像 - " + memory.getAttributeKey() + ": " + memory.getAttributeValue();
                String fingerprint = profileFingerprint(memory);
                if (!needsProfileReindex(fingerprint, readHashValue(key, "content_hash"))) {
                    skipped++;
                    continue;
                }
                float[] vector = embed(text);
                if (vector == null) {
                    log.warn("RAG 画像增量索引失败：embedding 为空 userId={} memoryId={}", userId, memory.getId());
                    complete = false;
                    continue;
                }
                if (!ensureLockOwnership(lockKey, lockToken, ownershipLost)) {
                    complete = false;
                    break;
                }
                storeEmbedding("profile:" + userId + ":" + memory.getId(), userId, SOURCE_PROFILE, text, vector,
                        Map.of("memory_id", String.valueOf(memory.getId()), "content_hash", fingerprint,
                                "memory_updated_at", String.valueOf(memoryTimestamp(memory))));
                indexed++;
            }
        }
        int deleted = 0;
        if (complete && ensureLockOwnership(lockKey, lockToken, ownershipLost)) {
            for (String key : existingKeys) {
                if (!key.equals(PROFILE_KEY_PREFIX + userId + ":snapshot") && !desiredKeys.contains(key)) {
                    redis.delete(key);
                    deleted++;
                }
            }
        } else {
            complete = false;
        }
        if (complete) {
            if (!writeSnapshotIfNewer(userId, snapshotAt, lockKey, lockToken)) {
                complete = false;
            }
        }
        if (complete) {
            log.info("RAG 画像增量索引完成 userId={} indexed={} skipped={} deleted={} total={}", userId, indexed,
                    skipped, deleted, desiredKeys.size());
        } else {
            log.warn("RAG 画像增量索引未完成，不更新快照，等待任务重试 userId={} indexed={} skipped={} deleted={} total={}",
                    userId, indexed, skipped, deleted, desiredKeys.size());
        }
        return complete;
    }

    private boolean isEligibleProfileMemory(UserProfileMemoryEntity memory, long userId, java.time.LocalDate today) {
        if (memory == null || memory.getId() == null || !Long.valueOf(userId).equals(memory.getUserId())) return false;
        if (!"active".equalsIgnoreCase(memory.getStatus())) return false;
        if ("short_term_state".equalsIgnoreCase(memory.getMemoryType())) return false;
        if (SensitiveDataDetector.containsSensitiveData(memory.getAttributeKey())
                || SensitiveDataDetector.containsSensitiveData(memory.getAttributeValue())) return false;
        return (memory.getValidFrom() == null || !today.isBefore(memory.getValidFrom()))
                && (memory.getValidUntil() == null || !today.isAfter(memory.getValidUntil()));
    }

    private List<String> listProfileKeys(long userId) {
        return scanKeys(PROFILE_KEY_PREFIX + userId + ":*");
    }

    private List<String> scanKeys(String pattern) {
        try {
            return redis.execute((RedisCallback<List<String>>) connection -> {
                List<String> keys = new ArrayList<>();
                ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
                try (Cursor<byte[]> cursor = connection.scan(options)) {
                    while (cursor.hasNext()) {
                        keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                    }
                }
                return keys;
            });
        } catch (Exception e) {
            log.warn("RAG 键扫描失败 pattern={}: {}", pattern, e.getMessage());
            throw new IllegalStateException("RAG 键扫描失败", e);
        }
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
        storeEmbedding(id, userId, sourceType, content, embedding, Map.of());
    }

    private void storeEmbedding(String id, long userId, String sourceType, String content, float[] embedding,
            Map<String, String> metadata) {
        String safeContent = SensitiveDataDetector.redact(content);
        if (safeContent == null || safeContent.isBlank()) return;
        String key = KEY_PREFIX + id;
        byte[] rawKey = key.getBytes(StandardCharsets.UTF_8);
        byte[] vecBytes = floatsToBytes(embedding);

        redis.execute((RedisCallback<Object>) conn -> {
            byte[] uid = "user_id".getBytes(StandardCharsets.UTF_8);
            byte[] st = "source_type".getBytes(StandardCharsets.UTF_8);
            byte[] cnt = "content".getBytes(StandardCharsets.UTF_8);
            byte[] emb = "embedding".getBytes(StandardCharsets.UTF_8);
            byte[] cat = "created_at".getBytes(StandardCharsets.UTF_8);

            conn.multi();
            try {
                conn.hashCommands().hSet(rawKey, uid, String.valueOf(userId).getBytes(StandardCharsets.UTF_8));
                conn.hashCommands().hSet(rawKey, st, sourceType.getBytes(StandardCharsets.UTF_8));
                conn.hashCommands().hSet(rawKey, cnt, safeContent.getBytes(StandardCharsets.UTF_8));
                conn.hashCommands().hSet(rawKey, emb, vecBytes);
                conn.hashCommands().hSet(rawKey, cat,
                        String.valueOf(System.currentTimeMillis() / 1000).getBytes(StandardCharsets.UTF_8));
                for (Map.Entry<String, String> entry : metadata.entrySet()) {
                    conn.hashCommands().hSet(rawKey, entry.getKey().getBytes(StandardCharsets.UTF_8),
                            entry.getValue().getBytes(StandardCharsets.UTF_8));
                }
                conn.expire(rawKey, 90 * 86400);
                conn.exec();
            } catch (RuntimeException e) {
                try {
                    conn.discard();
                } catch (Exception discardError) {
                    log.debug("RAG Redis 事务回滚失败 key={}: {}", key, discardError.getMessage());
                }
                throw e;
            }
            return null;
        });
    }

    String profileKey(long userId, long memoryId) {
        return PROFILE_KEY_PREFIX + userId + ":" + memoryId;
    }

    static boolean needsProfileReindex(String currentHash, String indexedHash) {
        return indexedHash == null || !indexedHash.equals(currentHash);
    }

    String profileFingerprint(UserProfileMemoryEntity memory) {
        String value = String.join("\u001f",
                String.valueOf(memory.getId()),
                String.valueOf(memory.getAttributeKey()),
                String.valueOf(memory.getAttributeValue()),
                String.valueOf(memory.getMemoryType()),
                String.valueOf(Boolean.TRUE.equals(memory.getIsCore())),
                String.valueOf(memory.getValidFrom()),
                String.valueOf(memory.getValidUntil()),
                String.valueOf(memory.getStatus()));
        return DigestUtils.md5DigestAsHex(value.getBytes(StandardCharsets.UTF_8));
    }

    private long memoryTimestamp(UserProfileMemoryEntity memory) {
        if (memory.getUpdatedAt() != null) return memory.getUpdatedAt().atZone(businessTimeZone).toInstant().toEpochMilli();
        if (memory.getUpdateTime() != null) return memory.getUpdateTime().atZone(businessTimeZone).toInstant().toEpochMilli();
        return 0L;
    }

    private String readHashValue(String key, String field) {
        byte[] rawKey = key.getBytes(StandardCharsets.UTF_8);
        byte[] rawField = field.getBytes(StandardCharsets.UTF_8);
        try {
            byte[] value = redis.execute((RedisCallback<byte[]>) connection -> connection.hashCommands().hGet(rawKey, rawField));
            return value == null ? null : new String(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.debug("RAG 画像元数据读取失败 key={} field={}: {}", key, field, e.getMessage());
            return null;
        }
    }

    private boolean acquireLock(String key, String token) {
        for (int attempt = 0; attempt < 50; attempt++) {
            if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, token, PROFILE_LOCK_TTL))) {
                return true;
            }
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private ScheduledFuture<?> startLockRenewal(String key, String token, AtomicBoolean ownershipLost) {
        return PROFILE_LOCK_RENEWER.scheduleAtFixedRate(() -> {
            try {
                if (!renewLock(key, token)) {
                    ownershipLost.set(true);
                    log.warn("RAG 画像锁续租失败，当前索引将停止写入 key={}", key);
                }
            } catch (Exception e) {
                ownershipLost.set(true);
                log.warn("RAG 画像锁续租异常，当前索引将停止写入 key={} error={}", key, e.getMessage());
            }
        }, PROFILE_LOCK_RENEW_INTERVAL_SECONDS, PROFILE_LOCK_RENEW_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private boolean renewLock(String key, String token) {
        Long renewed = redis.execute(RENEW_LOCK_SCRIPT, List.of(key), token,
                String.valueOf(PROFILE_LOCK_TTL.toMillis()));
        return Long.valueOf(1L).equals(renewed);
    }

    private boolean isLockOwned(String key, String token) {
        return token.equals(redis.opsForValue().get(key));
    }

    private boolean ensureLockOwnership(String key, String token, AtomicBoolean ownershipLost) {
        if (ownershipLost.get()) {
            return false;
        }
        if (!isLockOwned(key, token)) {
            ownershipLost.set(true);
            log.warn("RAG 画像锁已失效，停止当前索引写入 key={}", key);
            return false;
        }
        return true;
    }

    private boolean writeSnapshotIfNewer(long userId, long snapshotAt, String lockKey, String lockToken) {
        String snapshotKey = PROFILE_KEY_PREFIX + userId + ":snapshot";
        Long written = redis.execute(WRITE_SNAPSHOT_SCRIPT, List.of(lockKey, snapshotKey), lockToken,
                String.valueOf(snapshotAt), String.valueOf(Duration.ofDays(90).toSeconds()));
        // 2 means another completed index already published a newer snapshot; this run is safely obsolete.
        return Long.valueOf(1L).equals(written) || Long.valueOf(2L).equals(written);
    }

    private void releaseLock(String key, String token) {
        try {
            redis.execute(RELEASE_LOCK_SCRIPT, List.of(key), token);
        } catch (Exception e) {
            log.warn("RAG 画像锁释放失败 key={}: {}", key, e.getMessage());
        }
    }

    private ZoneId parseZoneId(String value) {
        try {
            return value == null || value.isBlank() ? ZoneId.of("Asia/Shanghai") : ZoneId.of(value.trim());
        } catch (RuntimeException e) {
            log.warn("RAG 业务时区配置无效，使用 Asia/Shanghai: {}", value);
            return ZoneId.of("Asia/Shanghai");
        }
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
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
        return searchDetailed(userId, query, topK, timeRange, sourceTypes).hits();
    }

    /**
     * Structured search entry point. A vector query that completes with zero
     * results returns VECTOR, while only an unavailable vector/Redis path returns
     * LEXICAL_FALLBACK. This prevents an empty but healthy vector index from being
     * mistaken for a degraded search.
     */
    @SuppressWarnings("unchecked")
    public RagSearchResult searchDetailed(long userId, String query, int topK,
            TimeExpressionParser.TimeRange timeRange, String... sourceTypes) {
        String normalizedQuery = RagQueryBuilder.embeddingText(query);
        String[] safeSourceTypes = sanitizeSourceTypes(sourceTypes);
        int safeTopK = Math.max(1, Math.min(topK, 50));
        if (userId <= 0 || (sourceTypes != null && sourceTypes.length > 0 && safeSourceTypes.length == 0)) {
            log.warn("RAG 检索参数无效 userId={} sourceTypes={}", userId, Arrays.toString(sourceTypes));
            return RagSearchResult.empty();
        }
        if (!RagQueryBuilder.meaningful(normalizedQuery)) {
            log.info("RAG 检索跳过：查询为空 userId={}", userId);
            return RagSearchResult.empty();
        }
        float[] queryVec;
        try {
            queryVec = embed(normalizedQuery);
        } catch (RuntimeException embeddingError) {
            // Embedding is an enhancement. An unexpected client/cache failure must
            // still leave the user-isolated lexical path available.
            log.warn("RAG Embedding 调用异常，进入关键词兜底 userId={} errorType={}", userId,
                    embeddingError.getClass().getSimpleName());
            return RagSearchResult.lexicalFallback(
                    lexicalFallback(userId, normalizedQuery, safeTopK, timeRange, safeSourceTypes));
        }
        if (queryVec == null || queryVec.length == 0) {
            log.warn("RAG 向量不可用，进入关键词兜底 userId={} sourceTypes={}", userId, Arrays.toString(safeSourceTypes));
            return RagSearchResult.lexicalFallback(
                    lexicalFallback(userId, normalizedQuery, safeTopK, timeRange, safeSourceTypes));
        }
        byte[] queryVector = floatsToBytes(queryVec);

        log.info("RAG 开始搜索 userId={} queryLen={} topK={} timeRange=[from={} to={}] sourceTypes={}",
                userId, normalizedQuery.length(), safeTopK,
                timeRange != null ? formatEpoch(timeRange.fromTimestamp()) : "无",
                timeRange != null ? formatEpoch(timeRange.toTimestamp()) : "无",
                Arrays.toString(safeSourceTypes));

        // 严格遵循 RediSearch Hybrid Query 语法：所有过滤条件必须在同一对括号内，且 => 前无空格
        StringBuilder fb = new StringBuilder("(@user_id:[").append(userId).append(" ").append(userId).append("]");
        if (safeSourceTypes.length > 0) {
            fb.append(" @source_type:{");
            for (int i = 0; i < safeSourceTypes.length; i++) {
                if (i > 0) fb.append("|");
                fb.append(safeSourceTypes[i]);
            }
            fb.append("}");
        }
        if (timeRange != null) {
            fb.append(" @created_at:[").append(timeRange.fromTimestamp())
                    .append(" ").append(timeRange.toTimestamp()).append("]");
        }
        fb.append(")");
        String filter = fb.toString();

        String knn = "=>[KNN " + safeTopK + " @embedding $vec AS _score]";
        String q = filter + knn;

        log.info("RAG 搜索 query string: {}", q);

        try {
            List<RagHit> vectorHits = null;
            for (int redisAttempt = 1; redisAttempt <= 2; redisAttempt++) {
                try {
                    vectorHits = redis.execute((RedisCallback<List<RagHit>>) conn -> {
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
                    Long rawCount = parseResultCount(raw.get(0));
                    if (rawCount != null) {
                        log.info("RAG Redis底层原始命中数: {}", rawCount);
                    }
                    parseResults(raw, hits);
                }
                hits.sort(java.util.Comparator.comparing(RagHit::score, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
                int rawHits = hits.size();
                List<RagHit> qualityHits = filterQualityHits(hits);
                log.info("RAG 搜索完成 userId={} totalHits={} qualityHits={}",
                        userId, rawHits, qualityHits.size());

                if (!qualityHits.isEmpty()) {
                    double topDistance = qualityHits.get(0).score() != null ? qualityHits.get(0).score() : -1;
                    double avgDistance = qualityHits.stream().filter(h -> h.score() != null)
                            .mapToDouble(RagHit::score).average().orElse(-1);
                    log.info("RAG qualityHits topDistance={} avgDistance={}",
                            String.format("%.3f", topDistance), String.format("%.3f", avgDistance));
                }
                return qualityHits;
                    });
                    break;
                } catch (RuntimeException redisError) {
                    if (redisAttempt == 2) throw redisError;
                    log.warn("RAG Redis 检索暂时失败，将重试 attempt={} errorType={}", redisAttempt,
                            redisError.getClass().getSimpleName());
                    sleepBeforeRetry(redisAttempt);
                }
            }
            if (vectorHits != null) return RagSearchResult.vector(vectorHits);
            log.warn("RAG Redis 返回空结果，进入关键词兜底 userId={}", userId);
            return RagSearchResult.lexicalFallback(
                    lexicalFallback(userId, normalizedQuery, safeTopK, timeRange, safeSourceTypes));
        } catch (Exception e) {
            log.warn("RAG 搜索失败，进入关键词兜底 userId={} errorType={}", userId, e.getClass().getSimpleName());
            return RagSearchResult.lexicalFallback(
                    lexicalFallback(userId, normalizedQuery, safeTopK, timeRange, safeSourceTypes));
        }
    }

    /**
     * RediSearch COSINE KNN 返回 distance，而不是相似度：0 表示完全相同，数值越小表示越相似。
     */
    static List<RagHit> filterQualityHits(List<RagHit> hits) {
        // 保留 0 距离命中，只过滤超过质量上限的远距离结果。
        List<RagHit> validHits = hits.stream()
                .filter(h -> h.score() != null && h.score() >= 0.0 && h.score() <= 0.55)
                .sorted(java.util.Comparator.comparing(RagHit::score))
                .toList();

        if (validHits.isEmpty()) {
            return List.of();
        }

        // 动态阈值排除与 Top 1 差距过大的噪音，同时保持 0.55 的硬上限。
        double topDistance = validHits.get(0).score();
        double threshold = Math.min(0.55, Math.max(topDistance * 3.0, topDistance + 0.10));
        return validHits.stream()
                .filter(h -> h.score() <= threshold)
                .toList();
    }

    private List<RagHit> lexicalFallback(long userId, String query, int topK,
            TimeExpressionParser.TimeRange timeRange, String... sourceTypes) {
        try {
            Set<String> types = new java.util.HashSet<>(Arrays.asList(sanitizeSourceTypes(sourceTypes)));
            List<String> terms = RagQueryBuilder.lexicalTerms(query);
            if (terms.isEmpty()) return List.of();
            int resultLimit = Math.max(1, Math.min(topK, 50));
            List<RagHit> hits = new ArrayList<>();
            // Music/image vectors are separate Redis documents. A SQL diary fallback
            // cannot distinguish those documents, so never return ordinary diary rows
            // when the caller requested only one of those source types.
            boolean canFallbackToDiary = types.isEmpty() || types.contains(SOURCE_DIARY);
            if (diaryMapper != null && canFallbackToDiary) {
                LambdaQueryWrapper<DiaryEntity> wrapper = new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getAuthorUserId, userId)
                        .eq(DiaryEntity::getIsDeleted, false)
                        .orderByDesc(DiaryEntity::getCreatedAt);
                wrapper.and(w -> {
                    w.like(DiaryEntity::getContent, terms.get(0));
                    for (int i = 1; i < terms.size(); i++) {
                        w.or().like(DiaryEntity::getContent, terms.get(i));
                    }
                });
                if (timeRange != null) {
                    java.time.LocalDateTime from = timeRange.fromTimestamp() <= 0
                            ? java.time.LocalDateTime.MIN
                            : java.time.Instant.ofEpochSecond(timeRange.fromTimestamp())
                                    .atZone(businessTimeZone).toLocalDateTime();
                    java.time.LocalDateTime to = timeRange.toTimestamp() == Long.MAX_VALUE
                            ? java.time.LocalDateTime.MAX
                            : java.time.Instant.ofEpochSecond(timeRange.toTimestamp())
                                    .atZone(businessTimeZone).toLocalDateTime();
                    wrapper.ge(DiaryEntity::getCreatedAt, from).le(DiaryEntity::getCreatedAt, to);
                }
                for (DiaryEntity diary : diaryMapper.selectPage(Page.of(1, resultLimit), wrapper).getRecords()) {
                    hits.add(new RagHit(diary.getContent(), null, "diary:" + diary.getId(), diary.getId(), SOURCE_DIARY));
                }
            }
            if ((types.isEmpty() || types.contains(SOURCE_PROFILE)) && profileMemoryMapper != null) {
                LambdaQueryWrapper<UserProfileMemoryEntity> wrapper = new LambdaQueryWrapper<UserProfileMemoryEntity>()
                        .eq(UserProfileMemoryEntity::getUserId, userId)
                        .eq(UserProfileMemoryEntity::getStatus, "active")
                        .and(w -> w.isNull(UserProfileMemoryEntity::getValidFrom)
                                .or().le(UserProfileMemoryEntity::getValidFrom, java.time.LocalDate.now(businessTimeZone)))
                        .and(w -> w.isNull(UserProfileMemoryEntity::getValidUntil)
                                .or().ge(UserProfileMemoryEntity::getValidUntil, java.time.LocalDate.now(businessTimeZone)))
                        .ne(UserProfileMemoryEntity::getMemoryType, "short_term_state")
                        .orderByDesc(UserProfileMemoryEntity::getUpdatedAt);
                wrapper.and(w -> {
                    w.like(UserProfileMemoryEntity::getAttributeKey, terms.get(0))
                            .or().like(UserProfileMemoryEntity::getAttributeValue, terms.get(0));
                    for (int i = 1; i < terms.size(); i++) {
                        String term = terms.get(i);
                        w.or().like(UserProfileMemoryEntity::getAttributeKey, term)
                                .or().like(UserProfileMemoryEntity::getAttributeValue, term);
                    }
                });
                for (UserProfileMemoryEntity memory : profileMemoryMapper.selectPage(Page.of(1, resultLimit), wrapper).getRecords()) {
                    if (!isEligibleProfileMemory(memory, userId, java.time.LocalDate.now(businessTimeZone))) continue;
                    String content = "用户长期画像 - " + memory.getAttributeKey() + ": " + memory.getAttributeValue();
                    hits.add(new RagHit(content, null, "profile:" + memory.getId(), null, SOURCE_PROFILE));
                }
            }
            if ((types.isEmpty() || types.contains(SOURCE_GRAPH)) && graphMapper != null) {
                LambdaQueryWrapper<DiaryKnowledgeGraphEntity> wrapper = new LambdaQueryWrapper<DiaryKnowledgeGraphEntity>()
                        .eq(DiaryKnowledgeGraphEntity::getUserId, userId)
                        .and(w -> w.isNull(DiaryKnowledgeGraphEntity::getStatus)
                                .or().eq(DiaryKnowledgeGraphEntity::getStatus, "active"))
                        .orderByDesc(DiaryKnowledgeGraphEntity::getCreatedAt);
                wrapper.and(w -> {
                    w.like(DiaryKnowledgeGraphEntity::getHeadEntity, terms.get(0))
                            .or().like(DiaryKnowledgeGraphEntity::getRelation, terms.get(0))
                            .or().like(DiaryKnowledgeGraphEntity::getTailEntity, terms.get(0));
                    for (int i = 1; i < terms.size(); i++) {
                        String term = terms.get(i);
                        w.or().like(DiaryKnowledgeGraphEntity::getHeadEntity, term)
                                .or().like(DiaryKnowledgeGraphEntity::getRelation, term)
                                .or().like(DiaryKnowledgeGraphEntity::getTailEntity, term);
                    }
                });
                for (DiaryKnowledgeGraphEntity triple : graphMapper.selectPage(Page.of(1, resultLimit), wrapper).getRecords()) {
                    String content = triple.getHeadEntity() + " " + triple.getRelation() + " " + triple.getTailEntity();
                    hits.add(new RagHit(content, null, "graph:" + triple.getId(), triple.getDiaryId(), SOURCE_GRAPH));
                }
            }
            log.info("RAG 关键词兜底完成 userId={} resultCount={} sourceTypes={}", userId, hits.size(), Arrays.toString(sourceTypes));
            return hits.stream().limit(resultLimit).toList();
        } catch (Exception e) {
            log.warn("RAG 关键词兜底失败 userId={} errorType={}", userId, e.getClass().getSimpleName());
            return List.of();
        }
    }

    private String[] sanitizeSourceTypes(String... sourceTypes) {
        if (sourceTypes == null || sourceTypes.length == 0) return new String[0];
        return Arrays.stream(sourceTypes)
                .filter(value -> value != null)
                .map(value -> value.trim().toLowerCase(java.util.Locale.ROOT))
                .filter(Set.of(SOURCE_DIARY, SOURCE_PROFILE, SOURCE_MUSIC, SOURCE_IMAGE, SOURCE_GRAPH, SOURCE_CHAPTER)::contains)
                .distinct().toArray(String[]::new);
    }

    /** Returns structured, provenance-aware items for ContextPlanner. */
    public List<ContextItem> retrieveContextItems(RagQuery query) {
        if (query == null) return List.of();
        return retrieveContextItems(query.userId(), query.queryText(), query.topK(), query.timeRange(), query.contextPurpose(),
                query.sourceTypes().toArray(String[]::new));
    }

    public List<ContextItem> retrieveContextItems(long userId, String query, int topK,
            ContextPurpose purpose, String... sourceTypes) {
        return retrieveContextItems(userId, query, topK, null, purpose, sourceTypes);
    }

    public List<ContextItem> retrieveContextItems(long userId, String query, int topK,
            TimeExpressionParser.TimeRange timeRange, ContextPurpose purpose, String... sourceTypes) {
        RagSearchResult search = searchDetailed(userId, query, topK, timeRange, sourceTypes);
        return retrieveContextItemsFromHits(userId, search.hits(), search.mode());
    }

    public List<RagHit> search(RagQuery query) {
        if (query == null) return List.of();
        return search(query.userId(), query.queryText(), query.topK(), query.timeRange(),
                query.sourceTypes().toArray(String[]::new));
    }

    public RagSearchResult searchDetailed(RagQuery query) {
        if (query == null) return RagSearchResult.empty();
        return searchDetailed(query.userId(), query.queryText(), query.topK(), query.timeRange(),
                query.sourceTypes().toArray(String[]::new));
    }

    List<ContextItem> retrieveContextItemsFromHits(long userId, List<RagHit> hits,
            RagSearchResult.Mode searchMode) {
        if (hits == null || hits.isEmpty()) return List.of();
        Set<String> diaryBackedTypes = Set.of(SOURCE_DIARY, SOURCE_MUSIC, SOURCE_IMAGE);
        Set<Long> diaryIds = hits.stream()
                .filter(hit -> hit != null && diaryBackedTypes.contains(hit.sourceType()))
                .map(RagHit::diaryId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        Map<Long, DiaryEntity> diaries = diaryMapper == null || diaryIds.isEmpty() ? Map.of()
                : diaryMapper.selectBatchIds(new ArrayList<>(diaryIds)).stream()
                .filter(diary -> Long.valueOf(userId).equals(diary.getAuthorUserId())
                        && !Boolean.TRUE.equals(diary.getIsDeleted()))
                .collect(java.util.stream.Collectors.toMap(DiaryEntity::getId, d -> d, (a, b) -> a));

        Set<String> rendered = new java.util.HashSet<>();
        List<ContextItem> result = new ArrayList<>();
        for (RagHit hit : hits) {
            String sourceType = hit.sourceType() == null ? "unknown" : hit.sourceType();
            boolean diaryBacked = diaryBackedTypes.contains(sourceType);
            DiaryEntity diary = diaryBacked && hit.diaryId() != null ? diaries.get(hit.diaryId()) : null;
            if (diaryBacked && hit.diaryId() != null && diary == null) continue;
            String content = diaryBacked && hit.diaryId() != null ? diary.getContent() : hit.content();
            if (SOURCE_MUSIC.equals(sourceType) || SOURCE_IMAGE.equals(sourceType)
                    || SOURCE_PROFILE.equals(sourceType) || SOURCE_GRAPH.equals(sourceType)) {
                content = hit.content();
            }
            content = SensitiveDataDetector.redact(content);
            String dedupKey = String.valueOf(hit.diaryId()) + ":" + sourceType + ":" + RagQueryBuilder.keyword(content);
            if (!rendered.add(dedupKey)) continue;
            String authorType = "user";
            String contentType = "original";
            ContextSource.TrustLevel trust = ContextSource.TrustLevel.SUPPORTING;
            if (SOURCE_IMAGE.equals(sourceType)) {
                content = hit.content();
                sourceType = "SYSTEM_IMAGE_CAPTION";
                authorType = "system";
                contentType = "derived";
                trust = ContextSource.TrustLevel.UNTRUSTED;
            } else if (SOURCE_MUSIC.equals(sourceType)) {
                sourceType = "USER_PROVIDED_LYRICS";
                contentType = "user_selected_context";
            } else if (SOURCE_PROFILE.equals(sourceType)) {
                sourceType = "FORMAL_MEMORY";
                contentType = "structured_memory";
                trust = ContextSource.TrustLevel.SUPPORTING;
            } else if (SOURCE_GRAPH.equals(sourceType)) {
                sourceType = "SYSTEM_GRAPH_DERIVATION";
                contentType = "structured_graph";
            } else if (SOURCE_DIARY.equals(sourceType)) {
                sourceType = "USER_DIARY";
            }
            // Some source-specific branches above intentionally use hit.content
            // instead of the diary body. Redact after that selection as well.
            content = SensitiveDataDetector.redact(content);
            if (content == null || content.isBlank()) continue;
            Instant eventTime = diary != null && diary.getCreatedAt() != null
                    ? diary.getCreatedAt().atZone(businessTimeZone).toInstant() : null;
            result.add(new ContextItem(truncate(content, 2500), new ContextSource(
                    sourceType, contextSourceId(hit, sourceType),
                    authorType, contentType, eventTime,
                    searchMode == null ? null : searchMode.name().toLowerCase(java.util.Locale.ROOT),
                    trust, userId),
                    hit.score() == null ? 0D : hit.score(), 20, false));
        }
        return result;
    }

    private String contextSourceId(RagHit hit, String normalizedSourceType) {
        if (hit == null) return null;
        if (SOURCE_DIARY.equals(hit.sourceType()) || "USER_DIARY".equals(normalizedSourceType)) {
            return hit.diaryId() == null ? hit.sourceId() : String.valueOf(hit.diaryId());
        }
        // Profile and graph references must retain their own stable IDs. Using a
        // backing diary ID here made unrelated graph/profile records collide in
        // deduplication and made provenance point at the wrong source.
        return hit.sourceId();
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
            long fromTs = startDate != null ? startDate.atStartOfDay(businessTimeZone).toEpochSecond() : 0;
            long toTs = endDate != null ? endDate.atTime(java.time.LocalTime.MAX).atZone(businessTimeZone).toEpochSecond() : Long.MAX_VALUE;
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
                        .filter(diary -> Long.valueOf(userId).equals(diary.getAuthorUserId())
                                && !Boolean.TRUE.equals(diary.getIsDeleted()))
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

    void parseResults(List<?> raw, List<RagHit> out) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        // 智能提取：兼容 RESP2 数组和 RESP3 Map/extra_attributes 格式。
        extractHitsHeuristically(raw, out);
    }

    private Long parseResultCount(Object value) {
        if (value instanceof Number number) return number.longValue();
        String text = asString(value);
        if (text == null || !text.matches("\\d+")) return null;
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void extractHitsHeuristically(List<?> list, List<RagHit> out) {
        String currentKey = null;
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);

            // 识别 RESP2 的 key
            if (isScalarText(item) && i + 1 < list.size() && list.get(i + 1) instanceof List<?>) {
                currentKey = asString(item);
            }

            if (item instanceof List<?> subList) {
                ParsedHit parsed = parseHitFields(subList);
                String finalKey = parsed.id() != null ? parsed.id() : currentKey;
                if (parsed.content() != null && finalKey != null) {
                    addParsedHit(finalKey, parsed.content(), parsed.score(), out);
                } else {
                    // 如果当前子列表不是文档，继续向下递归寻找
                    extractHitsHeuristically(subList, out);
                }
            } else if (item instanceof Map<?, ?> map) {
                ParsedHit parsed = parseHitFields(map);
                String finalKey = parsed.id() != null ? parsed.id() : currentKey;
                if (parsed.content() != null && finalKey != null) {
                    addParsedHit(finalKey, parsed.content(), parsed.score(), out);
                } else {
                    extractNestedValues(map, out, currentKey);
                }
            }
        }
    }

    private ParsedHit parseHitFields(List<?> values) {
        String content = null;
        String id = null;
        Double score = null;
        for (int i = 0; i + 1 < values.size(); i += 2) {
            String key = asString(values.get(i));
            Object value = values.get(i + 1);
            if ("id".equals(key)) id = asString(value);
            else if ("content".equals(key)) content = asString(value);
            else if ("_score".equals(key)) score = parseScore(value);
            else if ("extra_attributes".equals(key)) {
                ParsedHit nested = parseHitFields(value instanceof List<?> list ? list : List.of());
                if (content == null) content = nested.content();
                if (score == null) score = nested.score();
                if (id == null) id = nested.id();
            }
        }
        return new ParsedHit(id, content, score);
    }

    private ParsedHit parseHitFields(Map<?, ?> map) {
        String content = valueFor(map, "content");
        String id = valueFor(map, "id");
        Double score = parseScore(map.get("_score"));
        Object extra = map.get("extra_attributes");
        if (extra instanceof Map<?, ?> extraMap) {
            if (content == null) content = valueFor(extraMap, "content");
            if (score == null) score = parseScore(extraMap.get("_score"));
        } else if (extra instanceof List<?> extraList) {
            ParsedHit nested = parseHitFields(extraList);
            if (content == null) content = nested.content();
            if (score == null) score = nested.score();
        }
        return new ParsedHit(id, content, score);
    }

    private void extractNestedValues(Map<?, ?> map, List<RagHit> out, String currentKey) {
        for (Object value : map.values()) {
            if (value instanceof List<?> list) extractHitsHeuristically(list, out);
            else if (value instanceof Map<?, ?> nested) {
                ParsedHit parsed = parseHitFields(nested);
                String key = parsed.id() == null ? currentKey : parsed.id();
                if (parsed.content() != null && key != null) addParsedHit(key, parsed.content(), parsed.score(), out);
            }
        }
    }

    private String valueFor(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? null : asString(value);
    }

    private void addParsedHit(String key, String content, Double score, List<RagHit> out) {
        RagKeyInfo info = parseRagKey(key);
        if (info.sourceType() != null && content != null) {
            out.add(new RagHit(content, score, info.sourceId(), info.diaryId(), info.sourceType()));
        }
    }

    private boolean isScalarText(Object value) {
        return value instanceof byte[] || value instanceof String;
    }

    private record ParsedHit(String id, String content, Double score) {}

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
        if (key == null || !key.startsWith(KEY_PREFIX)) return new RagKeyInfo(null, null, null);
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
     * Converts the legacy attribute-key profile keys to memory-id keys exactly once.
     * The caller supplies already-filtered current memories so this method does not query or expose other users' data.
     */
    public void migrateLegacyProfileIndex(Map<Long, List<UserProfileMemoryEntity>> grouped) {
        if (embeddingApiKey.isBlank()) {
            log.warn("RAG 画像旧索引迁移跳过：未配置 embedding API Key，下次启动将继续迁移");
            return;
        }
        if (grouped == null) {
            // A missing database snapshot must never be interpreted as an empty
            // profile. Keep the old index and retry on the next startup.
            log.warn("RAG 画像旧索引迁移跳过：未取得用户画像快照，下次启动将重试");
            return;
        }
        if (PROFILE_SCHEMA_VERSION.equals(redis.opsForValue().get(PROFILE_SCHEMA_KEY))) {
            return;
        }
        String lockToken = UUID.randomUUID().toString();
        if (!acquireLock(PROFILE_LOCK_PREFIX + "migration", lockToken)) {
            log.warn("RAG 画像旧索引迁移跳过：获取迁移锁超时");
            return;
        }
        AtomicBoolean migrationOwnershipLost = new AtomicBoolean(false);
        ScheduledFuture<?> migrationRenewal = startLockRenewal(PROFILE_LOCK_PREFIX + "migration", lockToken,
                migrationOwnershipLost);
        try {
            if (!ensureLockOwnership(PROFILE_LOCK_PREFIX + "migration", lockToken, migrationOwnershipLost)) {
                log.warn("RAG 画像旧索引迁移终止：迁移锁已失效");
                return;
            }
            List<String> oldKeys = scanKeys(PROFILE_KEY_PREFIX + "*");
            if (!oldKeys.isEmpty()) {
                redis.delete(oldKeys);
            }
            boolean complete = true;
            if (grouped != null) {
                for (Map.Entry<Long, List<UserProfileMemoryEntity>> entry : grouped.entrySet()) {
                    if (entry.getKey() != null) {
                        if (!ensureLockOwnership(PROFILE_LOCK_PREFIX + "migration", lockToken,
                                migrationOwnershipLost)) {
                            complete = false;
                            break;
                        }
                        String userLockKey = PROFILE_LOCK_PREFIX + entry.getKey();
                        String userLockToken = UUID.randomUUID().toString();
                        if (!acquireLock(userLockKey, userLockToken)) {
                            complete = false;
                            log.warn("RAG 画像旧索引迁移跳过用户：获取画像锁超时 userId={}", entry.getKey());
                            continue;
                        }
                        AtomicBoolean ownershipLost = new AtomicBoolean(false);
                        ScheduledFuture<?> renewal = startLockRenewal(userLockKey, userLockToken, ownershipLost);
                        try {
                            complete &= indexUserProfileLocked(entry.getKey(), entry.getValue(), System.currentTimeMillis(),
                                    userLockKey, userLockToken, ownershipLost);
                        } finally {
                            renewal.cancel(false);
                            releaseLock(userLockKey, userLockToken);
                        }
                    }
                }
            }
            if (complete) {
                redis.opsForValue().set(PROFILE_SCHEMA_KEY, PROFILE_SCHEMA_VERSION);
                log.info("RAG 画像旧索引迁移完成 userCount={} deletedKeys={}", grouped == null ? 0 : grouped.size(), oldKeys.size());
            } else {
                log.warn("RAG 画像旧索引迁移未完成，embedding 失败，下次启动将继续迁移");
            }
        } catch (Exception e) {
            log.error("RAG 画像旧索引迁移失败，下次启动将重试: {}", e.getMessage(), e);
        } finally {
            migrationRenewal.cancel(false);
            releaseLock(PROFILE_LOCK_PREFIX + "migration", lockToken);
        }
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
                if (m == null || m.getId() == null) {
                    log.warn("批量画像向量化跳过无 ID 记忆 userId={} attributeKey={}", userId,
                            m == null ? null : m.getAttributeKey());
                    continue;
                }
                if (SensitiveDataDetector.containsSensitiveData(m.getAttributeKey())
                        || SensitiveDataDetector.containsSensitiveData(m.getAttributeValue())) {
                    log.warn("批量画像向量化跳过敏感正式记忆 userId={} memoryId={}", userId, m.getId());
                    continue;
                }
                String text = "用户长期画像 - " + m.getAttributeKey() + ": " + m.getAttributeValue();
                float[] vec = embed(text);
                if (vec != null) {
                    storeEmbedding("profile:" + userId + ":" + m.getId(), userId, SOURCE_PROFILE, text, vec,
                            Map.of("memory_id", String.valueOf(m.getId()), "content_hash", profileFingerprint(m),
                                    "memory_updated_at", String.valueOf(memoryTimestamp(m))));
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
            var keys = scanKeys(pattern);
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
                var chunkKeys = scanKeys(KEY_PREFIX + "diary:" + diary.getId() + ":*");
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
