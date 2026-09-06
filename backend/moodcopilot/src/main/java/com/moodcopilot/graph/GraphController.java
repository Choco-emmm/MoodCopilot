package com.moodcopilot.graph;

import com.moodcopilot.common.ApiResponse;
import com.moodcopilot.entity.UserEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import com.moodcopilot.entity.DiaryKnowledgeGraphEntity;
import com.moodcopilot.ai.GraphConsolidationService;
import com.moodcopilot.ai.mq.AiTaskEntity;
import com.moodcopilot.ai.mq.AiTaskMessage;
import com.moodcopilot.ai.mq.AiTaskProducer;
import com.moodcopilot.ai.mq.AiTaskService;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/graph")
public class GraphController {

    private final GraphService graphService;
    private final GraphConsolidationService graphConsolidationService;
    private final AiTaskProducer aiTaskProducer;
    private final AiTaskService aiTaskService;

    public GraphController(GraphService graphService, GraphConsolidationService graphConsolidationService,
                           AiTaskProducer aiTaskProducer, AiTaskService aiTaskService) {
        this.graphService = graphService;
        this.graphConsolidationService = graphConsolidationService;
        this.aiTaskProducer = aiTaskProducer;
        this.aiTaskService = aiTaskService;
    }

    @GetMapping("/user-graph")
    public ApiResponse<GraphService.GraphData> getUserGraph(@AuthenticationPrincipal UserEntity user) {
        if (user == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "登录状态已失效");
        }
        return ApiResponse.ok(graphService.getGraphDataForUser(user.getId()));
    }

    @GetMapping("/triples")
    public ApiResponse<List<DiaryKnowledgeGraphEntity>> getTriples(@AuthenticationPrincipal UserEntity user) {
        if (user == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "登录状态已失效");
        }
        return ApiResponse.ok(graphService.getTriplesForUser(user.getId()));
    }

    @PutMapping("/triples/{id}")
    public ApiResponse<Void> updateTriple(@AuthenticationPrincipal UserEntity user, @PathVariable Long id, @RequestBody DiaryKnowledgeGraphEntity data) {
        if (user == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "登录状态已失效");
        }
        graphService.updateTriple(user.getId(), id, data);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/triples/{id}")
    public ApiResponse<Void> deleteTriple(@AuthenticationPrincipal UserEntity user, @PathVariable Long id) {
        if (user == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "登录状态已失效");
        }
        graphService.deleteTriple(user.getId(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/consolidate/preview")
    public ApiResponse<java.util.Map<String, Object>> previewConsolidate(@AuthenticationPrincipal UserEntity user) {
        if (user == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "登录状态已失效");
        }
        graphConsolidationService.reserveConsolidation(user.getId());
        String taskId = aiTaskProducer.submitGraphConsolidationTask(user.getId());
        return ApiResponse.ok(java.util.Map.of("taskId", taskId, "status", "PENDING"));
    }

    @GetMapping("/consolidate/tasks/{taskId}")
    public ApiResponse<java.util.Map<String, Object>> consolidationTask(@AuthenticationPrincipal UserEntity user,
                                                                         @PathVariable String taskId) {
        AiTaskEntity task = aiTaskService.getTask(taskId);
        if (task == null || !user.getId().equals(task.getUserId())
                || !AiTaskMessage.TYPE_GRAPH_CONSOLIDATION.equals(task.getTaskType())) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "整理任务不存在");
        }
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("status", task.getStatus());
        if ("SUCCEEDED".equals(task.getStatus())) result.put("triples", graphConsolidationService.readTaskResult(taskId));
        if (task.getLastError() != null) result.put("error", task.getLastError());
        return ApiResponse.ok(result);
    }

    @PostMapping("/consolidate/apply")
    public ApiResponse<Void> applyConsolidate(@AuthenticationPrincipal UserEntity user, @RequestBody List<GraphConsolidationService.ConsolidatedTriple> triples) {
        if (user == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "登录状态已失效");
        }
        graphConsolidationService.applyConsolidation(user.getId(), triples);
        return ApiResponse.ok(null);
    }
}
