package com.archlens.backend.messaging;

import com.archlens.backend.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class AnalysisJobProducer {

    private static final Logger log = LoggerFactory.getLogger(AnalysisJobProducer.class);
    private final RabbitTemplate rabbitTemplate;

    public AnalysisJobProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendAnalysisJob(AnalysisJobMessage message) {
        log.info("Dispatching analysis job message: {}", message.jobId());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ANALYSIS_JOBS_ROUTING_KEY,
                message
        );
    }
}
