package com.moodcopilot.ai.mq;

import com.moodcopilot.config.RabbitMqConfig;
import com.moodcopilot.diary.DiaryService;
import com.moodcopilot.ai.AiPostProcessService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

@Service
public class AiTaskConsumer {
    private static final Logger log = LoggerFactory.getLogger(AiTaskConsumer.class);
    private final AiTaskService taskService;
    private final DiaryService diaryService;
    private final AiPostProcessService postProcessService;

    public AiTaskConsumer(AiTaskService taskService, @Lazy DiaryService diaryService,
                          AiPostProcessService postProcessService) {
        this.taskService = taskService;
        this.diaryService = diaryService;
        this.postProcessService = postProcessService;
    }

    @RabbitListener(queues = RabbitMqConfig.ANALYSIS_QUEUE, containerFactory = "aiHeavyRabbitListenerContainerFactory")
    public void consumeAnalysis(AiTaskMessage message, Message raw, Channel channel,
                                @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {
        consume(message, channel, tag);
    }

    @RabbitListener(queues = {RabbitMqConfig.MEMORY_QUEUE, RabbitMqConfig.LIFE_EVENT_QUEUE,
            RabbitMqConfig.GRAPH_QUEUE}, containerFactory = "aiHeavyRabbitListenerContainerFactory")
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
        try {
            if (AiTaskMessage.TYPE_DIARY_ANALYSIS.equals(task.getTaskType())) {
                long diaryId = Long.parseLong(task.getAggregateId());
                boolean useReasoning = Boolean.parseBoolean(taskService.payloadValue(task, "useReasoning"));
                diaryService.runAiAnalysisSync(diaryId, task.getUserId(), useReasoning, task.getTaskId());
            } else {
                long diaryId = Long.parseLong(task.getAggregateId());
                postProcessService.process(task.getTaskType(), diaryId, task.getUserId(),
                        task.getAnalysisVersion(), task.getTaskId());
            }
            taskService.markSucceeded(task.getTaskId());
            channel.basicAck(tag, false);
        } catch (Exception e) {
            if (isUnrecoverable(e)) {
                taskService.markDeadLetter(task.getTaskId(), e);
            } else {
                taskService.markFailed(task.getTaskId(), e);
            }
            channel.basicAck(tag, false);
            log.error("AI 任务处理失败，已写入任务状态，taskId={}", task.getTaskId(), e);
        }
    }

    private boolean isUnrecoverable(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof JsonProcessingException || current instanceof NumberFormatException
                    || current instanceof IllegalArgumentException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
