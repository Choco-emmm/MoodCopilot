package com.moodcopilot.ai.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

@Service
public class AiTaskProducer {
    private static final Logger log = LoggerFactory.getLogger(AiTaskProducer.class);
    public static final String STREAM_KEY = "stream:ai:tasks";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public AiTaskProducer(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void submitDiaryAnalysisTask(long diaryId, long userId) {
        AiTaskMessage message = new AiTaskMessage(AiTaskMessage.TYPE_DIARY_ANALYSIS, diaryId, userId);
        sendMessage(message);
    }

    private void sendMessage(AiTaskMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            Map<String, String> map = Collections.singletonMap("payload", json);
            MapRecord<String, String, String> record = StreamRecords.newRecord()
                    .in(STREAM_KEY)
                    .ofMap(map);
            RecordId recordId = redisTemplate.opsForStream().add(record);
            log.info("已提交 AI 任务到消息队列，taskType={}，messageId={}", message.taskType(), recordId);
        } catch (JsonProcessingException e) {
            log.error("AI 任务消息序列化失败: {}", e.getMessage());
        }
    }
}
