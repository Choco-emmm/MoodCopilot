package com.moodcopilot.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class RabbitMqConfig {
    public static final String EXCHANGE = "moodcopilot.ai";
    public static final String ANALYSIS_QUEUE = "ai.analysis";
    public static final String MEMORY_QUEUE = "ai.memory";
    public static final String LIFE_EVENT_QUEUE = "ai.life-event";
    public static final String GRAPH_QUEUE = "ai.graph";
    public static final String RAG_QUEUE = "ai.rag";
    public static final String REPORT_QUEUE = "ai.report-invalidation";
    public static final String NOTIFICATION_QUEUE = "ai.notification";
    public static final String LIFE_CHAPTER_QUEUE = "ai.life-chapter";

    @Bean
    public DirectExchange aiExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public Declarables aiTopology(DirectExchange exchange) {
        Queue analysis = new Queue(ANALYSIS_QUEUE, true);
        Queue memory = new Queue(MEMORY_QUEUE, true);
        Queue lifeEvent = new Queue(LIFE_EVENT_QUEUE, true);
        Queue graph = new Queue(GRAPH_QUEUE, true);
        Queue rag = new Queue(RAG_QUEUE, true);
        Queue report = new Queue(REPORT_QUEUE, true);
        Queue notification = new Queue(NOTIFICATION_QUEUE, true);
        Queue lifeChapter = new Queue(LIFE_CHAPTER_QUEUE, true);
        return new Declarables(
                analysis, memory, lifeEvent, graph, rag, report, notification, lifeChapter,
                BindingBuilder.bind(analysis).to(exchange).with(ANALYSIS_QUEUE),
                BindingBuilder.bind(memory).to(exchange).with(MEMORY_QUEUE),
                BindingBuilder.bind(lifeEvent).to(exchange).with(LIFE_EVENT_QUEUE),
                BindingBuilder.bind(graph).to(exchange).with(GRAPH_QUEUE),
                BindingBuilder.bind(rag).to(exchange).with(RAG_QUEUE),
                BindingBuilder.bind(report).to(exchange).with(REPORT_QUEUE),
                BindingBuilder.bind(notification).to(exchange).with(NOTIFICATION_QUEUE),
                BindingBuilder.bind(lifeChapter).to(exchange).with(LIFE_CHAPTER_QUEUE));
    }

    @Bean
    public Jackson2JsonMessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        template.setMandatory(true);
        return template;
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory aiRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter,
            @Value("${spring.rabbitmq-task.analysis-concurrency:1}") int concurrency,
            @Value("${spring.rabbitmq-task.analysis-max-concurrency:2}") int maxConcurrency) {
        return listenerFactory(connectionFactory, converter, 5, concurrency, maxConcurrency);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory aiHeavyRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter,
            @Value("${spring.rabbitmq-task.heavy-concurrency:2}") int concurrency,
            @Value("${spring.rabbitmq-task.heavy-max-concurrency:4}") int maxConcurrency) {
        return listenerFactory(connectionFactory, converter, 1, concurrency, maxConcurrency);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory aiLightRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter,
            @Value("${spring.rabbitmq-task.light-concurrency:2}") int concurrency,
            @Value("${spring.rabbitmq-task.light-max-concurrency:8}") int maxConcurrency) {
        return listenerFactory(connectionFactory, converter, 20, concurrency, maxConcurrency);
    }

    private SimpleRabbitListenerContainerFactory listenerFactory(ConnectionFactory connectionFactory,
                                                                  Jackson2JsonMessageConverter converter,
                                                                  int prefetch,
                                                                  int concurrency,
                                                                  int maxConcurrency) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(prefetch);
        int safeConcurrency = Math.max(1, concurrency);
        factory.setConcurrentConsumers(safeConcurrency);
        factory.setMaxConcurrentConsumers(Math.max(safeConcurrency, maxConcurrency));
        return factory;
    }
}
