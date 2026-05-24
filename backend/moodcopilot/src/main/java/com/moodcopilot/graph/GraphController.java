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

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/graph")
public class GraphController {

    private final GraphService graphService;
    private final GraphConsolidationService graphConsolidationService;

    public GraphController(GraphService graphService, GraphConsolidationService graphConsolidationService) {
        this.graphService = graphService;
        this.graphConsolidationService = graphConsolidationService;
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
    public ApiResponse<List<GraphConsolidationService.ConsolidatedTriple>> previewConsolidate(@AuthenticationPrincipal UserEntity user) {
        if (user == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "登录状态已失效");
        }
        return ApiResponse.ok(graphConsolidationService.previewConsolidation(user.getId()));
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
