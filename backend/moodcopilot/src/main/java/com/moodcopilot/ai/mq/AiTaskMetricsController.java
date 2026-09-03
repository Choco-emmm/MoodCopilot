package com.moodcopilot.ai.mq;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.moodcopilot.common.ApiResponse;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.mapper.UserMemoryCandidateMapper;
import com.moodcopilot.mapper.UserMemoryEvidenceMapper;
import com.moodcopilot.mapper.UserProfileMemoryMapper;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.FORBIDDEN;

@RestController
@RequestMapping("/api/admin/ai-metrics")
public class AiTaskMetricsController {
    private final AiTaskMapper taskMapper;
    private final UserMemoryCandidateMapper candidateMapper;
    private final UserMemoryEvidenceMapper evidenceMapper;
    private final UserProfileMemoryMapper memoryMapper;
    private final RabbitAdmin rabbitAdmin;

    public AiTaskMetricsController(AiTaskMapper taskMapper,
                                   UserMemoryCandidateMapper candidateMapper,
                                   UserMemoryEvidenceMapper evidenceMapper,
                                   UserProfileMemoryMapper memoryMapper,
                                   RabbitAdmin rabbitAdmin) {
        this.taskMapper = taskMapper;
        this.candidateMapper = candidateMapper;
        this.evidenceMapper = evidenceMapper;
        this.memoryMapper = memoryMapper;
        this.rabbitAdmin = rabbitAdmin;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> metrics() {
        requireAdmin();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskStatus", countBy("status"));
        result.put("candidateStatus", candidateMapper.selectMaps(new QueryWrapper<com.moodcopilot.entity.UserMemoryCandidateEntity>()
                .select("status", "COUNT(*) AS count").groupBy("status")));
        result.put("evidenceCount", evidenceMapper.selectCount(null));
        result.put("activeMemoryCount", memoryMapper.selectCount(new QueryWrapper<com.moodcopilot.entity.UserProfileMemoryEntity>()
                .eq("status", "active")));
        Map<String, Object> queues = new LinkedHashMap<>();
        List.of("ai.analysis", "ai.memory", "ai.life-event", "ai.graph", "ai.rag",
                "ai.report-invalidation", "ai.notification").forEach(queue -> {
            try {
                QueueInformation info = rabbitAdmin.getQueueInfo(queue);
                queues.put(queue, info == null ? 0 : info.getMessageCount());
            } catch (Exception e) {
                queues.put(queue, null);
            }
        });
        result.put("queueDepth", queues);
        result.put("generatedAt", java.time.Instant.now());
        return ApiResponse.ok(result);
    }

    private List<Map<String, Object>> countBy(String column) {
        return taskMapper.selectMaps(new QueryWrapper<AiTaskEntity>()
                .select(column, "COUNT(*) AS count").groupBy(column));
    }

    private void requireAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth != null && auth.getPrincipal() instanceof UserEntity user
                && "ADMIN".equalsIgnoreCase(user.getRole()))) {
            throw new ResponseStatusException(FORBIDDEN, "需要管理员权限");
        }
    }
}
