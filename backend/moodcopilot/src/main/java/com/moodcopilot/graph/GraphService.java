package com.moodcopilot.graph;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodcopilot.entity.DiaryKnowledgeGraphEntity;
import com.moodcopilot.mapper.DiaryKnowledgeGraphMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GraphService {

    private final DiaryKnowledgeGraphMapper mapper;

    public GraphService(DiaryKnowledgeGraphMapper mapper) {
        this.mapper = mapper;
    }

    public GraphData getGraphDataForUser(Long userId) {
        List<DiaryKnowledgeGraphEntity> triples = mapper.selectList(
            new LambdaQueryWrapper<DiaryKnowledgeGraphEntity>()
                .eq(DiaryKnowledgeGraphEntity::getUserId, userId)
        );

        Set<String> nodesSet = new HashSet<>();
        List<GraphEdge> edges = new ArrayList<>();
        Map<String, Integer> nodeDegree = new HashMap<>();

        for (DiaryKnowledgeGraphEntity t : triples) {
            String head = t.getHeadEntity();
            String tail = t.getTailEntity();
            String relation = t.getRelation();

            if (head == null || tail == null || relation == null || head.isBlank() || tail.isBlank()) continue;

            nodesSet.add(head);
            nodesSet.add(tail);
            nodeDegree.put(head, nodeDegree.getOrDefault(head, 0) + 1);
            nodeDegree.put(tail, nodeDegree.getOrDefault(tail, 0) + 1);

            edges.add(new GraphEdge(head, tail, relation, t.getDiaryId(), t.getTailPolarity()));
        }

        List<GraphNode> nodes = new ArrayList<>();
        for (String n : nodesSet) {
            nodes.add(new GraphNode(n, n, nodeDegree.getOrDefault(n, 1)));
        }

        return new GraphData(nodes, edges);
    }

    public List<DiaryKnowledgeGraphEntity> getTriplesForUser(Long userId) {
        return mapper.selectList(
            new LambdaQueryWrapper<DiaryKnowledgeGraphEntity>()
                .eq(DiaryKnowledgeGraphEntity::getUserId, userId)
                .orderByDesc(DiaryKnowledgeGraphEntity::getCreatedAt)
        );
    }

    public void updateTriple(Long userId, Long id, DiaryKnowledgeGraphEntity data) {
        DiaryKnowledgeGraphEntity entity = mapper.selectById(id);
        if (entity == null || !entity.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在或无权限");
        }
        entity.setHeadEntity(data.getHeadEntity());
        entity.setRelation(data.getRelation());
        entity.setTailEntity(data.getTailEntity());
        entity.setTailPolarity(data.getTailPolarity());
        mapper.updateById(entity);
    }

    public void deleteTriple(Long userId, Long id) {
        DiaryKnowledgeGraphEntity entity = mapper.selectById(id);
        if (entity == null || !entity.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在或无权限");
        }
        mapper.deleteById(id);
    }

    public static class GraphData {
        public List<GraphNode> nodes;
        public List<GraphEdge> edges;
        public GraphData(List<GraphNode> nodes, List<GraphEdge> edges) { this.nodes = nodes; this.edges = edges; }
    }

    public static class GraphNode {
        public String id;
        public String name;
        public int value;
        public GraphNode(String id, String name, int value) { this.id = id; this.name = name; this.value = value; }
    }

    public static class GraphEdge {
        public String source;
        public String target;
        public String label;
        public Long diaryId;
        public Integer tailPolarity;
        public GraphEdge(String source, String target, String label, Long diaryId, Integer tailPolarity) {
            this.source = source; this.target = target; this.label = label; this.diaryId = diaryId; this.tailPolarity = tailPolarity;
        }
    }
}
