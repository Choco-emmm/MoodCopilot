package com.moodcopilot.ai.mq;

import com.moodcopilot.config.RabbitMqConfig;
import com.moodcopilot.diary.DiaryService;
import com.moodcopilot.ai.AiPostProcessService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.moodcopilot.event.LifeChapterService;
import com.moodcopilot.ai.MemoryConsolidationService;
import com.moodcopilot.ai.GraphConsolidationService;
import com.moodcopilot.notification.NotificationService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AiTaskConsumer {
    private static final Logger log = LoggerFactory.getLogger(AiTaskConsumer.class);
    private final AiTaskService taskService;
    private final DiaryService diaryService;
    private final AiPostProcessService postProcessService;
    private final LifeChapterService lifeChapterService;
    private final MemoryConsolidationService memoryConsolidationService;
    private final GraphConsolidationService graphConsolidationService;
    private final NotificationService notificationService;

    public AiTaskConsumer(AiTaskService taskService, @Lazy DiaryService diaryService,
                          AiPostProcessService postProcessService, LifeChapterService lifeChapterService,
                          MemoryConsolidationService memoryConsolidationService,
                          GraphConsolidationService graphConsolidationService,
                          NotificationService notificationService) {
        this.taskService = taskService;
        this.diaryService = diaryService;
        this.postProcessService = postProcessService;
        this.lifeChapterService = lifeChapterService;
        this.memoryConsolidationService = memoryConsolidationService;
        this.graphConsolidationService = graphConsolidationService;
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = RabbitMqConfig.ANALYSIS_QUEUE, containerFactory = "aiHeavyRabbitListenerContainerFactory")
    public void consumeAnalysis(AiTaskMessage message, Message raw, Channel channel,
                                @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {
        consume(message, channel, tag);
    }

    @RabbitListener(queues = {RabbitMqConfig.MEMORY_QUEUE, RabbitMqConfig.LIFE_EVENT_QUEUE,
            RabbitMqConfig.GRAPH_QUEUE, RabbitMqConfig.LIFE_CHAPTER_QUEUE}, containerFactory = "aiHeavyRabbitListenerContainerFactory")
    public void consumeHeavyPostProcess(AiTaskMessage message, Message raw, Channel channel,
                                    @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {
        consume(message, channel, tag);
    }

    @RabbitListener(queues = {RabbitMqConfig.RAG_QUEUE, RabbitMqConfig.REPORT_QUEUE,
            RabbitMqConfig.NOTIFICATION_QUEUE}, containerFactory = "aiLightRabbitListenerContainerFactory")
    public void consumeLightPostProcess(AiTaskMessage message, Message raw, Channel channel,
                                    @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {
        consume(message, channel, tag);
    }

    private void consume(AiTaskMessage message, Channel channel, long tag) throws Exception {
        if (message == null || message.taskId() == null) {
            channel.basicAck(tag, false);
            return;
        }
        AiTaskEntity task = taskService.claimForRun(message.taskId());
        if (task == null) {
            // Dispatcher publishes after claiming a short RUNNING lease. A broker delivery can
            // arrive before the publisher-confirm callback updates the row to PUBLISHED.
            // Requeue that message instead of ACKing it and losing the only delivery.
            if (taskService.isDispatching(message.taskId())) {
                channel.basicNack(tag, false, true);
                return;
            }
            log.info("忽略已处理或已租约占用的重复 AI 消息，taskId={}", message.taskId());
            channel.basicAck(tag, false);
            return;
        }
        long executionStartedAt = System.nanoTime();
        long queueWaitMs = task.getPublishedAt() == null || task.getStartedAt() == null
                ? -1L
                : Math.max(0L, Duration.between(task.getPublishedAt(), task.getStartedAt()).toMillis());
        try {
            if (AiTaskMessage.TYPE_DIARY_ANALYSIS.equals(task.getTaskType())) {
                long diaryId = Long.parseLong(task.getAggregateId());
                boolean useReasoning = Boolean.parseBoolean(taskService.payloadValue(task, "useReasoning"));
                diaryService.runAiAnalysisSync(diaryId, task.getUserId(), useReasoning, task.getTaskId());
            } else if (AiTaskMessage.TYPE_LIFE_CHAPTER_REFRESH.equals(task.getTaskType())) {
                long chapterId = Long.parseLong(task.getAggregateId());
                lifeChapterService.refreshChapterTask(task.getUserId(), chapterId, task.getAnalysisVersion());
            } else if (AiTaskMessage.TYPE_TIMELINE_RECOMPUTE.equals(task.getTaskType())) {
                lifeChapterService.recomputeTimeline(task.getUserId(), java.time.LocalDate.parse(task.getAggregateId()), task.getAnalysisVersion());
            } else if (AiTaskMessage.TYPE_MEMORY_CONSOLIDATION.equals(task.getTaskType())) {
                memoryConsolidationService.runConsolidationTask(task.getUserId(), task.getTaskId());
            } else if (AiTaskMessage.TYPE_GRAPH_CONSOLIDATION.equals(task.getTaskType())) {
                graphConsolidationService.runConsolidationTask(task.getUserId(), task.getTaskId());
            } else {
                long diaryId = Long.parseLong(task.getAggregateId());
                postProcessService.process(task.getTaskType(), diaryId, task.getUserId(),
                        task.getAnalysisVersion(), task.getTaskId());
            }
            taskService.markSucceeded(task.getTaskId(), task.getLeaseOwner());
            channel.basicAck(tag, false);
            log.info("AI 任务处理完成，taskId={}，taskType={}，queueWaitMs={}，executionDurationMs={}",
                    task.getTaskId(), task.getTaskType(), queueWaitMs, elapsedMillis(executionStartedAt));
        } catch (Exception e) {
            if (isUnrecoverable(e)) {
                taskService.markDeadLetter(task.getTaskId(), task.getLeaseOwner(), e);
            } else {
                taskService.markFailed(task.getTaskId(), task.getLeaseOwner(), e);
            }
            if (AiTaskMessage.TYPE_LIFE_CHAPTER_REFRESH.equals(task.getTaskType())
                    && (isUnrecoverable(e) || task.getAttempts() >= task.getMaxAttempts())) {
                lifeChapterService.markGenerationFailed(task.getUserId(), Long.valueOf(task.getAggregateId()),
                        task.getAnalysisVersion(), e.getMessage());
            }
            if (isFinalFailure(task, e)) {
                notifyConsolidationFailure(task, e);
            }
            channel.basicAck(tag, false);
            log.error("AI 任务处理失败，已写入任务状态，taskId={}，taskType={}，queueWaitMs={}，executionDurationMs={}，error={}",
                    task.getTaskId(), task.getTaskType(), queueWaitMs, elapsedMillis(executionStartedAt), e.getMessage(), e);
        }
    }

    private long elapsedMillis(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private boolean isFinalFailure(AiTaskEntity task, Exception error) {
        return isUnrecoverable(error) || (task.getAttempts() != null && task.getMaxAttempts() != null
                && task.getAttempts() >= task.getMaxAttempts());
    }

    private void notifyConsolidationFailure(AiTaskEntity task, Exception error) {
        String type = switch (task.getTaskType()) {
            case AiTaskMessage.TYPE_MEMORY_CONSOLIDATION -> "MEMORY_CONSOLIDATION_COMPLETED";
            case AiTaskMessage.TYPE_GRAPH_CONSOLIDATION -> "GRAPH_CONSOLIDATION_COMPLETED";
            default -> null;
        };
        if (type == null) return;
        String label = AiTaskMessage.TYPE_MEMORY_CONSOLIDATION.equals(task.getTaskType()) ? "长期画像整理" : "知识图谱整理";
        notificationService.notifyGlobalEvent(task.getUserId(), type,
                java.util.Map.of("message", label + "失败，请稍后重试", "taskId", task.getTaskId()));
    }

    private boolean isUnrecoverable(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof JsonProcessingException || current instanceof NumberFormatException
                    || current instanceof IllegalArgumentException || current instanceof DuplicateKeyException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
