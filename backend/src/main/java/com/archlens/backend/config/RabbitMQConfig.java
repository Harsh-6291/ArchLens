package com.archlens.backend.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "archlens.direct";
    public static final String ANALYSIS_JOBS_QUEUE = "analysis-jobs";
    public static final String ANALYSIS_JOBS_ROUTING_KEY = "analysis-jobs";

    public static final String DLX_EXCHANGE_NAME = "archlens.dlx";
    public static final String ANALYSIS_JOBS_DLQ = "analysis-jobs.dlq";
    public static final String ANALYSIS_JOBS_DLQ_ROUTING_KEY = "analysis-jobs.dlq";

    @Bean
    public DirectExchange mainExchange() {
        return new DirectExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue analysisJobsQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_EXCHANGE_NAME);
        args.put("x-dead-letter-routing-key", ANALYSIS_JOBS_DLQ_ROUTING_KEY);
        return new Queue(ANALYSIS_JOBS_QUEUE, true, false, false, args);
    }

    @Bean
    public Queue analysisJobsDeadLetterQueue() {
        return new Queue(ANALYSIS_JOBS_DLQ, true, false, false);
    }

    @Bean
    public Binding analysisJobsBinding(Queue analysisJobsQueue, DirectExchange mainExchange) {
        return BindingBuilder.bind(analysisJobsQueue).to(mainExchange).with(ANALYSIS_JOBS_ROUTING_KEY);
    }

    @Bean
    public Binding analysisJobsDlqBinding(Queue analysisJobsDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(analysisJobsDeadLetterQueue).to(deadLetterExchange).with(ANALYSIS_JOBS_DLQ_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
