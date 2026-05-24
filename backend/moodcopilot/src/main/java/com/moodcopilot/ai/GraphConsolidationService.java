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
            你是一个 AI 知识图谱整理专家。下面是一位用户长期积累的事件关系网络（三元组）。
            由于是分多次提取的，其中可能包含重复的因果链、细微差异但语义相同的实体、或者可以被更高级别概念概括的琐碎关联。

            你的任务是将这些关联进行合并、去重和提纯，输出一份高度精简、结构清晰的全新图谱。
            
            【整理规则】
            1. 合并同类实体：例如将“晚上熬夜”、“熬夜工作”、“总是熬夜”统一合并为“熬夜”。
            2. 精简关系路径：如果存在 A->B, B->C，且意义上等同于 A->C，在不丢失关键信息前提下可以压缩。
            3. 去除无意义的三元组：删除孤立、没有业务价值的噪声事件。
            
            只输出合法 JSON，不要输出 markdown 格式，不要有任何解释。
            JSON 格式必须是：
            {
              "triples": [
                {"headEntity": "...", "relation": "...", "tailEntity": "..."},
                {"headEntity": "...", "relation": "...", "tailEntity": "..."}
              ]
            }
            """;

    private final ChatClient chatClient;
    private final DiaryKnowledgeGraphMapper graphMapper;
    private final GraphService graphService;
    private final ObjectMapper objectMapper;
    private final TransactionOperations transactionOperations;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    public GraphConsolidationService(@Qualifier("analysisChatClient") ChatClient chatClient,
                                     DiaryKnowledgeGraphMapper graphMapper,
                                     GraphService graphService,
                                     ObjectMapper objectMapper,
                                     TransactionOperations transactionOperations,
                                     org.springframework.data.redis.core.StringRedisTemplate redisTemplate) {
        this.chatClient = chatClient;
        this.graphMapper = graphMapper;
        this.graphService = graphService;
        this.objectMapper = objectMapper;
        this.transactionOperations = transactionOperations;
        this.redisTemplate = redisTemplate;
    }

    public record ConsolidatedGraphResponse(@JsonProperty("triples") List<ConsolidatedTriple> triples) {}
    public record ConsolidatedTriple(@JsonProperty("headEntity") String headEntity,
                                     @JsonProperty("relation") String relation,
                                     @JsonProperty("tailEntity") String tailEntity) {}

    public List<ConsolidatedTriple> previewConsolidation(Long userId) {
        // Rate limit logic
        String today = java.time.LocalDate.now().toString();
        String redisKey = "graph:consolidate:count:" + userId + ":" + today;
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1) {
            redisTemplate.expire(redisKey, java.time.Duration.ofDays(1));
        }
        if (count != null && count > 2) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "每天最多只能进行两次智能整理");
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
                entity.setCreatedAt(now);
                graphMapper.insert(entity);
            }
            return null;
        });
    }

    private String buildPrompt(List<DiaryKnowledgeGraphEntity> existing) {
        return "现有图谱三元组：\n" + existing.stream()
                .map(t -> t.getHeadEntity() + " --(" + t.getRelation() + ")--> " + t.getTailEntity())
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
