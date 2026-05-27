package com.moodcopilot.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.DiaryKnowledgeGraphEntity;
import com.moodcopilot.graph.GraphService;
import com.moodcopilot.mapper.DiaryKnowledgeGraphMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GraphConsolidationService {

    private static final Logger log = LoggerFactory.getLogger(GraphConsolidationService.class);

    private static final String CONSOLIDATION_PROMPT = """
            你是一个 AI 知识图谱整理专家。下面是一位用户长期积累的情绪与心理关系网络（三元组）。
            由于是分多次提取的，其中可能包含重复的因果链、纯事件流水账、或者可以被概括的琐碎关联。

            你的任务是将这些关联进行合并、去重和提纯，剔除没有情绪深度的连结，输出一份高度精简、结构清晰的全新图谱。
            
            【整理规则】
            1. 情绪导向：图谱的终极目的是分析“触发源”到“用户内心感受/情绪状态”的联系。如果原三元组只是纯客观事实或行为流水账（如“去操场->看到人”、“朋友->挂贴吧”），请直接删除，不予保留。
            2. 合并同类项：例如将“晚上熬夜”、“熬夜工作”统一合并为“熬夜”；将“稍微有点心烦”、“暴躁”合并归类为更核心的情绪词如“烦躁”。
            3. 实体提纯：确保 head 节点是精炼的触发源，tail 节点是具体的情绪或心理状态（如“焦虑”、“内耗”、“释怀”）。剔除纯关于他人情绪的无意义节点。
            4. 压缩路径：如果存在 触发事件A -> 行为B，行为B -> 情绪C，可将其合并为 触发事件A -> 情绪C。
            
            只输出合法 JSON，不要输出 markdown 格式，不要有任何解释。
            JSON 格式必须是：
            {
              "triples": [
                {"headEntity": "...", "relation": "...", "tailEntity": "...", "tailPolarity": 1},
                {"headEntity": "...", "relation": "...", "tailEntity": "...", "tailPolarity": -1}
              ]
            }
            """;

    private final ChatClient chatClient;
    private final DiaryKnowledgeGraphMapper graphMapper;
    private final GraphService graphService;
    private final ObjectMapper objectMapper;
    private final TransactionOperations transactionOperations;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final RagMemoryService ragMemoryService;

    public GraphConsolidationService(@Qualifier("analysisChatClient") ChatClient chatClient,
                                     DiaryKnowledgeGraphMapper graphMapper,
                                     GraphService graphService,
                                     ObjectMapper objectMapper,
                                     TransactionOperations transactionOperations,
                                     org.springframework.data.redis.core.StringRedisTemplate redisTemplate,
                                     RagMemoryService ragMemoryService) {
        this.chatClient = chatClient;
        this.graphMapper = graphMapper;
        this.graphService = graphService;
        this.objectMapper = objectMapper;
        this.transactionOperations = transactionOperations;
        this.redisTemplate = redisTemplate;
        this.ragMemoryService = ragMemoryService;
    }

    public record ConsolidatedGraphResponse(@JsonProperty("triples") List<ConsolidatedTriple> triples) {}
    public record ConsolidatedTriple(@JsonProperty("headEntity") String headEntity,
                                     @JsonProperty("relation") String relation,
                                     @JsonProperty("tailEntity") String tailEntity,
                                     @JsonProperty("tailPolarity") Integer tailPolarity) {}

    public List<ConsolidatedTriple> previewConsolidation(Long userId) {
        // Rate limit logic
        String today = java.time.LocalDate.now().toString();
        String redisKey = "graph:consolidate:count:" + userId + ":" + today;
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1) {
            redisTemplate.expire(redisKey, java.time.Duration.ofDays(1));
        }
        if (count != null && count > 2) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "每天最多只能进行2次关系图谱整理");
        }

        List<DiaryKnowledgeGraphEntity> existing = graphService.getTriplesForUser(userId);
        if (existing.isEmpty() || existing.size() < 3) {
            throw new RuntimeException("图谱节点过少，无需整理");
        }

        String prompt = buildPrompt(existing);
        String json = chatClient.prompt()
                .system(CONSOLIDATION_PROMPT)
                .user(prompt)
                .call()
                .content();

        try {
            String cleanedJson = JsonUtils.cleanJson(json);
            ConsolidatedGraphResponse response = objectMapper.readValue(cleanedJson, ConsolidatedGraphResponse.class);
            return response.triples();
        } catch (Exception e) {
            log.error("Failed to parse LLM response for graph consolidation", e);
            throw new RuntimeException("AI 返回格式错误，请稍后重试");
        }
    }

    public void applyConsolidation(Long userId, List<ConsolidatedTriple> newTriples) {
        List<DiaryKnowledgeGraphEntity> existing = graphService.getTriplesForUser(userId);
        LocalDateTime now = LocalDateTime.now();

        // 删除旧的 RAG 向量
        for (DiaryKnowledgeGraphEntity old : existing) {
            ragMemoryService.deleteKnowledgeGraph(old.getId());
        }

        transactionOperations.execute(status -> {
            for (DiaryKnowledgeGraphEntity old : existing) {
                graphMapper.deleteById(old.getId());
            }

            for (ConsolidatedTriple t : newTriples) {
                DiaryKnowledgeGraphEntity entity = new DiaryKnowledgeGraphEntity();
                entity.setUserId(userId);
                entity.setDiaryId(-1L); // Indicates consolidated
                entity.setHeadEntity(trimTo(t.headEntity(), 64));
                entity.setRelation(trimTo(t.relation(), 64));
                entity.setTailEntity(trimTo(t.tailEntity(), 64));
                entity.setTailPolarity(t.tailPolarity() != null ? t.tailPolarity() : 0);
                entity.setCreatedAt(now);
                graphMapper.insert(entity);
            }
            return null;
        });

        // 重新向量化
        List<DiaryKnowledgeGraphEntity> latest = graphService.getTriplesForUser(userId);
        for (DiaryKnowledgeGraphEntity entity : latest) {
            ragMemoryService.indexKnowledgeGraph(userId, entity.getDiaryId(), entity.getId(),
                    entity.getHeadEntity(), entity.getRelation(), entity.getTailEntity());
        }
    }

    private String buildPrompt(List<DiaryKnowledgeGraphEntity> existing) {
        return "现有图谱三元组：\n" + existing.stream()
                .map(t -> t.getHeadEntity() + " --(" + t.getRelation() + ")--> " + t.getTailEntity() + " [极性:" + (t.getTailPolarity() == null ? 0 : t.getTailPolarity()) + "]")
                .collect(Collectors.joining("\n"));
    }

    private String trimTo(String val, int maxLen) {
        if (val == null) return "未知";
        String trimmed = val.trim();
        if (trimmed.length() > maxLen) {
            return trimmed.substring(0, maxLen);
        }
        return trimmed;
    }
}
