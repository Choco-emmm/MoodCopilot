package com.moodcopilot.ai.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.diary.DiaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Service;

@Service
public class AiTaskConsumer implements StreamListener<String, MapRecord<String, String, String>> {
    private static final Logger log = LoggerFactory.getLogger(AiTaskConsumer.class);

    private final ObjectMapper objectMapper;
    private final DiaryService diaryService;
    private final StringRedisTemplate redisTemplate;

    public AiTaskConsumer(ObjectMapper objectMapper, @Lazy DiaryService diaryService, StringRedisTemplate redisTemplate) {
        this.objectMapper = objectMapper;
        this.diaryService = diaryService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        String recordId = message.getId().getValue();
        try {
            String payload = message.getValue().get("payload");
            if (payload == null) {
                log.warn("收到空 payload 的消息，直接忽略: {}", recordId);
                ack(recordId);
                return;
            }

            AiTaskMessage task = objectMapper.readValue(payload, AiTaskMessage.class);
            log.info("开始消费 AI 任务: {} (recordId={})", task.taskType(), recordId);

            if (AiTaskMessage.TYPE_DIARY_ANALYSIS.equals(task.taskType())) {
                // 同步执行任务，抛出异常则不执行 ack
                diaryService.runAiAnalysisSync(task.diaryId(), task.userId(), task.isUseReasoning());
            } else {
                log.warn("未知的任务类型: {}", task.taskType());
            }

            // 成功处理后，手动 ACK 确认
            ack(recordId);
        } catch (Exception e) {
            // 如果报错，不进行 ack，它会留在 Pending 列表中，稍后可重试
            log.error("处理 AI 任务失败 (recordId={}): {}", recordId, e.getMessage(), e);
        }
    }

    private void ack(String recordId) {
        // Note: acknowledge in Spring Data Redis takes (key, group, recordId) 
        redisTemplate.opsForStream().acknowledge(AiTaskProducer.STREAM_KEY, "ai-group", recordId);
    }
}
