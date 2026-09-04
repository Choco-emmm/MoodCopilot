package com.moodcopilot.ai.mq;

import com.moodcopilot.config.RabbitMqConfig;
import com.moodcopilot.diary.DiaryService;
import com.moodcopilot.ai.AiPostProcessService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.moodcopilot.event.LifeChapterService;
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

    public AiTaskConsumer(AiTaskService taskService, @Lazy DiaryService diaryService,
                          AiPostProcessService postProcessService, LifeChapterService lifeChapterService) {
        this.taskService = taskService;
        this.diaryService = diaryService;
        this.postProcessService = postProcessService;
        this.lifeChapterService = lifeChapterService;
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
            channel.basicAck(tag, false);
            log.error("AI 任务处理失败，已写入任务状态，taskId={}，taskType={}，queueWaitMs={}，executionDurationMs={}，error={}",
                    task.getTaskId(), task.getTaskType(), queueWaitMs, elapsedMillis(executionStartedAt), e.getMessage(), e);
        }
    }

    private long elapsedMillis(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
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
