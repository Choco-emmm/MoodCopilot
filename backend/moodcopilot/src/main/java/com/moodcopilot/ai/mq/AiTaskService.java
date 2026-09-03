package com.moodcopilot.ai.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.config.RabbitMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AiTaskService {
    private static final Logger log = LoggerFactory.getLogger(AiTaskService.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final int DISPATCH_LEASE_SECONDS = 30;
    private static final int RUN_LEASE_MINUTES = 15;
    private static final String NODE_ID = "node-" + UUID.randomUUID();

    private final AiTaskMapper taskMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public AiTaskService(AiTaskMapper taskMapper, RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public String enqueue(Long userId, String taskType, String aggregateId, String analysisVersion,
                          String requestedModel, String idempotencyKey, Map<String, Object> payload,
                          String parentTaskId) {
        AiTaskEntity existing = taskMapper.selectOne(new LambdaQueryWrapper<AiTaskEntity>()
                .eq(AiTaskEntity::getIdempotencyKey, idempotencyKey).last("LIMIT 1"));
        if (existing != null) return existing.getTaskId();
        AiTaskEntity task = new AiTaskEntity();
        task.setTaskId(UUID.randomUUID().toString());
        task.setParentTaskId(parentTaskId);
        task.setUserId(userId);
        task.setTaskType(taskType);
        task.setAggregateId(aggregateId);
        task.setAnalysisVersion(analysisVersion);
        task.setRequestedModel(requestedModel);
        task.setIdempotencyKey(idempotencyKey);
        task.setPayload(toJson(payload));
        task.setStatus("PENDING_DISPATCH");
        task.setAttempts(0);
        task.setMaxAttempts(MAX_ATTEMPTS);
        taskMapper.insert(task);
        return task.getTaskId();
    }

    public void dispatchDueTasks() {
        LocalDateTime now = LocalDateTime.now();
        List<AiTaskEntity> due = taskMapper.selectList(new LambdaQueryWrapper<AiTaskEntity>()
                .in(AiTaskEntity::getStatus, "PENDING_DISPATCH", "RETRY_WAIT")
                .and(w -> w.isNull(AiTaskEntity::getNextRetryAt).or().le(AiTaskEntity::getNextRetryAt, now))
                .and(w -> w.isNull(AiTaskEntity::getLeaseUntil).or().lt(AiTaskEntity::getLeaseUntil, now))
                .orderByAsc(AiTaskEntity::getCreatedAt).last("LIMIT 50"));
        for (AiTaskEntity task : due) {
            if (!claimForDispatch(task.getTaskId(), now)) continue;
            try {
                AiTaskMessage message = new AiTaskMessage(task.getTaskId(), task.getUserId(),
                        task.getTaskType(), task.getAggregateId());
                CorrelationData correlation = new CorrelationData(task.getTaskId());
                rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, routingKey(task.getTaskType()), message,
                        correlation);
                CorrelationData.Confirm confirm = correlation.getFuture().get(10, TimeUnit.SECONDS);
                if (!confirm.isAck()) {
                    throw new IllegalStateException("RabbitMQ publisher confirm rejected: " + confirm.getReason());
                }
                taskMapper.update(null, new LambdaUpdateWrapper<AiTaskEntity>()
                        .eq(AiTaskEntity::getTaskId, task.getTaskId())
                        .eq(AiTaskEntity::getStatus, "RUNNING")
                        .eq(AiTaskEntity::getLeaseOwner, NODE_ID)
                        .isNull(AiTaskEntity::getStartedAt)
                        .set(AiTaskEntity::getStatus, "PUBLISHED")
                        .set(AiTaskEntity::getLeaseOwner, null)
                        .set(AiTaskEntity::getLeaseUntil, null)
                        .set(AiTaskEntity::getPublishedAt, LocalDateTime.now()));
            } catch (Exception e) {
                markDispatchFailed(task.getTaskId(), e);
            }
        }
    }

    public AiTaskEntity claimForRun(String taskId) {
        LocalDateTime now = LocalDateTime.now();
        int updated = taskMapper.update(null, new LambdaUpdateWrapper<AiTaskEntity>()
                .eq(AiTaskEntity::getTaskId, taskId)
                .and(w -> w.eq(AiTaskEntity::getStatus, "PUBLISHED")
                        .or(x -> x.eq(AiTaskEntity::getStatus, "RETRY_WAIT")
                                .and(y -> y.isNull(AiTaskEntity::getNextRetryAt)
                                        .or().le(AiTaskEntity::getNextRetryAt, now)))
                        .or(x -> x.eq(AiTaskEntity::getStatus, "RUNNING")
                                .eq(AiTaskEntity::getLeaseOwner, NODE_ID)
                                .isNull(AiTaskEntity::getStartedAt)
                                .gt(AiTaskEntity::getLeaseUntil, now)))
                .set(AiTaskEntity::getStatus, "RUNNING")
                .set(AiTaskEntity::getLeaseOwner, NODE_ID)
                .set(AiTaskEntity::getLeaseUntil, now.plusMinutes(RUN_LEASE_MINUTES))
                .set(AiTaskEntity::getStartedAt, now)
                .setSql("attempts = attempts + 1"));
        return updated == 1 ? taskMapper.selectById(taskId) : null;
    }

    @Transactional
    public void markSucceeded(String taskId) {
        markSucceeded(taskId, NODE_ID);
    }

    @Transactional
    public void markSucceeded(String taskId, String leaseOwner) {
        taskMapper.update(null, new LambdaUpdateWrapper<AiTaskEntity>()
                .eq(AiTaskEntity::getTaskId, taskId).eq(AiTaskEntity::getStatus, "RUNNING")
                .eq(AiTaskEntity::getLeaseOwner, leaseOwner)
                .set(AiTaskEntity::getStatus, "SUCCEEDED")
                .set(AiTaskEntity::getLeaseOwner, null).set(AiTaskEntity::getLeaseUntil, null)
                .set(AiTaskEntity::getFinishedAt, LocalDateTime.now()).set(AiTaskEntity::getLastError, null));
    }

    @Transactional
    public void markFailed(String taskId, Throwable error) {
        markFailed(taskId, NODE_ID, error, false);
    }

    @Transactional
    public void markFailed(String taskId, String leaseOwner, Throwable error) {
        markFailed(taskId, leaseOwner, error, false);
    }

    @Transactional
    public void markDeadLetter(String taskId, Throwable error) {
        markFailed(taskId, NODE_ID, error, true);
    }

    @Transactional
    public void markDeadLetter(String taskId, String leaseOwner, Throwable error) {
        markFailed(taskId, leaseOwner, error, true);
    }

    private void markFailed(String taskId, String leaseOwner, Throwable error, boolean permanent) {
        AiTaskEntity task = taskMapper.selectById(taskId);
        if (task == null) return;
        String message = truncate(error == null ? "unknown error" : error.getMessage(), 2000);
        if (!permanent && task.getAttempts() != null && task.getAttempts() < task.getMaxAttempts()) {
            long delaySeconds = 1L << Math.min(task.getAttempts(), 3);
            taskMapper.update(null, new LambdaUpdateWrapper<AiTaskEntity>()
                    .eq(AiTaskEntity::getTaskId, taskId).eq(AiTaskEntity::getStatus, "RUNNING")
                    .eq(AiTaskEntity::getLeaseOwner, leaseOwner)
                    .set(AiTaskEntity::getStatus, "RETRY_WAIT")
                    .set(AiTaskEntity::getNextRetryAt, LocalDateTime.now().plusSeconds(delaySeconds))
                    .set(AiTaskEntity::getLastError, message)
                    .set(AiTaskEntity::getLeaseOwner, null).set(AiTaskEntity::getLeaseUntil, null));
            log.warn("AI task will retry, taskId={}, attempt={}, error={}", taskId, task.getAttempts(), message);
        } else {
            taskMapper.update(null, new LambdaUpdateWrapper<AiTaskEntity>()
                    .eq(AiTaskEntity::getTaskId, taskId).eq(AiTaskEntity::getStatus, "RUNNING")
                    .eq(AiTaskEntity::getLeaseOwner, leaseOwner)
                    .set(AiTaskEntity::getStatus, "DEAD_LETTER")
                    .set(AiTaskEntity::getLastError, message)
                    .set(AiTaskEntity::getLeaseOwner, null).set(AiTaskEntity::getLeaseUntil, null)
                    .set(AiTaskEntity::getFinishedAt, LocalDateTime.now()));
            log.error("AI task moved to dead letter, taskId={}, attempts={}, permanent={}, error={}", taskId,
                    task.getAttempts(), permanent, message);
        }
    }

    /** 投递阶段没有 RUNNING 执行次数，单独推进 attempts，避免发布失败的任务永远重试。 */
    @Transactional
    public void markDispatchFailed(String taskId, Throwable error) {
        AiTaskEntity task = taskMapper.selectById(taskId);
        if (task == null) return;
        int attempts = task.getAttempts() == null ? 0 : task.getAttempts();
        int maxAttempts = task.getMaxAttempts() == null ? MAX_ATTEMPTS : task.getMaxAttempts();
        String message = truncate(error == null ? "dispatch failed" : error.getMessage(), 2000);
        LambdaUpdateWrapper<AiTaskEntity> update = new LambdaUpdateWrapper<AiTaskEntity>()
                .eq(AiTaskEntity::getTaskId, taskId).eq(AiTaskEntity::getStatus, "RUNNING")
                .eq(AiTaskEntity::getLeaseOwner, NODE_ID)
                .isNull(AiTaskEntity::getStartedAt)
                .setSql("attempts = attempts + 1")
                .set(AiTaskEntity::getLastError, message)
                .set(AiTaskEntity::getLeaseOwner, null).set(AiTaskEntity::getLeaseUntil, null);
        if (attempts + 1 < maxAttempts) {
            long delaySeconds = 1L << Math.min(attempts, 3);
            update.set(AiTaskEntity::getStatus, "RETRY_WAIT")
                    .set(AiTaskEntity::getNextRetryAt, LocalDateTime.now().plusSeconds(delaySeconds));
        } else {
            update.set(AiTaskEntity::getStatus, "DEAD_LETTER")
                    .set(AiTaskEntity::getFinishedAt, LocalDateTime.now());
        }
        taskMapper.update(null, update);
        log.warn("AI 任务投递失败，taskId={}，attempt={}，status={}", taskId, attempts + 1,
                attempts + 1 < maxAttempts ? "RETRY_WAIT" : "DEAD_LETTER");
    }

    @Transactional
    public void recoverExpiredTasks() {
        LocalDateTime now = LocalDateTime.now();
        List<AiTaskEntity> expired = taskMapper.selectList(new LambdaQueryWrapper<AiTaskEntity>()
                .eq(AiTaskEntity::getStatus, "RUNNING").lt(AiTaskEntity::getLeaseUntil, now).last("LIMIT 100"));
        for (AiTaskEntity task : expired) {
            if (task.getLeaseOwner() != null) {
                markFailed(task.getTaskId(), task.getLeaseOwner(), new IllegalStateException("task lease expired"));
            }
        }
    }

    public String payloadValue(AiTaskEntity task, String key) {
        try {
            Map<?, ?> payload = objectMapper.readValue(task.getPayload() == null ? "{}" : task.getPayload(), Map.class);
            Object value = payload.get(key);
            return value == null ? null : String.valueOf(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid task payload", e);
        }
    }

    private boolean claimForDispatch(String taskId, LocalDateTime now) {
        return taskMapper.update(null, new LambdaUpdateWrapper<AiTaskEntity>()
                .eq(AiTaskEntity::getTaskId, taskId)
                .in(AiTaskEntity::getStatus, "PENDING_DISPATCH", "RETRY_WAIT")
                .and(w -> w.isNull(AiTaskEntity::getLeaseUntil).or().lt(AiTaskEntity::getLeaseUntil, now))
                .set(AiTaskEntity::getStatus, "RUNNING")
                .set(AiTaskEntity::getLeaseOwner, NODE_ID)
                .set(AiTaskEntity::getLeaseUntil, now.plusSeconds(DISPATCH_LEASE_SECONDS))) == 1;
    }

    private String routingKey(String taskType) {
        return switch (taskType) {
            case AiTaskMessage.TYPE_DIARY_ANALYSIS -> RabbitMqConfig.ANALYSIS_QUEUE;
            case AiTaskMessage.TYPE_MEMORY_EXTRACTION -> RabbitMqConfig.MEMORY_QUEUE;
            case AiTaskMessage.TYPE_LIFE_EVENT_EXTRACTION -> RabbitMqConfig.LIFE_EVENT_QUEUE;
            case AiTaskMessage.TYPE_GRAPH_EXTRACTION -> RabbitMqConfig.GRAPH_QUEUE;
            case AiTaskMessage.TYPE_DIARY_RAG_INDEX, AiTaskMessage.TYPE_GRAPH_RAG_INDEX,
                    AiTaskMessage.TYPE_MEMORY_RAG_INDEX -> RabbitMqConfig.RAG_QUEUE;
            case AiTaskMessage.TYPE_REPORT_INVALIDATION -> RabbitMqConfig.REPORT_QUEUE;
            case AiTaskMessage.TYPE_NOTIFICATION -> RabbitMqConfig.NOTIFICATION_QUEUE;
            default -> throw new IllegalArgumentException("unknown task type: " + taskType);
        };
    }

    private String toJson(Map<String, Object> payload) {
        try { return objectMapper.writeValueAsString(payload == null ? Map.of() : payload); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("invalid task payload", e); }
    }

    private String truncate(String value, int max) {
        if (value == null) return "unknown error";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
