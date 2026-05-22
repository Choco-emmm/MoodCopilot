package com.moodcopilot.config;

import com.moodcopilot.ai.mq.AiTaskConsumer;
import com.moodcopilot.ai.mq.AiTaskProducer;
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

    @Bean
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamMessageListenerContainer(
            RedisConnectionFactory factory,
            AiTaskConsumer aiTaskConsumer,
            StringRedisTemplate redisTemplate) {

        // 确保 stream 和 group 存在
        initStreamAndGroup(redisTemplate);

        // 使用专门的线程池或直接使用默认执行器
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(5);
        executor.setThreadNamePrefix("Stream-Consumer-");
        executor.initialize();

        StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainerOptions.builder()
                        .batchSize(10)
                        .executor(executor)
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
