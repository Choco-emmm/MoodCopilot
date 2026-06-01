package com.moodcopilot.config;

import com.moodcopilot.ai.mq.AiTaskConsumer;
import com.moodcopilot.ai.mq.AiTaskProducer;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.Collections;

@Configuration
public class RedisStreamConfig {
    private static final Logger log = LoggerFactory.getLogger(RedisStreamConfig.class);

    private ThreadPoolTaskExecutor streamExecutor;

    @Bean(destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamMessageListenerContainer(
            RedisConnectionFactory factory,
            AiTaskConsumer aiTaskConsumer,
            StringRedisTemplate redisTemplate) {

        // 确保 stream 和 group 存在
        initStreamAndGroup(redisTemplate);

        streamExecutor = new ThreadPoolTaskExecutor();
        streamExecutor.setCorePoolSize(3);
        streamExecutor.setMaxPoolSize(5);
        streamExecutor.setThreadNamePrefix("Stream-Consumer-");
        streamExecutor.setWaitForTasksToCompleteOnShutdown(true);
        streamExecutor.setAwaitTerminationSeconds(10);
        streamExecutor.initialize();

        StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainerOptions.builder()
                        .batchSize(10)
                        .executor(streamExecutor)
                        .pollTimeout(Duration.ofSeconds(2))
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(factory, options);

        // 使用消费者组 "ai-group" 消费 "stream:ai:tasks"
        container.receive(
                Consumer.from("ai-group", "node-1"),
                StreamOffset.create(AiTaskProducer.STREAM_KEY, ReadOffset.lastConsumed()),
                aiTaskConsumer
        );

        container.start();
        return container;
    }

    @PreDestroy
    public void cleanup() {
        log.info("正在停止 Stream 消费者线程池...");
        if (streamExecutor != null) {
            streamExecutor.shutdown();
        }
    }

    private void initStreamAndGroup(StringRedisTemplate redisTemplate) {
        try {
            Boolean hasKey = redisTemplate.hasKey(AiTaskProducer.STREAM_KEY);
            if (Boolean.FALSE.equals(hasKey)) {
                redisTemplate.opsForStream().add(AiTaskProducer.STREAM_KEY, Collections.singletonMap("init", "init"));
            }
            redisTemplate.opsForStream().createGroup(AiTaskProducer.STREAM_KEY, ReadOffset.from("0-0"), "ai-group");
            log.info("Redis Stream 和 Consumer Group [ai-group] 创建成功");
        } catch (Exception e) {
            // 如果组已存在，会抛出异常，这里忽略即可
            log.info("Consumer Group 可能已存在: {}", e.getMessage());
        }
    }
}
